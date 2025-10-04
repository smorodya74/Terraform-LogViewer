package io.terraform.logviewer.grpc;

import io.terraform.logviewer.grpc.QueryRequest;
import io.terraform.logviewer.service.dto.QueryParameters;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class QueryRequestMapper {

    public QueryParameters toParameters(QueryRequest request) {
        int page = Math.max(request.getPage(), 0);
        int size = request.getSize() > 0 ? Math.min(request.getSize(), 500) : 50;

        Optional<OffsetDateTime> from = parseTimestamp(request.getTsFrom());
        Optional<OffsetDateTime> to = parseTimestamp(request.getTsTo());
        Optional<String> level = Optional.ofNullable(request.getLevel()).filter(StringUtils::hasText);
        Optional<String> section = Optional.ofNullable(request.getSection()).filter(StringUtils::hasText);
        Optional<String> query = Optional.ofNullable(request.getQ()).filter(StringUtils::hasText);

        Map<String, String> filters = new HashMap<>(request.getFiltersMap());

        Optional<String> sortBy = Optional.ofNullable(request.hasSortBy() ? request.getSortBy() : null)
                .filter(StringUtils::hasText);
        boolean sortDesc = request.hasSortDesc() && request.getSortDesc();
        boolean groupByReqId = request.hasGroupByReqId() && request.getGroupByReqId();

        return new QueryParameters(
                page,
                size,
                from,
                to,
                level,
                section,
                request.getUnreadOnly(),
                query,
                filters,
                sortBy,
                sortDesc,
                groupByReqId
        );
    }

    private Optional<OffsetDateTime> parseTimestamp(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(value));
        } catch (DateTimeParseException e) {
            log.debug("Unable to parse timestamp {}", value, e);
            return Optional.empty();
        }
    }
}
