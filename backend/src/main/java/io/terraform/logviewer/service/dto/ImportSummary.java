package io.terraform.logviewer.service.dto;

import java.time.OffsetDateTime;

public record ImportSummary(
        String importId,
        String fileName,
        long total,
        OffsetDateTime firstTimestamp,
        OffsetDateTime lastTimestamp
) {
}
