package io.terraform.logviewer.service.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

public record QueryParameters(
        int page,
        int size,
        Optional<OffsetDateTime> from,
        Optional<OffsetDateTime> to,
        Optional<String> level,
        Optional<String> section,
        boolean unreadOnly,
        Optional<String> query,
        Map<String, String> filters,
        Optional<String> sortBy,
        boolean sortDesc,
        boolean groupByReqId) {
}
