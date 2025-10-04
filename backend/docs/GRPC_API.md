# Terraform Log Viewer gRPC API

The Terraform Log Viewer backend exposes two gRPC services on `localhost:9090`: `LogIngest`
for streaming log imports and `LogQuery` for searching and reading stored entries. gRPC
reflection is enabled, so tools such as `grpcurl` can discover the schema at runtime.

All timestamps use ISO-8601 with a UTC offset (`2024-08-01T12:00:00Z`). Log levels are
normalized to upper case.

## Getting Started

```bash
# Health check: list services (uses gRPC reflection)
grpcurl -plaintext localhost:9090 list

# Inspect the Search RPC signature
grpcurl -plaintext localhost:9090 describe logviewer.v1.LogQuery.Search
```

If you prefer static schemas, the protobuf definition is located at
`src/main/proto/logviwer.proto`.

## Import Logs (`LogIngest`)

`LogIngest.Ingest` is a client-streaming RPC. Each stream begins with a single
`ImportMeta` envelope that declares the logical file name. Every following envelope must
be a `LogLine` carrying the original JSON or plain-text line from the Terraform log. When
the client closes the stream, the server persists all lines and responds with an
`ImportResult` summary.

```bash
# Example: import a Terraform log file via grpcurl
{
  echo '{"meta":{"fileName":"plan.log"}}'
  sed 's/.*/{"line":{"rawJson":"&"}}/' plan.log
} | \
grpcurl -plaintext -d @ localhost:9090 logviewer.v1.LogIngest/Ingest
```

`ImportResult` includes the generated `import_id`, the `file_name`, and counters for
`total`, `saved`, and `failed` lines. If the parser extracts structured request/response
payloads, they are saved into `tf_log_bodies` and become available through the query API.

## Query Logs (`LogQuery`)

`LogQuery` serves read operations. Unless stated otherwise, all RPCs are unary and expect
JSON-encoded request/response bodies when called through tools such as `grpcurl`.

### Search

`Search(QueryRequest) -> QueryResponse`

- Supports pagination (`page`, `size`).
- Timestamp range filters (`ts_from`, `ts_to`).
- Attribute filters via `filters` map (`tf_req_id`, `tf_resource_type`, HTTP status code,
  etc.).
- Full-text search across `message`, `module`, and `raw_json` through the `q` field.
- Optional `section` filter (`plan`, `apply`, `unknown`) and `level` filter.
- Set `unread_only=true` to return only unread entries.

Example:

```bash
grpcurl -plaintext -d '{
  "page": 0,
  "size": 50,
  "q": "error",
  "section": "apply",
  "filters": {"tf_resource_type": "aws_s3_bucket"},
  "ts_from": "2024-08-01T00:00:00Z",
  "ts_to": "2024-08-01T23:59:59Z"
}' localhost:9090 logviewer.v1.LogQuery/Search
```

### Export

`Export(ExportRequest) -> stream LogRecord`

Streams every `LogRecord` that matches the embedded `QueryRequest`. Useful for exporting
large selections without pagination.

### MarkRead

`MarkRead(MarkReadRequest) -> MarkReadResponse`

Mark individual entries (`ids`) or every record with a specific `req_id` as read/unread.
Set `mark_read=false` to toggle entries back to unread.

### Timeline

`Timeline(TimelineRequest) -> stream TimelineItem`

Returns aggregated duration buckets grouped by `tf_req_id` within the optional timestamp
window. Each item contains `req_id`, `start_ts`, `end_ts`, and the number of log entries.

### GetLog

`GetLog(GetLogRequest) -> LogDetails`

Fetches the complete record for a single log entry, including:

- `record`: the same fields as `LogRecord`.
- `raw_json`: exact raw payload saved during import.
- `attrs_json`: JSON dump of extracted attributes (`tf_req_id`, tokens, annotations).
- `bodies_count`: how many related payloads are stored in `tf_log_bodies`.

### Bodies

`Bodies(BodiesRequest) -> stream BodyItem`

Streams request/response payloads (`kind` = `request` | `response`) captured during import.
Use this RPC in the UI to implement expandable JSON bodies.

## Payload Extraction

The parser walks through known Terraform log fields and nested objects to identify HTTP
payloads (`http_request`, `http_response`, `request_body`, `response_body`, `payload`,
custom provider keys, etc.). When a JSON object looks like a request or response body, it
is stored in `tf_log_bodies`. If your logs use different key names, extend the parser's
allow-list so the importer can recognize them.

## Error Handling

All RPCs return standard gRPC status codes:

- `INVALID_ARGUMENT` for malformed filters or pagination ranges.
- `NOT_FOUND` when requesting nonexistent log entries.
- `INTERNAL` when unexpected parsing or database errors occur (see server logs).

Client libraries should inspect the gRPC status details instead of relying on HTTP status
codes.

## Generating Client Stubs

Use `protoc` or your preferred build tooling with `src/main/proto/logviwer.proto` to
generate strongly typed clients. For quick experiments, `grpcurl` is enough because the
server exposes reflection metadata.
