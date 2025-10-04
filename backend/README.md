# Terraform Log Viewer

This module exposes the Terraform log viewer gRPC backend. The project now ships with a Docker image and compose file so you can run the API without installing Java or Maven locally.

## Prerequisites

* Docker Engine / Docker Desktop 26+
* Docker Compose V2 (bundled with modern Docker Desktop)

## Build & Run

```bash
# Build the application image and start the full stack
# (backend + PostgreSQL; ClickHouse stays optional)
docker compose up --build
```

The gRPC server becomes available on `localhost:9090` once the image is built and the database health check passes.

To stop the stack:

```bash
docker compose down
```

## Useful commands

* Tail backend logs: `docker compose logs -f app`
* Rebuild after code changes: `docker compose build app`
* Keep PostgreSQL data between restarts: managed automatically through the named volume `pgdata`

ClickHouse remains disabled by default. If you need it, start the service with `docker compose up ch` and enable the ClickHouse profile in `application.yml` or via environment variables.

## gRPC API

The backend exposes only gRPC endpoints. A full description of the services (`LogIngest`
for imports and `LogQuery` for searching, exporting, timelines, and log body retrieval)
with ready-to-run `grpcurl` snippets lives in [docs/GRPC_API.md](docs/GRPC_API.md).

In short:

* `LogIngest.Ingest` accepts a client-stream of log lines and persists them, populating
  structured attributes and `tf_log_bodies` when request/response payloads are detected.
* `LogQuery.Search` performs paginated and full-text queries across `message`, `module`,
  and `raw_json`, while other RPCs expose exports, timelines, and rich log details.

Because the server publishes gRPC reflection metadata, tools like `grpcurl` or any
generated gRPC client can call the API without REST adapters.
