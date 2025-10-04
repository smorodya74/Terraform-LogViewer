CREATE TABLE IF NOT EXISTS tf_log_entries (
    id BIGSERIAL PRIMARY KEY,
    ts TIMESTAMPTZ NOT NULL,
    level VARCHAR(16),
    section VARCHAR(16),
    module VARCHAR(255),
    message TEXT,
    req_id VARCHAR(128),
    trans_id VARCHAR(128),
    rpc VARCHAR(255),
    resource_type VARCHAR(255),
    data_source_type VARCHAR(255),
    http_op_type VARCHAR(32),
    status_code INTEGER,
    file_name VARCHAR(512),
    import_id VARCHAR(64),
    unread BOOLEAN NOT NULL DEFAULT TRUE,
    raw_json TEXT,
    attrs_json TEXT,
    annotations_json TEXT
);

CREATE INDEX IF NOT EXISTS idx_tf_log_entries_ts ON tf_log_entries (ts DESC);
CREATE INDEX IF NOT EXISTS idx_tf_log_entries_req_id ON tf_log_entries (req_id);
CREATE INDEX IF NOT EXISTS idx_tf_log_entries_section ON tf_log_entries (section);
CREATE INDEX IF NOT EXISTS idx_tf_log_entries_level ON tf_log_entries (level);

CREATE TABLE IF NOT EXISTS tf_log_bodies (
    id BIGSERIAL PRIMARY KEY,
    log_id BIGINT NOT NULL REFERENCES tf_log_entries(id) ON DELETE CASCADE,
    kind VARCHAR(32) NOT NULL,
    body_json TEXT
);

CREATE INDEX IF NOT EXISTS idx_tf_log_bodies_log_id ON tf_log_bodies (log_id);
