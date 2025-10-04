package io.terraform.logviewer.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnBean(name = "clickHouseJdbcTemplate")
public class ClickHouseInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClickHouseInitializer.class);

    private final JdbcTemplate ch;
    private final String db;

    public ClickHouseInitializer(@Qualifier("clickHouseJdbcTemplate") JdbcTemplate ch,
                                 @Value("${app.clickhouse.database:tf_logs}") String db) {
        this.ch = ch;
        this.db = db;
    }

    @PostConstruct
    public void init() {
        try {
            // 1) DB
            ch.execute("CREATE DATABASE IF NOT EXISTS " + db);

            // 2) Table
            final String tableFqn = db + ".log_entries_ch";
            final String ddl = """
                    CREATE TABLE IF NOT EXISTS %s (
                      event_id String,
                      import_id UUID,
                      ts DateTime64(3, 'UTC'),
                      level LowCardinality(String),
                      section LowCardinality(String),
                      module String,
                      message String,
                      req_id String,
                      trans_id String,
                      rpc String,
                      resource_type String,
                      data_source_type String,
                      http_op_type String,
                      status_code UInt16,
                      raw String
                    ) ENGINE = MergeTree
                    ORDER BY (ts, level, section)
                    """.formatted(tableFqn);

            ch.execute(ddl);
            LOGGER.info("ClickHouse initialized: database='{}', table='{}'", db, tableFqn);
        } catch (Exception e) {
            // Если ClickHouse недоступен — не валим приложение
            LOGGER.warn("ClickHouse init skipped: {}", e.getMessage());
        }
    }
}
