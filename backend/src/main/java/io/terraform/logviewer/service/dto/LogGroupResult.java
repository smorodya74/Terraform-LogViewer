package io.terraform.logviewer.service.dto;

import io.terraform.logviewer.entity.LogEntryEntity;
import java.time.OffsetDateTime;
import java.util.List;

public record LogGroupResult(
        String reqId,
        OffsetDateTime firstTimestamp,
        OffsetDateTime lastTimestamp,
        List<LogEntryEntity> entries) {
}
