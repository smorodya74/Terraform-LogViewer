package io.terraform.logviewer.parser;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ParsedLogRecord(
        OffsetDateTime timestamp,
        boolean timestampGuessed,
        String level,
        boolean levelGuessed,
        String section,
        String module,
        String message,
        String reqId,
        String transactionId,
        String rpc,
        String resourceType,
        String dataSourceType,
        String httpOperationType,
        Integer statusCode,
        Map<String, Object> attributes,
        List<ParsedPayload> bodies,
        String rawJson) {

    public record ParsedPayload(String kind, String json) {}
}
