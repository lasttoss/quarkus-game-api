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
