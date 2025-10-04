package io.terraform.logviewer.service.dto;

import java.time.OffsetDateTime;

public record TimelinePoint(
        String reqId,
        OffsetDateTime start,
        OffsetDateTime end,
        long count,
        String importId
) {
}
