CREATE DATABASE IF NOT EXISTS tf_logs;

CREATE TABLE IF NOT EXISTS tf_logs.log_entries_ch
(
    event_id         String,
    import_id        UUID,
    ts               DateTime64(3, 'UTC'),
    level            LowCardinality(String),
    section          LowCardinality(String),
    module           String,
    message          String,
    req_id           String,
    trans_id         String,
    rpc              LowCardinality(String),
    resource_type    LowCardinality(String),
    data_source_type LowCardinality(String),
    http_op_type     LowCardinality(String),
    status_code      UInt16,
    raw              String
    )
    ENGINE = MergeTree
    PARTITION BY toYYYYMMDD(ts)
    ORDER BY (ts, req_id, trans_id, event_id)
    TTL ts + INTERVAL 90 DAY
    SETTINGS index_granularity = 8192;
