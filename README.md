# quarkus-game-api

**Server-authoritative game backend over gRPC in Quarkus (Java 17)**: one `sendData` RPC carries
the whole game protocol as opcode + JSON payload, gameplay is driven by server-side config, state
is persisted with Hibernate and cached in Redis, and a JWT interceptor protects everything except
the handshake.

```proto
service GameGrpc {
    rpc sendData (GameRequest) returns (GameResponse);   // {opcode, payload}
}
```

## What this demonstrates

- **Opcode dispatch instead of a growing REST surface**: the client sends an `opcode` and a JSON
  payload, and `services/GameService` routes it to the right handler. Game protocols change every
  sprint; adding an opcode does not add an endpoint, a version and a DTO.
- **Config as game design data**: seeds, seasons, fruit rates, watering-can counts and timers live
  in entities under `repositories`, so balance changes are data, not code.
- **Authorization at the transport boundary**: `configs/AuthorizationServerInterceptor` reads and
  verifies the bearer token once per call and exposes the user id through a request-scoped
  `Principal`, so no handler has to remember to check it.
- **Time-based gameplay in one place**: sow → grow → spray water → pick → protect is modelled as
  explicit opcodes with server-side countdowns, which is the part clients must never compute.
- **Typed wire format**: the protocol is a Protobuf service (`src/main/proto/game.proto`), so the
  client and the server cannot silently disagree about the shape of a message.

## Opcodes

| opcode | name | meaning |
|---:|---|---|
| 0 | `PING` | liveness |
| 100 | `USER_INFO` | account / profile |
| 200 | `PLANT_PROGRESS_INFO` | current plot state and countdowns |
| 201 | `PLANT_PROGRESS_SOW` | plant a seed |
| 202 | `PLANT_PROGRESS_PICKING` | harvest |
| 203 | `PLANT_PROGRESS_SPRAY_WATER` | water a plot |
| 204 | `PLANT_PROGRESS_PROTECT_RESOURCE` | protect a resource before it is stolen |

The table lives in `constants/GameOpCode.java`.

## Quickstart

```bash
git clone https://github.com/lasttoss/quarkus-game-api.git
cd quarkus-game-api
make up          # dev keys + postgres + redis + the gRPC service on :9090
```

Calling it: any gRPC client generated from `src/main/proto/game.proto` works. For a quick check
from the shell:

```bash
./mvnw quarkus:dev            # needs postgres + redis + certs/ from make keys
grpcurl -plaintext -d '{"opcode":0,"payload":"{}"}' localhost:9090 game.api.GameGrpc/sendData
```

## Configuration

`src/main/resources/application.properties` reads `GRPC_PORT`, `DB_URL`, `DB_USERNAME`,
`DB_PASSWORD`, `REDIS_URL`, `REDIS_PASSWORD`, `REDIS_DATABASE`, `JWT_PUBLIC_KEY`,
`JWT_PRIVATE_KEY` from the environment, with development defaults. See `.env.example`.

## Fixed while preparing this repository

1. **The service could not start from a clean clone**: the configuration existed only as
   `application.properties.bak`. It is now a real file with environment placeholders.
2. **The JWT key pair the interceptor needs was not in the repository** (correctly), so startup
   failed with no way to create one. `scripts/gen-dev-keys.sh` generates a development pair and
   `certs/` is git-ignored.
3. **Every `mvn package` tried to build a container image** (`quarkus.container-image.build=true`),
   which fails on machines without a Docker daemon. Opt-in now.
4. **A database password and a Redis password were committed** in that file; both are gone.

## Notes / limitations

- No automated tests yet; CI builds the module (`./mvnw -B -DskipTests package`), which is what
  would have caught all four problems above.
- The gameplay rules in this service are the same shape as the ones I ran in production, but this
  is a clean-room extraction: no employer code, schema, asset or data is included.

## License

MIT - see [LICENSE](LICENSE).

## The interface as a picture

