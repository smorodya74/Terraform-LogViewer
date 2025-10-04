package io.terraform.logviewer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.terraform.logviewer.entity.LogBodyEntity;
import io.terraform.logviewer.entity.LogEntryEntity;
import io.terraform.logviewer.parser.ParsedLogRecord;
import io.terraform.logviewer.parser.TerraformLogParser;
import io.terraform.logviewer.plugin.LogPluginGateway;
import io.terraform.logviewer.repository.LogBodyRepository;
import io.terraform.logviewer.repository.LogEntryRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class LogImportService {

    private final LogEntryRepository entryRepository;
    private final LogBodyRepository bodyRepository;
    private final TerraformLogParser parser;
    private final ObjectMapper objectMapper;
    private final LogPluginGateway pluginGateway;

    public LogImportService(LogEntryRepository entryRepository,
                            LogBodyRepository bodyRepository,
                            TerraformLogParser parser,
                            ObjectMapper objectMapper,
                            LogPluginGateway pluginGateway) {
        this.entryRepository = entryRepository;
        this.bodyRepository = bodyRepository;
        this.parser = parser;
        this.objectMapper = objectMapper;
        this.pluginGateway = pluginGateway;
    }

    public ImportSession startSession(String fileName) {
        return new ImportSession(UUID.randomUUID().toString(), fileName);
    }

    @Transactional
    public void ingestLine(ImportSession session, String raw) {
        session.total++;
        try {
            ParsedLogRecord record = parser.parse(raw, session.getContext());
            LogEntryEntity entity = toEntity(record, session);
            entryRepository.save(entity);
            persistBodies(entity, record.bodies());

            Map<String, String> pluginAnnotations = pluginGateway.process(record, entity);
            if (!pluginAnnotations.isEmpty()) {
                entity.setAnnotationsJson(writeJson(pluginAnnotations));
                entryRepository.save(entity);
            }
            session.saved++;
        } catch (Exception e) {
            session.failed++;
            log.warn("Failed to ingest line: {}", e.getMessage());
        }
    }

    private LogEntryEntity toEntity(ParsedLogRecord record, ImportSession session) {
        LogEntryEntity entity = new LogEntryEntity();
        entity.setTimestamp(defaultTimestamp(record.timestamp()));
        entity.setLevel(normalize(record.level()));
        entity.setSection(record.section());
        entity.setModule(record.module());
        entity.setMessage(record.message());
        entity.setReqId(record.reqId());
        entity.setTransactionId(record.transactionId());
        entity.setRpc(record.rpc());
        entity.setResourceType(record.resourceType());
        entity.setDataSourceType(record.dataSourceType());
        entity.setHttpOperationType(record.httpOperationType());
        entity.setStatusCode(record.statusCode());
        entity.setFileName(session.fileName);
        entity.setImportId(session.importId);
        entity.setRawJson(record.rawJson());
        entity.setAttrsJson(writeJson(record.attributes()));
        entity.setUnread(true);
        return entity;
    }

    private void persistBodies(LogEntryEntity entity, List<ParsedLogRecord.ParsedPayload> bodies) {
        if (bodies == null || bodies.isEmpty()) return;
        for (ParsedLogRecord.ParsedPayload payload : bodies) {
            LogBodyEntity bodyEntity = new LogBodyEntity();
            bodyEntity.setLogEntry(entity);
            bodyEntity.setKind(payload.kind());
            bodyEntity.setBodyJson(payload.json());
            bodyRepository.save(bodyEntity);
        }
    }

    private OffsetDateTime defaultTimestamp(OffsetDateTime timestamp) {
        return timestamp != null ? timestamp : OffsetDateTime.now();
    }

    private String normalize(String level) {
        return StringUtils.hasText(level) ? level.toUpperCase() : null;
    }

    private String writeJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return value.toString();
        }
    }

    @Getter
    public static class ImportSession {
        private final String importId;
        private final String fileName;
        private final TerraformLogParser.ImportContext context;
        private long total;
        private long saved;
        private long failed;

        public ImportSession(String importId, String fileName) {
            this.importId = importId;
            this.fileName = fileName;
            this.context = new TerraformLogParser.ImportContext(null, null);
        }
    }
}
