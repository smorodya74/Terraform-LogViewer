package io.terraform.logviewer.service;

import io.terraform.logviewer.entity.LogBodyEntity;
import io.terraform.logviewer.entity.LogEntryEntity;
import io.terraform.logviewer.repository.LogBodyRepository;
import io.terraform.logviewer.repository.LogEntryRepository;
import io.terraform.logviewer.service.dto.GroupQueryResult;
import io.terraform.logviewer.service.dto.ImportSummary;
import io.terraform.logviewer.service.dto.LogGroupResult;
import io.terraform.logviewer.service.dto.QueryParameters;
import io.terraform.logviewer.service.dto.TimelinePoint;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class LogQueryService {

    private static final Map<String, String> FILTER_MAPPING = Map.of(
            "req_id", "reqId",
            "tf_req_id", "reqId",
            "trans_id", "transactionId",
            "rpc", "rpc",
            "resource_type", "resourceType",
            "tf_resource_type", "resourceType",
            "data_source_type", "dataSourceType",
            "http_op_type", "httpOperationType",
            "status_code", "statusCode",
            "import_id", "importId"
    );

    private static final Map<String, String> SORT_MAPPING = Map.ofEntries(
            Map.entry("ts", "timestamp"),
            Map.entry("level", "level"),
            Map.entry("section", "section"),
            Map.entry("module", "module"),
            Map.entry("message", "message"),
            Map.entry("req_id", "reqId"),
            Map.entry("trans_id", "transactionId"),
            Map.entry("rpc", "rpc"),
            Map.entry("resource_type", "resourceType"),
            Map.entry("data_source_type", "dataSourceType"),
            Map.entry("http_op_type", "httpOperationType"),
            Map.entry("status_code", "statusCode"),
            Map.entry("file_name", "fileName"),
            Map.entry("import_id", "importId")
    );

    private final LogEntryRepository entryRepository;
    private final LogBodyRepository bodyRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<ImportSummary> listImports() {
        return entryRepository.findImportSummaries().stream()
                .map(item -> new ImportSummary(
                        item.getImportId(),
                        item.getFileName(),
                        item.getTotal(),
                        item.getFirstTimestamp(),
                        item.getLastTimestamp()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<LogEntryEntity> search(QueryParameters parameters) {
        Pageable pageable = PageRequest.of(
                parameters.page(),
                parameters.size(),
                resolveSort(parameters)
        );
        Specification<LogEntryEntity> specification = buildSpecification(parameters);
        return entryRepository.findAll(specification, pageable);
    }

    @Transactional(readOnly = true)
    public GroupQueryResult searchGroups(QueryParameters parameters) {
        Specification<LogEntryEntity> specification = buildSpecification(parameters);
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<LogEntryEntity> countRoot = countQuery.from(LogEntryEntity.class);
        Predicate basePredicate = specification.toPredicate(countRoot, countQuery, cb);
        if (basePredicate != null) {
            countQuery.where(basePredicate);
        }
        Expression<String> countReqId = cb.coalesce(countRoot.get("reqId"), cb.literal(""));
        countQuery.select(cb.countDistinct(countReqId));
        long totalGroups = entityManager.createQuery(countQuery).getSingleResult();

        if (totalGroups == 0) {
            return new GroupQueryResult(0, List.of());
        }

        CriteriaQuery<Object[]> groupQuery = cb.createQuery(Object[].class);
        Root<LogEntryEntity> groupRoot = groupQuery.from(LogEntryEntity.class);
        Predicate groupPredicate = specification.toPredicate(groupRoot, groupQuery, cb);
        if (groupPredicate != null) {
            groupQuery.where(groupPredicate);
        }

        Expression<OffsetDateTime> timestampPath = groupRoot.get("timestamp").as(OffsetDateTime.class);
        Expression<String> groupReqId = cb.coalesce(groupRoot.get("reqId"), cb.literal(""));
        Expression<OffsetDateTime> minTimestamp = cb.least(timestampPath);
        Expression<OffsetDateTime> maxTimestamp = cb.greatest(timestampPath);

        groupQuery.multiselect(groupReqId, minTimestamp, maxTimestamp);
        groupQuery.groupBy(groupReqId);
        groupQuery.orderBy(cb.desc(maxTimestamp));

        TypedQuery<Object[]> typedGroupQuery = entityManager.createQuery(groupQuery);
        int offset = Math.max(parameters.page(), 0) * parameters.size();
        typedGroupQuery.setFirstResult(offset);
        typedGroupQuery.setMaxResults(parameters.size());

        List<Object[]> rawGroups = typedGroupQuery.getResultList();
        if (rawGroups.isEmpty()) {
            return new GroupQueryResult(totalGroups, List.of());
        }

        List<String> nonNullReqIds = new ArrayList<>();
        boolean includeNull = false;
        for (Object[] row : rawGroups) {
            String reqId = (String) row[0];
            if (reqId == null || reqId.isBlank()) {
                includeNull = true;
            } else {
                nonNullReqIds.add(reqId);
            }
        }

        CriteriaQuery<LogEntryEntity> entryQuery = cb.createQuery(LogEntryEntity.class);
        Root<LogEntryEntity> entryRoot = entryQuery.from(LogEntryEntity.class);
        Predicate entryPredicate = specification.toPredicate(entryRoot, entryQuery, cb);

        List<Predicate> predicates = new ArrayList<>();
        if (entryPredicate != null) {
            predicates.add(entryPredicate);
        }
        Predicate groupFilter = null;
        if (!nonNullReqIds.isEmpty()) {
            groupFilter = entryRoot.get("reqId").in(nonNullReqIds);
        }
        if (includeNull) {
            Predicate nullPredicate = cb.isNull(entryRoot.get("reqId"));
            groupFilter = groupFilter == null ? nullPredicate : cb.or(groupFilter, nullPredicate);
        }
        if (groupFilter != null) {
            predicates.add(groupFilter);
        }
        if (!predicates.isEmpty()) {
            entryQuery.where(cb.and(predicates.toArray(Predicate[]::new)));
        }

        entryQuery.orderBy(
                cb.asc(entryRoot.get("reqId")),
                cb.asc(entryRoot.get("timestamp")),
                cb.asc(entryRoot.get("id"))
        );

        List<LogEntryEntity> entries = entityManager.createQuery(entryQuery).getResultList();

        Map<String, List<LogEntryEntity>> groupedEntries = new LinkedHashMap<>();
        for (LogEntryEntity entry : entries) {
            String key = Optional.ofNullable(entry.getReqId()).orElse("");
            groupedEntries.computeIfAbsent(key, value -> new ArrayList<>()).add(entry);
        }
        groupedEntries.values().forEach(list ->
                list.sort((a, b) -> {
                    int cmp = a.getTimestamp().compareTo(b.getTimestamp());
                    if (cmp != 0) {
                        return cmp;
                    }
                    return Long.compare(a.getId(), b.getId());
                })
        );

        List<LogGroupResult> groups = new ArrayList<>(rawGroups.size());
        for (Object[] row : rawGroups) {
            String rawReqId = (String) row[0];
            String key = rawReqId == null ? "" : rawReqId;
            OffsetDateTime first = (OffsetDateTime) row[1];
            OffsetDateTime last = (OffsetDateTime) row[2];
            List<LogEntryEntity> items = groupedEntries.getOrDefault(key, List.of());
            groups.add(new LogGroupResult(
                    rawReqId == null || rawReqId.isBlank() ? null : rawReqId,
                    first,
                    last,
                    items
            ));
        }

        return new GroupQueryResult(totalGroups, groups);
    }

    @Transactional(readOnly = true)
    public Stream<LogEntryEntity> export(QueryParameters parameters) {
        Specification<LogEntryEntity> specification = buildSpecification(parameters);
        return entryRepository
                .findAll(specification, resolveSort(parameters))
                .stream();
    }

    @Transactional(readOnly = true)
    public List<LogGroupResult> exportGroups(QueryParameters parameters) {
        Specification<LogEntryEntity> specification = buildSpecification(parameters);
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<LogEntryEntity> query = cb.createQuery(LogEntryEntity.class);
        Root<LogEntryEntity> root = query.from(LogEntryEntity.class);
        Predicate predicate = specification.toPredicate(root, query, cb);
        if (predicate != null) {
            query.where(predicate);
        }

        query.orderBy(
                cb.asc(cb.coalesce(root.get("reqId"), cb.literal(""))),
                cb.asc(root.get("timestamp")),
                cb.asc(root.get("id"))
        );

        List<LogEntryEntity> entries = entityManager.createQuery(query).getResultList();
        if (entries.isEmpty()) {
            return List.of();
        }

        Map<String, List<LogEntryEntity>> grouped = new LinkedHashMap<>();
        for (LogEntryEntity entry : entries) {
            String key = Optional.ofNullable(entry.getReqId()).orElse("");
            grouped.computeIfAbsent(key, value -> new ArrayList<>()).add(entry);
        }

        List<LogGroupResult> results = new ArrayList<>(grouped.size());
        grouped.forEach((key, items) -> {
            OffsetDateTime first = items.stream()
                    .map(LogEntryEntity::getTimestamp)
                    .min(OffsetDateTime::compareTo)
                    .orElse(null);
            OffsetDateTime last = items.stream()
                    .map(LogEntryEntity::getTimestamp)
                    .max(OffsetDateTime::compareTo)
                    .orElse(null);
            results.add(new LogGroupResult(key.isBlank() ? null : key, first, last, List.copyOf(items)));
        });

        results.sort((a, b) -> {
            OffsetDateTime left = Optional.ofNullable(a.lastTimestamp()).orElse(OffsetDateTime.MIN);
            OffsetDateTime right = Optional.ofNullable(b.lastTimestamp()).orElse(OffsetDateTime.MIN);
            return right.compareTo(left);
        });

        return results;
    }

    @Transactional
    public int markRead(List<Long> ids, String reqId, boolean markRead) {
        if (!CollectionUtils.isEmpty(ids)) {
            return entryRepository.updateUnreadStatus(ids, !markRead);
        }
        if (StringUtils.hasText(reqId)) {
            return entryRepository.updateUnreadStatusByReqId(reqId, !markRead);
        }
        return 0;
    }

    @Transactional(readOnly = true)
    public Optional<LogEntryEntity> findById(long id) {
        return entryRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<LogBodyEntity> bodies(LogEntryEntity entry) {
        try (Stream<LogBodyEntity> stream = bodyRepository.findByLogEntry(entry)) {
            return stream.toList();
        }
    }

    @Transactional(readOnly = true)
    public List<TimelinePoint> timeline(Optional<String> reqId,
                                        Optional<OffsetDateTime> from,
                                        Optional<OffsetDateTime> to,
                                        Optional<String> importId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<LogEntryEntity> root = query.from(LogEntryEntity.class);

        List<Predicate> predicates = new ArrayList<>();
        reqId.filter(StringUtils::hasText)
                .ifPresent(value -> predicates.add(cb.equal(root.get("reqId"), value)));
        from.ifPresent(value -> predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), value)));
        to.ifPresent(value -> predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), value)));
        importId.filter(StringUtils::hasText)
                .ifPresent(value -> predicates.add(cb.equal(root.get("importId"), value)));

        var reqIdExpression = cb.coalesce(root.get("reqId"), cb.literal("unknown"));
        var importExpression = cb.coalesce(root.get("importId"), cb.literal(""));
        query.multiselect(
                reqIdExpression,
                cb.min(root.get("timestamp")),
                cb.max(root.get("timestamp")),
                cb.count(root),
                importExpression
        );
        query.groupBy(reqIdExpression, importExpression);
        if (!predicates.isEmpty()) {
            query.where(predicates.toArray(Predicate[]::new));
        }

        TypedQuery<Object[]> typedQuery = entityManager.createQuery(query);
        List<Object[]> raw = typedQuery.getResultList();
        if (raw.isEmpty()) return Collections.emptyList();

        return raw.stream()
                .map(row -> new TimelinePoint(
                        (String) row[0],
                        (OffsetDateTime) row[1],
                        (OffsetDateTime) row[2],
                        (Long) row[3],
                        (String) row[4]
                ))
                .collect(Collectors.toList());
    }

    private Specification<LogEntryEntity> buildSpecification(QueryParameters parameters) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            parameters.from().ifPresent(from ->
                    predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), from)));
            parameters.to().ifPresent(to ->
                    predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), to)));

            parameters.level().filter(StringUtils::hasText)
                    .ifPresent(level -> predicates.add(
                            cb.equal(cb.upper(root.get("level")), level.toUpperCase(Locale.ROOT))
                    ));

            parameters.section().filter(StringUtils::hasText)
                    .ifPresent(section -> predicates.add(
                            cb.equal(root.get("section"), section.toLowerCase(Locale.ROOT))
                    ));

            if (parameters.unreadOnly()) {
                predicates.add(cb.isTrue(root.get("unread")));
            }

            parameters.query().filter(StringUtils::hasText).ifPresent(queryText -> {
                String pattern = "%" + queryText.toLowerCase(Locale.ROOT) + "%";
                Predicate messageMatch = containsIgnoreCase(cb, root.get("message"), pattern);
                Predicate moduleMatch = containsIgnoreCase(cb, root.get("module"), pattern);
                Predicate jsonMatch = containsIgnoreCase(cb, root.get("rawJson"), pattern);
                predicates.add(cb.or(messageMatch, moduleMatch, jsonMatch));
            });

            parameters.filters().forEach((key, value) -> {
                if (!StringUtils.hasText(value)) return;

                String field = FILTER_MAPPING.get(key);
                if (field == null) return;

                if ("statusCode".equals(field)) {
                    try {
                        int status = Integer.parseInt(value);
                        predicates.add(cb.equal(root.get(field), status));
                    } catch (NumberFormatException ignored) {
                        // игнорируем некорректный статус
                    }
                } else {
                    predicates.add(cb.equal(lowerIgnoreNull(cb, root.get(field)), value.toLowerCase(Locale.ROOT)));
                }
            });

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Predicate containsIgnoreCase(CriteriaBuilder cb, Path<?> path, String pattern) {
        return cb.like(lowerIgnoreNull(cb, path), pattern);
    }

    private Expression<String> lowerIgnoreNull(CriteriaBuilder cb, Expression<?> expression) {
        return cb.lower(cb.coalesce(expression.as(String.class), cb.literal("")));
    }

    private Sort resolveSort(QueryParameters parameters) {
        Optional<String> requested = parameters.sortBy()
                .map(value -> value.toLowerCase(Locale.ROOT));
        if (requested.isPresent()) {
            String field = SORT_MAPPING.get(requested.get());
            if (field != null) {
                Sort.Direction direction = parameters.sortDesc() ? Sort.Direction.DESC : Sort.Direction.ASC;
                return Sort.by(direction, field).and(Sort.by(Sort.Direction.DESC, "id"));
            }
        }
        return Sort.by(Sort.Direction.DESC, "timestamp", "id");
    }
}