```mermaid
%% Source for docs/diagrams/one-rpc-many-opcodes.html
%% One gRPC method carries every game action; the server decides what the clock says.
flowchart LR
  C["game client"] -->|"gRPC :9090<br/>{opcode, payload}"| JI{"JWT interceptor<br/>everything but auth"}
  JI -->|"valid token"| D["opcode dispatch"]
  JI -.->|"no token"| X["refused"]
  D --> O100["100 USER_INFO"]
  D --> O200["200 PLANT_INFO<br/>countdowns"]
  D --> O201["201 sow"]
  D --> O202["202 picking"]
  D --> O203["203 spray water"]
  D --> O204["204 protect resource"]
  O200 --> SV["game service<br/>owns the clock"]
  SV --> H["Hibernate"] --> PG[("PostgreSQL")]
  SV <--> RD[("Redis cache")]
  classDef gate fill:#eef5ef,stroke:#1a6b3c,stroke-width:2px;
  class JI, SV gate;
```

One RPC, and an opcode inside it, is the whole client-facing surface: 100 for the account, 200 for the plot
state and its countdowns, and 201-204 for the four verbs. A growing REST surface would be a new route, a
version and a DTO per action; this is the same dispatch with one shape to keep.

The half of the picture that is about authority is the clock. A harvest a client could time itself is a
harvest a client can fake, so the server stores when a plot was planted and answers whether it is ready —
countdowns are facts it owns, not local timers it is trusted to be told about. PostgreSQL keeps what must
survive a restart; Redis holds what may be recomputed, which is exactly the plot a client polls while it
waits.

`docs/diagrams/one-rpc-many-opcodes.mmd` is the Mermaid source; `make diagram` exports a PNG if a browser
is present.

## The chart

`charts/game-api/` deploys the service with the two things a gRPC game API needs: a rolling update that never
takes a replica out of the Service before its successor can accept a connection (`maxUnavailable: 0`), and a
PodDisruptionBudget that keeps one serving through a disruption. It also carries an HPA, no service-account
token, a read-only root filesystem with an `emptyDir` for the `/tmp` a JVM writes to, and a NetworkPolicy whose
egress names PostgreSQL and Redis rather than allowing everything.

The probe is chosen from what the build actually has: if the health extension is on the classpath the chart
probes its endpoint, and otherwise it falls back to a TCP connect rather than inventing a path. The value is in
`values.yaml`, so the choice is visible rather than hidden in a template.

```bash
make chart     # helm lint --strict + helm template
```

## Coverage

`./mvnw -B test` runs the unit suite; these are JaCoCo's numbers, line coverage:

| class | lines |
|---|---|
| `UserPlantService` | 90.2% (266/295) |
| `GameRequest.Builder` | 0.0% (0/113) |
| `GameResponse.Builder` | 0.0% (0/113) |
| `GameResponse` | 0.0% (0/96) |
| `GameRequest` | 0.0% (0/96) |
| `UserPlantModel` | 95.2% (40/42) |
| `UserWateringCanModel` | 92.7% (38/41) |
| `ConfigService` | 100.0% (38/38) |
| `UserInventoryModel` | 80.0% (20/25) |
| `UserPlantMapperImpl` | 0.0% (0/25) |
| `ApiErrorEnum` | 95.8% (23/24) |
| `GameService` | 0.0% (0/21) |
| **total** | **40.9%** (535/1309 lines, 11.1% of 949 branches) |

Branch coverage is the lower number because most of what is left uncovered is branchier than what is
covered: `GameService` and the gRPC layer, whose opcode switch is the next thing to take.

The plant rules are covered from both ends: the model tests check what growing and waiting do to a plant
and a can, the service tests check who is allowed to do what and when, and the config tests check the
cache in front of the database. Everything in the service is mocked - repositories, Redis, the config
service, the mappers - which is what makes the timing rules testable at all: the countdown a completed
plant waits for and the five minutes between water are both timestamps, and a test can place them on
whichever side of now it wants.

### Fixed: the picking guard refused exactly when the time had come

