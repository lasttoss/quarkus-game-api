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
