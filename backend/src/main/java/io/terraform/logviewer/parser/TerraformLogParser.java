package io.terraform.logviewer.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class TerraformLogParser {

    // --- Field name dictionaries ---------------------------------------------------------------

    private static final Set<String> TIMESTAMP_FIELDS = Set.of(
            "@timestamp", "timestamp", "ts", "time", "datetime", "logged_at", "created_at");

    private static final Set<String> LEVEL_FIELDS = Set.of(
            "level", "lvl", "severity", "log_level", "@level", "priority");

    // IMPORTANT: include @message because tflog uses it
    private static final Set<String> MESSAGE_FIELDS = Set.of(
            "@message", "message", "msg", "event", "log", "body");

    // --- Regexes -----------------------------------------------------------------------------

    // ISO-8601 at the start of the line (with Z or offset)
    private static final Pattern ISO_PREFIX = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:[.\\d]*)(?:Z|[+-]\\d{2}:?\\d{2}))");

    private static final Pattern LEVEL_PREFIX = Pattern.compile(
            "^(?:\\[|)(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)(?:\\]|:|\\s)", Pattern.CASE_INSENSITIVE);

    // key=value tokens in plain text logs
    private static final Pattern KV_PATTERN = Pattern.compile("(?<!\\S)([A-Za-z0-9_.-]+)=([^\\s]+)");

    // module.name tokens
    private static final Pattern MODULE_PATTERN = Pattern.compile("\\bmodule\\.[A-Za-z0-9_.-]+(?:\\.[A-Za-z0-9_.-]+)?");

    // CLI args lines (Terraform tflog)
    private static final Pattern CLI_APPLY = Pattern.compile("\\bcli\\s+(?:command\\s+)?args?[^\\n]*\\bapply\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CLI_PLAN = Pattern.compile("\\bcli\\s+(?:command\\s+)?args?[^\\n]*\\bplan\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // Backend messages
    private static final Pattern STARTING_APPLY_OP = Pattern.compile("\\bbackend/local:\\s*starting\\s*apply\\s*operation\\b");
    private static final Pattern STARTING_PLAN_OP  = Pattern.compile("\\bbackend/local:\\s*starting\\s*plan\\s*operation\\b");
    private static final Pattern APPLY_OP_COMPLETED = Pattern.compile("\\bapply\\s*operation\\s*completed\\b");
    private static final Pattern PLAN_OP_COMPLETED  = Pattern.compile("\\bplan\\s*operation\\s*completed\\b");

    // Other strong apply hints present in real logs
    private static final Pattern APPLY_CALLING_APPLY = Pattern.compile("\\bapply\\s+calling\\s+apply\\b");
    private static final Pattern APPLY_WALK_GRAPH    = Pattern.compile("\\bbuilding\\s+and\\s+walking\\s+apply\\s+graph\\b");

    // Noise tokens that should NOT bias plan/apply classification by substring alone
    private static final List<Pattern> NEUTRAL_NOISE = List.of(
            Pattern.compile("planresourcechange"),            // tf_rpc value
            Pattern.compile("getproviderschema"),
            Pattern.compile("validateresourceconfig"),
            Pattern.compile("validatedataresourceconfig"),
            Pattern.compile("upgraderesourcestate"),
            Pattern.compile("vertex\\s+\\\""),
            Pattern.compile("schema\\s+for\\s+provider"),
            Pattern.compile("statemgr\\.filesystem"),
            Pattern.compile("sdk\\.proto"),
            Pattern.compile("fwserver\\/server\\.go"),
            Pattern.compile("tf_proto_version"),
            Pattern.compile("tf_provider_addr"),
            Pattern.compile("tf_rpc"),
            Pattern.compile("tf_resource_type"),
            Pattern.compile("tf_data_source_type")
    );

    // Heuristics tuned for your tflogs (order doesn't matter).
    private static final List<SectionHeuristic> PLAN_HEURISTICS = List.of(
            // hard, explicit
            new SectionHeuristic(STARTING_PLAN_OP, 8, true),
            new SectionHeuristic(PLAN_OP_COMPLETED, 7, true),
            new SectionHeuristic(CLI_PLAN, 7, true),

            // soft, contextual
            new SectionHeuristic(Pattern.compile("\\bterraform(?:\\s|-|:)plan\\b"), 6),
            new SectionHeuristic(Pattern.compile("\\bplan\\s+phase\\b"), 4),
            new SectionHeuristic(Pattern.compile("\\bstarting\\s+plan\\b"), 4),
            new SectionHeuristic(Pattern.compile("\\bgenerating\\s+plan\\b"), 4),
            new SectionHeuristic(Pattern.compile("\\brefresh(?:ing)?\\s+state\\b"), 2),
            new SectionHeuristic(Pattern.compile("\\bdry\\s*-?run\\b"), 3),
            new SectionHeuristic(Pattern.compile("\\bspeculative\\s+run\\b"), 3),
            new SectionHeuristic(Pattern.compile("\\bplan\\s+summary\\b"), 3),
            new SectionHeuristic(Pattern.compile("\\bplan\\s+output\\b"), 2),
            new SectionHeuristic(Pattern.compile("\\bplanned\\s+actions\\b"), 2)
    );

    private static final List<SectionHeuristic> APPLY_HEURISTICS = List.of(
            // hard, explicit
            new SectionHeuristic(STARTING_APPLY_OP, 8, true),
            new SectionHeuristic(APPLY_OP_COMPLETED, 7, true),
            new SectionHeuristic(CLI_APPLY, 7, true),
            new SectionHeuristic(APPLY_CALLING_APPLY, 7, true),
            new SectionHeuristic(APPLY_WALK_GRAPH, 6, true),

            // soft, contextual
            new SectionHeuristic(Pattern.compile("\\bterraform(?:\\s|-|:)apply\\b"), 6),
            new SectionHeuristic(Pattern.compile("\\bapply\\s+phase\\b"), 4),
            new SectionHeuristic(Pattern.compile("\\bapply\\s+start(?:ed|ing)?\\b"), 4),
            new SectionHeuristic(Pattern.compile("\\bapply\\s+complete\\b"), 5),
            new SectionHeuristic(Pattern.compile("\\bapply\\s+failed\\b"), 5),
            new SectionHeuristic(Pattern.compile("\\bcreation\\s+complete\\b"), 3),
            new SectionHeuristic(Pattern.compile("\\bcreating\\.{3}"), 2),
            new SectionHeuristic(Pattern.compile("\\bmodifying\\.{3}"), 2),
            new SectionHeuristic(Pattern.compile("\\bupdating\\.{3}"), 2)
    );

    private static final DateTimeFormatter[] TIMESTAMP_FORMATTERS = new DateTimeFormatter[]{
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_INSTANT,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS][XXX]").withZone(ZoneOffset.UTC),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss[.SSS][XXX]").withZone(ZoneOffset.UTC)
    };

    private final ObjectMapper objectMapper;

    // --- Public API ---------------------------------------------------------------------------

    public ParsedLogRecord parse(String raw, ImportContext context) {
        if (!StringUtils.hasText(raw)) {
            return context.withDefaults(ParsedLogRecordBuilder.empty(context.getLastTimestamp(), context.getLastLevel()));
        }

        JsonNode root = tryParseJson(raw).orElse(null);
        if (root instanceof ObjectNode objectNode) {
            return parseJson(objectNode, raw, context);
        }
        return parsePlain(raw, context);
    }

    // --- JSON path ----------------------------------------------------------------------------

    private ParsedLogRecord parseJson(ObjectNode node, String raw, ImportContext context) {
        OffsetDateTime ts = findTimestamp(node).orElse(null);
        boolean tsGuessed = ts == null;
        if (ts == null) ts = context.getLastTimestamp();
        if (ts == null) {
            ts = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
            tsGuessed = true;
        }

        String level = findLevel(node).orElse(null);
        boolean levelGuessed = level == null;
        if (level == null) level = context.getLastLevel();
        if (level == null) {
            level = "INFO";
            levelGuessed = true;
        }

        String message = findFirstString(node, MESSAGE_FIELDS).orElseGet(node::toString);

        Map<String, Object> attrs = extractAttributes(node);
        Map<String, String> kvTokens = parseKeyValueTokens(message);

        List<ParsedLogRecord.ParsedPayload> bodies = new ArrayList<>();
        addIfPresent(node, bodies, "http_request", "request");
        addIfPresent(node, bodies, "http_response", "response");
        addIfPresent(node, bodies, "request_body", "request");
        addIfPresent(node, bodies, "response_body", "response");
        addIfPresent(node, bodies, "request", "request");
        addIfPresent(node, bodies, "response", "response");
        collectNestedBodies(node, bodies);

        String section = resolveSection(node, message, kvTokens);
        String module = findModule(node, message, kvTokens);
        String reqId = findFirstStringDeep(node, Set.of("req_id", "request_id", "tf_req_id", "requestId"))
                .orElseGet(() -> findFirstStringInTokens(kvTokens, Set.of("req_id", "request_id", "tf_req_id")));
        String transactionId = findFirstStringDeep(node, Set.of("trans_id", "transaction_id", "trace_id", "tf_trans_id"))
                .orElseGet(() -> findFirstStringInTokens(kvTokens, Set.of("trans_id", "transaction_id", "trace_id", "tf_trans_id")));
        String rpc = findFirstStringDeep(node, Set.of("rpc", "rpc_method", "rpc_name", "rpc_call", "operation", "tf_rpc"))
                .orElseGet(() -> findFirstStringInTokens(kvTokens, Set.of("rpc", "rpc_method", "rpc_name")));
        String resourceType = findFirstStringDeep(node, Set.of("tf_resource_type", "resource_type"))
                .orElseGet(() -> findFirstStringInTokens(kvTokens, Set.of("tf_resource_type", "resource_type")));
        String dataSourceType = findFirstStringDeep(node, Set.of("data_source_type", "tf_data_source_type"))
                .orElseGet(() -> findFirstStringInTokens(kvTokens, Set.of("data_source_type", "tf_data_source_type")));
        String httpOperationType = normalizeHttpMethod(findFirstStringDeep(node,
                Set.of("http_op_type", "method", "http_method", "http_verb", "verb"))
                .orElseGet(() -> findFirstStringInTokens(kvTokens, Set.of("http_op_type", "method", "http_method", "http_verb", "verb"))));
        Integer statusCode = findFirstIntDeep(node, Set.of("status_code", "http_status", "status", "statusCode"));
        if (statusCode == null) {
            statusCode = parseInteger(findFirstStringInTokens(kvTokens, Set.of("status_code", "http_status", "status", "statusCode")));
        }

        ParsedLogRecordBuilder builder = ParsedLogRecordBuilder.empty(ts, level)
                .timestampGuessed(tsGuessed)
                .levelGuessed(levelGuessed)
                .section(section)
                .module(module)
                .message(message)
                .reqId(reqId)
                .transactionId(transactionId)
                .rpc(rpc)
                .resourceType(resourceType)
                .dataSourceType(dataSourceType)
                .httpOperationType(httpOperationType)
                .statusCode(statusCode)
                .attributes(attrs)
                .bodies(bodies)
                .rawJson(raw);

        return context.withDefaults(builder);
    }

    // --- Plain text path ----------------------------------------------------------------------

    private ParsedLogRecord parsePlain(String raw, ImportContext context) {
        OffsetDateTime ts = extractTimestampFromText(raw).orElse(context.getLastTimestamp());
        boolean tsGuessed = ts == null;
        if (ts == null) {
            ts = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
            tsGuessed = true;
        }

        String level = extractLevelFromText(raw).orElse(context.getLastLevel());
        boolean levelGuessed = level == null;
        if (level == null) {
            level = "INFO";
            levelGuessed = true;
        }

        Map<String, String> kvTokens = parseKeyValueTokens(raw);

        ParsedLogRecordBuilder builder = ParsedLogRecordBuilder.empty(ts, level)
                .timestampGuessed(tsGuessed)
                .levelGuessed(levelGuessed)
                .section(resolveSection(null, raw, kvTokens))
                .module(findModule(null, raw, kvTokens))
                .message(raw)
                .reqId(findFirstStringInTokens(kvTokens, Set.of("req_id", "request_id", "tf_req_id")))
                .transactionId(findFirstStringInTokens(kvTokens, Set.of("trans_id", "transaction_id", "trace_id", "tf_trans_id")))
                .rpc(findFirstStringInTokens(kvTokens, Set.of("rpc", "rpc_method", "rpc_name")))
                .resourceType(findFirstStringInTokens(kvTokens, Set.of("tf_resource_type", "resource_type")))
                .dataSourceType(findFirstStringInTokens(kvTokens, Set.of("data_source_type", "tf_data_source_type")))
                .httpOperationType(normalizeHttpMethod(findFirstStringInTokens(kvTokens, Set.of("http_op_type", "method", "http_method", "http_verb", "verb"))))
                .statusCode(parseInteger(findFirstStringInTokens(kvTokens, Set.of("status_code", "http_status", "status", "statusCode"))))
                .attributes(Map.of())
                .bodies(List.of())
                .rawJson(buildPlainJson(raw, ts, level));

        return context.withDefaults(builder);
    }

    // --- Helpers: JSON/fields -----------------------------------------------------------------

    private Optional<JsonNode> tryParseJson(String raw) {
        try {
            return Optional.ofNullable(objectMapper.readTree(raw));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private Optional<OffsetDateTime> findTimestamp(ObjectNode node) {
        for (String field : TIMESTAMP_FIELDS) {
            JsonNode value = node.get(field);
            if (value != null && value.isValueNode()) {
                Optional<OffsetDateTime> parsed = parseTimestampNode(value);
                if (parsed.isPresent()) return parsed;
            }
        }
        return Optional.empty();
    }

    private Optional<OffsetDateTime> parseTimestampNode(JsonNode node) {
        if (node.isNumber()) {
            long epochMillis = node.asLong();
            if (String.valueOf(epochMillis).length() <= 10) {
                epochMillis *= 1000;
            }
            return Optional.of(OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC));
        }
        if (node.isTextual()) {
            String text = node.asText();
            for (DateTimeFormatter formatter : TIMESTAMP_FORMATTERS) {
                try {
                    return Optional.of(OffsetDateTime.parse(text, formatter));
                } catch (DateTimeParseException ignored) {
                }
                try {
                    Instant instant = formatter.parse(text, Instant::from);
                    return Optional.of(OffsetDateTime.ofInstant(instant, ZoneOffset.UTC));
                } catch (DateTimeParseException ignored) {
                }
            }
            return extractTimestampFromText(text);
        }
        return Optional.empty();
    }

    private Optional<OffsetDateTime> extractTimestampFromText(String raw) {
        Matcher matcher = ISO_PREFIX.matcher(raw);
        if (matcher.find()) {
            String iso = matcher.group(1);
            try {
                return Optional.of(OffsetDateTime.parse(iso));
            } catch (DateTimeParseException ignored) { }
        }
        return Optional.empty();
    }

    private Optional<String> findLevel(ObjectNode node) {
        for (String field : LEVEL_FIELDS) {
            JsonNode value = node.get(field);
            if (value != null && value.isValueNode()) {
                String text = value.asText(null);
                if (StringUtils.hasText(text)) {
                    return Optional.of(text.toUpperCase(Locale.ROOT));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> extractLevelFromText(String raw) {
        if (!StringUtils.hasText(raw)) return Optional.empty();
        String candidate = raw.trim();
        for (int i = 0; i < 3 && StringUtils.hasText(candidate); i++) {
            Matcher matcher = LEVEL_PREFIX.matcher(candidate);
            if (matcher.find()) {
                return Optional.ofNullable(matcher.group(1)).map(s -> s.toUpperCase(Locale.ROOT));
            }
            int spaceIndex = candidate.indexOf(' ');
            if (spaceIndex < 0) break;
            candidate = candidate.substring(spaceIndex + 1).trim();
        }
        return Optional.empty();
    }

    private Optional<String> findFirstString(ObjectNode node, Set<String> candidates) {
        for (String field : candidates) {
            JsonNode value = node.get(field);
            if (value != null && value.isValueNode()) {
                String text = value.asText(null);
                if (StringUtils.hasText(text)) return Optional.of(text);
            }
        }
        return Optional.empty();
    }

    private Optional<String> findFirstStringDeep(JsonNode node, Set<String> candidates) {
        return findFirstStringDeep(node, candidates, 3);
    }

    private Optional<String> findFirstStringDeep(JsonNode node, Set<String> candidates, int depth) {
        if (node == null || depth < 0 || candidates.isEmpty()) return Optional.empty();
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            for (String field : candidates) {
                JsonNode value = objectNode.get(field);
                if (value != null && value.isValueNode()) {
                    String text = value.asText(null);
                    if (StringUtils.hasText(text)) {
                        return Optional.of(text);
                    }
                }
            }
            Iterator<Map.Entry<String, JsonNode>> iterator = objectNode.fields();
            while (iterator.hasNext()) {
                JsonNode child = iterator.next().getValue();
                Optional<String> nested = findFirstStringDeep(child, candidates, depth - 1);
                if (nested.isPresent()) {
                    return nested;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                Optional<String> nested = findFirstStringDeep(child, candidates, depth - 1);
                if (nested.isPresent()) {
                    return nested;
                }
            }
        }
        return Optional.empty();
    }

    private Integer findFirstIntDeep(JsonNode node, Set<String> candidates) {
        return findFirstIntDeep(node, candidates, 3);
    }

    private Integer findFirstIntDeep(JsonNode node, Set<String> candidates, int depth) {
        if (node == null || depth < 0 || candidates.isEmpty()) return null;
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            for (String field : candidates) {
                JsonNode value = objectNode.get(field);
                Integer parsed = parseIntegerNode(value);
                if (parsed != null) return parsed;
            }
            Iterator<Map.Entry<String, JsonNode>> iterator = objectNode.fields();
            while (iterator.hasNext()) {
                JsonNode child = iterator.next().getValue();
                Integer nested = findFirstIntDeep(child, candidates, depth - 1);
                if (nested != null) return nested;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                Integer nested = findFirstIntDeep(child, candidates, depth - 1);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private Integer parseIntegerNode(JsonNode value) {
        if (value == null) return null;
        if (value.isNumber()) {
            return value.asInt();
        }
        if (value.isTextual()) {
            return parseInteger(value.asText());
        }
        return null;
    }

    // --- Feature extraction (module, attrs, payloads) -----------------------------------------

    private String findModule(ObjectNode node, String message, Map<String, String> tokens) {
        Optional<String> direct = findFirstStringDeep(node,
                Set.of("module", "module_path", "module_addr", "module_name", "module_address", "moduleId",
                        "moduleID", "moduleKey", "moduleDisplayName", "component", "logger", "source", "@module"));
        if (direct.isPresent()) return direct.get();

        String fromTokens = findFirstStringInTokens(tokens,
                Set.of("module", "module_path", "module_addr", "module_name", "component", "logger", "@module"));
        if (StringUtils.hasText(fromTokens)) return fromTokens;

        String fromMessage = extractModuleFromMessage(message);
        if (StringUtils.hasText(fromMessage)) return fromMessage;

        return null;
    }

    private Map<String, Object> extractAttributes(ObjectNode node) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            attrs.put(entry.getKey(), simplify(entry.getValue()));
        }
        return attrs;
    }

    private Object simplify(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isValueNode()) {
            if (node.isNumber()) return node.numberValue();
            if (node.isBoolean()) return node.booleanValue();
            return node.asText();
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return node.toString();
        }
    }

    private void addIfPresent(ObjectNode node, List<ParsedLogRecord.ParsedPayload> bodies, String field, String kind) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return;

        String json = toJsonString(value);
        if (StringUtils.hasText(json)) {
            bodies.add(new ParsedLogRecord.ParsedPayload(kind, json));
        }
    }

    private void collectNestedBodies(ObjectNode node, List<ParsedLogRecord.ParsedPayload> bodies) {
        if (node == null) return;
        Set<String> seen = new HashSet<>();
        for (ParsedLogRecord.ParsedPayload payload : bodies) {
            seen.add(payload.kind() + ":" + payload.json());
        }
        collectNestedBodies(node, bodies, seen, 0, null);
    }

    private void collectNestedBodies(JsonNode node,
                                     List<ParsedLogRecord.ParsedPayload> bodies,
                                     Set<String> seen,
                                     int depth,
                                     String fieldName) {
        if (node == null || depth > 5) {
            return;
        }
        if (node.isObject()) {
            if (isBodyCandidate(fieldName, node)) {
                addPayload(node, fieldName, bodies, seen);
            }
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> iterator = objectNode.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                collectNestedBodies(entry.getValue(), bodies, seen, depth + 1, entry.getKey());
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                collectNestedBodies(item, bodies, seen, depth + 1, fieldName);
            }
        } else if (isBodyCandidate(fieldName, node)) {
            addPayload(node, fieldName, bodies, seen);
        }
    }

    private void addPayload(JsonNode value,
                            String fieldName,
                            List<ParsedLogRecord.ParsedPayload> bodies,
                            Set<String> seen) {
        if (value == null) return;
        String json = toJsonString(value);
        if (!StringUtils.hasText(json)) return;
        String kind = resolvePayloadKind(fieldName);
        String dedupeKey = kind + ":" + json;
        if (seen.add(dedupeKey)) {
            bodies.add(new ParsedLogRecord.ParsedPayload(kind, json));
        }
    }

    private String resolvePayloadKind(String fieldName) {
        if (!StringUtils.hasText(fieldName)) {
            return "payload";
        }
        String lower = fieldName.toLowerCase(Locale.ROOT);
        if (lower.contains("request") && !lower.contains("response")) {
            return "request";
        }
        if (lower.contains("response")) {
            return "response";
        }
        if (lower.contains("error")) {
            return "error";
        }
        if (lower.contains("payload") || lower.contains("body") || lower.contains("content")) {
            return "payload";
        }
        return "body";
    }

    private boolean isBodyCandidate(String fieldName, JsonNode value) {
        if (!StringUtils.hasText(fieldName) || value == null) {
            return false;
        }
        String lower = fieldName.toLowerCase(Locale.ROOT);
        boolean looksLikeId = lower.endsWith("_id") || lower.endsWith("id") || lower.contains("request_id")
                || lower.contains("response_code") || lower.contains("status");
        if (looksLikeId) {
            return false;
        }

        boolean isRequest = lower.contains("request") && !lower.contains("request_id");
        boolean isResponse = lower.contains("response") && !lower.contains("response_code");
        boolean isPayload = lower.contains("payload") || lower.contains("body") || lower.contains("content");
        boolean candidate = isRequest || isResponse || isPayload;
        if (!candidate) {
            return false;
        }

        if (value.isObject() || value.isArray()) {
            return value.size() > 0;
        }
        if (value.isTextual()) {
            String text = value.asText();
            if (!StringUtils.hasText(text)) return false;
            String trimmed = text.trim();
            if (trimmed.length() < 16) return false;
            if (trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("<")) return true;
            return trimmed.length() >= 80;
        }
        return false;
    }

    private String toJsonString(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isValueNode()) {
            return value.asText();
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return value.toString();
        }
    }

    // --- Section classification ---------------------------------------------------------------

    private String resolveSection(ObjectNode node, String message, Map<String, String> tokens) {
        // 1) Explicit fields, if integrators pass them
        Optional<String> explicitField = findFirstStringDeep(node,
                Set.of("section", "phase", "stage", "operation", "command", "action", "step", "terraform_phase",
                        "terraform_operation", "event_section", "phase_type"));
        String normalized = explicitField.map(this::normalizeSectionValue)
                .orElseGet(() -> normalizeSectionValue(findFirstStringInTokens(tokens,
                        Set.of("section", "phase", "stage", "operation", "command", "action", "step"))));
        if (normalized != null) return normalized;

        // 2) Strong signals in message (CLI args and backend/local)
        String m = Optional.ofNullable(message).orElse("");
        String lower = m.toLowerCase(Locale.ROOT);

        if (CLI_APPLY.matcher(lower).find() || STARTING_APPLY_OP.matcher(lower).find()
                || APPLY_CALLING_APPLY.matcher(lower).find() || APPLY_WALK_GRAPH.matcher(lower).find()
                || APPLY_OP_COMPLETED.matcher(lower).find()) {
            return "apply";
        }
        if (CLI_PLAN.matcher(lower).find() || STARTING_PLAN_OP.matcher(lower).find()
                || PLAN_OP_COMPLETED.matcher(lower).find()) {
            return "plan";
        }

        // 3) Token sweep (still allow hints in tokens)
        String fromTokens = tokens.values().stream()
                .map(this::normalizeSectionValue)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
        if (StringUtils.hasText(fromTokens)) return fromTokens;

        // 4) Scoring over fields, tokens, and message with tuned heuristics
        SectionScore score = new SectionScore();
        if (node != null) {
            scorePotentialCommandFields(node, score);
        }
        tokens.forEach((key, value) -> {
            scoreText(key, score, 1);
            scoreText(value, score, 2);
        });
        scoreText(m, score, 3);

        String resolved = score.resolve();
        if (resolved != null) return resolved;

        // 5) Conservative fallback (keep "unknown" if we only saw framework noise)
        return "unknown";
    }

    private void scorePotentialCommandFields(ObjectNode node, SectionScore score) {
        for (String field : List.of("command", "cli_command", "terraform_command", "operation")) {
            findFirstStringDeep(node, Set.of(field))
                    .ifPresent(value -> scoreText(value, score, 4));
        }
        JsonNode terraformNode = node.get("terraform");
        if (terraformNode instanceof ObjectNode terraformObject) {
            scoreNode(terraformObject.get("command"), score, 5);
            scoreNode(terraformObject.get("cli_command"), score, 5);
            scoreNode(terraformObject.get("operation"), score, 4);
            scoreNode(terraformObject.get("phase"), score, 3);
            scoreNode(terraformObject.get("stage"), score, 3);
            JsonNode cliArgs = terraformObject.get("cli_args");
            if (containsText(cliArgs, "apply")) {
                score.hardApply = true;
            }
            if (containsText(cliArgs, "plan")) {
                score.hardPlan = true;
            }
            scoreNode(cliArgs, score, 5);
            scoreNode(terraformObject.get("arguments"), score, 3);
        }
        // tflog specific keys occasionally surface operation hint
        for (String f : List.of("@message", "message")) {
            JsonNode v = node.get(f);
            if (v != null && v.isTextual()) scoreText(v.asText(), score, 4);
        }
    }

    private void scoreNode(JsonNode node, SectionScore score, int multiplier) {
        if (node == null || node.isNull()) return;
        if (node.isTextual()) {
            scoreText(node.asText(), score, multiplier);
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                if (element != null && element.isTextual()) {
                    scoreText(element.asText(), score, multiplier);
                }
            }
        }
    }

    private boolean containsText(JsonNode node, String keyword) {
        if (node == null || !StringUtils.hasText(keyword)) {
            return false;
        }
        String needle = keyword.toLowerCase(Locale.ROOT);
        if (node.isTextual()) {
            return node.asText().toLowerCase(Locale.ROOT).contains(needle);
        }
        if (node.isArray()) {
            for (JsonNode element : node) {
                if (containsText(element, keyword)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void scoreText(String text, SectionScore score, int multiplier) {
        if (!StringUtils.hasText(text)) return;

        String normalized = text.toLowerCase(Locale.ROOT);

        // Ignore framework noise which used to bias towards "plan" (PlanResourceChange etc.)
        for (Pattern p : NEUTRAL_NOISE) {
            if (p.matcher(normalized).find()) return;
        }

        // Hard signals
        boolean hardApply = STARTING_APPLY_OP.matcher(normalized).find()
                || APPLY_OP_COMPLETED.matcher(normalized).find()
                || APPLY_CALLING_APPLY.matcher(normalized).find()
                || APPLY_WALK_GRAPH.matcher(normalized).find()
                || CLI_APPLY.matcher(normalized).find();
        boolean hardPlan = STARTING_PLAN_OP.matcher(normalized).find()
                || PLAN_OP_COMPLETED.matcher(normalized).find()
                || CLI_PLAN.matcher(normalized).find();

        if (hardApply) score.hardApply = true;
        if (hardPlan) score.hardPlan = true;

        // Weighted heuristics
        for (SectionHeuristic h : PLAN_HEURISTICS) {
            if (h.pattern().matcher(normalized).find()) {
                score.plan += h.weight() * multiplier;
                if (h.hard()) score.hardPlan = true;
            }
        }
        for (SectionHeuristic h : APPLY_HEURISTICS) {
            if (h.pattern().matcher(normalized).find()) {
                score.apply += h.weight() * multiplier;
                if (h.hard()) score.hardApply = true;
            }
        }

        // Small generic boost ONLY for whole words "plan"/"apply"
        if (Pattern.compile("\\bplan\\b").matcher(normalized).find()
                && !Pattern.compile("\\bapply\\b").matcher(normalized).find()) {
            score.plan += 1 * multiplier;
        } else if (Pattern.compile("\\bapply\\b").matcher(normalized).find()
                && !Pattern.compile("\\bplan\\b").matcher(normalized).find()) {
            score.apply += 1 * multiplier;
        }
    }

    private record SectionHeuristic(Pattern pattern, int weight, boolean hard) {
        SectionHeuristic(Pattern pattern, int weight) { this(pattern, weight, false); }
    }

    private static class SectionScore {
        private int plan;
        private int apply;
        private boolean hardPlan;
        private boolean hardApply;

        private String resolve() {
            // Hard signals decide first
            if (hardApply ^ hardPlan) return hardApply ? "apply" : "plan";
            if (hardApply && hardPlan) return null; // contradictory -> unknown

            // Otherwise require a clear gap and sufficient strength
            if (apply >= plan + 3 && apply >= 6) return "apply";
            if (plan  >= apply + 3 && plan  >= 6) return "plan";

            // If weak/ambiguous → unknown
            return null;
        }
    }

    // --- JSON builder for plain line ----------------------------------------------------------

    private String buildPlainJson(String message, OffsetDateTime ts, String level) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("message", message);
        if (ts != null) node.put("timestamp", ts.toString());
        if (level != null) node.put("level", level);
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return node.toString();
        }
    }

    // --- Import context -----------------------------------------------------------------------

    public static class ImportContext {
        private OffsetDateTime lastTimestamp;
        private String lastLevel;

        public ImportContext(OffsetDateTime lastTimestamp, String lastLevel) {
            this.lastTimestamp = lastTimestamp;
            this.lastLevel = lastLevel;
        }

        public OffsetDateTime getLastTimestamp() { return lastTimestamp; }
        public String getLastLevel() { return lastLevel; }

        public ParsedLogRecord withDefaults(ParsedLogRecordBuilder builder) {
            ParsedLogRecord record = builder.build();
            this.lastTimestamp = record.timestamp();
            this.lastLevel = record.level();
            return record;
        }
    }

    // --- Tokenization & small utils -----------------------------------------------------------

    private Map<String, String> parseKeyValueTokens(String message) {
        Map<String, String> tokens = new LinkedHashMap<>();
        if (!StringUtils.hasText(message)) return tokens;
        Matcher matcher = KV_PATTERN.matcher(message);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = cleanToken(matcher.group(2));
            tokens.putIfAbsent(key, value);
        }
        return tokens;
    }

    private String cleanToken(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        while (trimmed.startsWith("\"") || trimmed.startsWith("'") || trimmed.startsWith("[") || trimmed.startsWith("{")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith(",") || trimmed.endsWith(";") || trimmed.endsWith(")")
                || trimmed.endsWith("\"") || trimmed.endsWith("'") || trimmed.endsWith("]")
                || trimmed.endsWith("}")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private String findFirstStringInTokens(Map<String, String> tokens, Set<String> candidates) {
        for (String candidate : candidates) {
            String value = tokens.get(candidate);
            if (StringUtils.hasText(value)) return value;
        }
        return null;
    }

    private Integer parseInteger(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizeSectionValue(String value) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("apply")) return "apply";
        if (normalized.contains("plan"))  return "plan";
        if (normalized.contains("starting apply operation")) return "apply";
        if (normalized.contains("starting plan operation"))  return "plan";
        return null;
    }

    private String extractModuleFromMessage(String message) {
        if (!StringUtils.hasText(message)) return null;
        Matcher matcher = MODULE_PATTERN.matcher(message);
        return matcher.find() ? matcher.group() : null;
    }

    private String normalizeHttpMethod(String method) {
        if (!StringUtils.hasText(method)) return null;
        String candidate = method.trim();
        int spaceIdx = candidate.indexOf(' ');
        if (spaceIdx > 0) candidate = candidate.substring(0, spaceIdx);
        candidate = candidate.replaceAll("[^A-Za-z]", "");
        if (!StringUtils.hasText(candidate)) return null;
        return candidate.toUpperCase(Locale.ROOT);
    }

    // --- Builder ------------------------------------------------------------------------------

    private record ParsedLogRecordBuilder(
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
            List<ParsedLogRecord.ParsedPayload> bodies,
            String rawJson) {

        private ParsedLogRecord build() {
            return new ParsedLogRecord(timestamp, timestampGuessed, level, levelGuessed, section,
                    module, message, reqId, transactionId, rpc, resourceType, dataSourceType,
                    httpOperationType, statusCode, attributes, bodies, rawJson);
        }

        private static ParsedLogRecordBuilder empty(OffsetDateTime ts, String level) {
            return new ParsedLogRecordBuilder(ts, false, level, false, "unknown", null, null,
                    null, null, null, null, null, null, null, Map.of(), List.of(), "{}");
        }

        private ParsedLogRecordBuilder timestampGuessed(boolean value) {
            return new ParsedLogRecordBuilder(timestamp, value, level, levelGuessed, section,
                    module, message, reqId, transactionId, rpc, resourceType, dataSourceType,
                    httpOperationType, statusCode, attributes, bodies, rawJson);
        }

        private ParsedLogRecordBuilder levelGuessed(boolean value) {
            return new ParsedLogRecordBuilder(timestamp, timestampGuessed, level, value, section,
                    module, message, reqId, transactionId, rpc, resourceType, dataSourceType,
                    httpOperationType, statusCode, attributes, bodies, rawJson);
        }

        private ParsedLogRecordBuilder section(String value) {
            return new ParsedLogRecordBuilder(timestamp, timestampGuessed, level, levelGuessed,
                    value, module, message, reqId, transactionId, rpc, resourceType,
                    dataSourceType, httpOperationType, statusCode, attributes, bodies, rawJson);
        }

        private ParsedLogRecordBuilder module(String value) {
            return new ParsedLogRecordBuilder(timestamp, timestampGuessed, level, levelGuessed,
                    section, value, message, reqId, transactionId, rpc, resourceType,
                    dataSourceType, httpOperationType, statusCode, attributes, bodies, rawJson);
        }

        private ParsedLogRecordBuilder message(String value) {
            return new ParsedLogRecordBuilder(timestamp, timestampGuessed, level, levelGuessed,
                    section, module, value, reqId, transactionId, rpc, resourceType,
                    dataSourceType, httpOperationType, statusCode, attributes, bodies, rawJson);
        }

        private ParsedLogRecordBuilder reqId(String value) {
            return new ParsedLogRecordBuilder(timestamp, timestampGuessed, level, levelGuessed,
                    section, module, message, value, transactionId, rpc, resourceType,
                    dataSourceType, httpOperationType, statusCode, attributes, bodies, rawJson);
        }

        private ParsedLogRecordBuilder transactionId(String value) {
            return new ParsedLogRecordBuilder(timestamp, timestampGuessed, level, levelGuessed,
                    section, module, message, reqId, value, rpc, resourceType, dataSourceType,
                    httpOperationType, statusCode, attributes, bodies, rawJson);
        }

        private ParsedLogRecordBuilder rpc(String value) {
            return new ParsedLogRecordBuilder(timestamp, timestampGuessed, level, levelGuessed,
                    section, module, message, reqId, transactionId, value, resourceType,
                    dataSourceType, httpOperationType, statusCode, attributes, bodies, rawJson);
        }

        private ParsedLogRecordBuilder resourceType(String value) {
            return new ParsedLogRecordBuilder(timestamp, timestampGuessed, level, levelGuessed,
                    section, module, message, reqId, transactionId, rpc, value,
                    dataSourceType, httpOperationType, statusCode, attributes, bodies, rawJson);
        }

        private ParsedLogRecordBuilder dataSourceType(String value) {
            return new ParsedLogRecordBuilder(timestamp, timestampGuessed, level, levelGuessed,
                    section, module, message, reqId, transactionId, rpc, resourceType, value,
                    httpOperationType, statusCode, attributes, bodies, rawJson);
        }

        private ParsedLogRecordBuilder httpOperationType(String value) {
            return new ParsedLogRecordBuilder(timestamp, timestampGuessed, level, levelGuessed,
                    section, module, message, reqId, transactionId, rpc, resourceType,
                    dataSourceType, value, statusCode, attributes, bodies, rawJson);
        }

        private ParsedLogRecordBuilder statusCode(Integer value) {
            return new ParsedLogRecordBuilder(timestamp, timestampGuessed, level, levelGuessed,
                    section, module, message, reqId, transactionId, rpc, resourceType,
                    dataSourceType, httpOperationType, value, attributes, bodies, rawJson);
        }

        private ParsedLogRecordBuilder attributes(Map<String, Object> value) {
            return new ParsedLogRecordBuilder(timestamp, timestampGuessed, level, levelGuessed,
                    section, module, message, reqId, transactionId, rpc, resourceType,
                    dataSourceType, httpOperationType, statusCode, value, bodies, rawJson);
        }

        private ParsedLogRecordBuilder bodies(List<ParsedLogRecord.ParsedPayload> value) {
            return new ParsedLogRecordBuilder(timestamp, timestampGuessed, level, levelGuessed,
                    section, module, message, reqId, transactionId, rpc, resourceType,
                    dataSourceType, httpOperationType, statusCode, attributes, value, rawJson);
        }

        private ParsedLogRecordBuilder rawJson(String value) {
            return new ParsedLogRecordBuilder(timestamp, timestampGuessed, level, levelGuessed,
                    section, module, message, reqId, transactionId, rpc, resourceType,
                    dataSourceType, httpOperationType, statusCode, attributes, bodies, value);
        }
    }
}