`pickingFruit` and `protectResource` both refused when `nextTimeToPick < now`, which is exactly when
picking and protecting become allowed: a plant was pickable before its time and refused once the time had
come. The error it answers with names the intended behaviour (`NOT_ALREADY_TIME_TO_PICKING_FRUIT`), and
`addExp` sets the field to `now + countdown`. The two tests that say so - one on each side of now - failed
before the change and pass after it.

### Fixed: the config checks asked the wrong question

Four checks asked "is this plant allowed" with `contains(plantId - 1)`: three against
`seedConfig.getData()`, which holds `SeedConfigData` objects, and one against `getFruitTimeCountdown()`,
which holds countdown seconds. In each case the line immediately below indexes the same list with
`get(plantId - 1)`, which is what the check exists to protect, so the question was always whether that
position is there. Comparing a list of configs against an index can never be true, and comparing a list
of countdowns against an index is true by accident at best, so every request was refused one line before
the code it was meant to protect - and sowing, watering and picking were all unreachable. One helper,
`isPlantInConfig`, now asks the question on behalf of all four.

The tests that pinned the broken behaviour were deleted rather than kept; the tests that replaced them
are the happy paths, which until now were not reachable.

### Fixed: nothing ever put water in the watering can

`UserWateringCanModel(userId)` starts at zero, `use()` only ever takes water out and stops at zero, and
nothing else in this repository set a can's quantity - the opcodes are `PING` and the five plant
operations, with nothing to fetch or buy water. So every spray a real player could send was refused with
`NOT_ENOUGH_QUANTITY_TO_SPRAY_WATER`, the plant never grew, and picking and protecting were states
nothing inside this repository could bring a plant into.

The rule is one water every five minutes, counted from `nextTimeToReset`, and `refill(nowSeconds)` is
that rule. It returns how much it added, so a caller with nothing to write can skip the write. `getInfo`
calls it too, because that is where the numbers a client displays come from, and `sprayWater` calls it
before the check on the can rather than after - a player away for ten minutes arrives holding the two
water that came back with the clock, and the check has to see them.

Water stops at fifty. `MAX_WATER` is the ceiling, nothing is ever added above it, and a can that is
somehow already above it - a store that sells water could do that - is left where it is, because the rule
is about not giving more rather than about taking away. If fifty is not the number, it is one constant.
A full can also does not bank time: the anchor moves to now when the can is full or when the call filled
it, so an hour spent at fifty is an hour nobody gets back, and the first water spent after that starts a
fresh five minutes. Leaving the anchor behind instead would pay out the intervals that arrived while
there was no room, which is a different rule wearing the same ceiling.

The trap is the anchor: a can that has never been refilled carries `nextTimeToReset = 0`, and treating
that as a timestamp would make `now - 0` around seventeen hundred million, or millions of water from a
brand new can. Zero means the can has not started counting, so the first call only sets it. There is a
test named after that, and it fails if anyone removes the special case.

### Found and not changed

Each of these is a decision about behaviour rather than an obvious mistake, so they are pinned by tests
and left for whoever owns the rules.

**An unknown protect type is accepted.** `protectResource` switches on the type with no default, so a
client that sends anything else gets `OK` and nothing happens.

**A malformed config throws instead of refusing.** The completion check reads the last entry of the
required-exp list, so a season config with no plants raises `IndexOutOfBounds`; `pickingFruit` takes
`rates.get(currentPlantId - 1)` and then `random.nextInt(rates.get(rates.size() - 1))`, so an inner rate
list that is empty raises `IndexOutOfBounds` and one whose last entry is zero raises
`IllegalArgumentException`. All three are config errors reaching a client as a crash rather than as an
error code.

**Buying water is not in this repository.** The store the water can also come from is elsewhere: there is
no opcode for it here and no `WATER` resource type in `GameEnum.Resource`. If the store is meant to fill
this can, it is writing to the same row.

Still to cover: `GameService`, whose opcode switch can be tested without a client.
