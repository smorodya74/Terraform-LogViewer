package io.terraform.logviewer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Менеджер партиций: если tf_log_entries НЕ partitioned — НИЧЕГО не делает. */
@Service
public class PartitionManager {

    private final JdbcTemplate jdbc;

    @Value("${app.pg.partitioning.enabled:true}")
    private boolean enabled;

    private final Set<String> ensuredMonths = ConcurrentHashMap.newKeySet();
    private volatile Boolean baseIsPartitioned = null;

    public PartitionManager(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void ensurePartitionFor(Instant ts) {
        if (!enabled || ts == null) return;
        if (!isBasePartitioned()) return; // база не парт — выходим молча

        ZonedDateTime start = ts.atZone(ZoneOffset.UTC).withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneOffset.UTC);
        ZonedDateTime end = start.plusMonths(1);

        String suffix = "%04d%02d".formatted(start.getYear(), start.getMonthValue());
        if (!ensuredMonths.add(suffix)) return;

        String partName = "tf_log_entries_y" + suffix;

        Boolean exists = jdbc.query("""
                SELECT EXISTS (
                  SELECT 1
                  FROM pg_inherits i
                  JOIN pg_class   c  ON c.oid = i.inhrelid
                  JOIN pg_class   p  ON p.oid = i.inhparent
                  JOIN pg_namespace nc ON nc.oid = c.relnamespace
                  JOIN pg_namespace np ON np.oid = p.relnamespace
                  WHERE np.nspname='public' AND p.relname='tf_log_entries'
                    AND nc.nspname='public' AND c.relname=?
                )""",
                ps -> ps.setString(1, partName),
                rs -> rs.next() && rs.getBoolean(1));

        if (Boolean.TRUE.equals(exists)) return;

        String fromIso = DateTimeFormatter.ISO_INSTANT.format(start.toInstant());
        String toIso   = DateTimeFormatter.ISO_INSTANT.format(end.toInstant());

        String ddl = """
            CREATE TABLE public.%s
            PARTITION OF public.tf_log_entries
            FOR VALUES FROM ('%s'::timestamptz) TO ('%s'::timestamptz);
            """.formatted(partName, fromIso, toIso);

        try {
            jdbc.execute(ddl);
        } catch (Exception e) {
            String msg = String.valueOf(e.getMessage()).toLowerCase();
            if (!(msg.contains("already exists") || msg.contains("already a partition") || msg.contains("duplicate"))) {
                ensuredMonths.remove(suffix);
                throw e; // в норме сюда не попадём, потому что base не парт — ensurePartitionFor просто не вызовется
            }
        }
    }

    /** true, если public.tf_log_entries — partitioned (relkind='p'); иначе false */
    private boolean isBasePartitioned() {
        Boolean cached = baseIsPartitioned;
        if (cached != null) return cached;
        try {
            String relkind = jdbc.queryForObject("""
                SELECT c.relkind
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname='public' AND c.relname='tf_log_entries'
                """, String.class);
            baseIsPartitioned = "p".equals(relkind);
        } catch (Exception e) {
            baseIsPartitioned = false;
        }
        return baseIsPartitioned;
    }
}
