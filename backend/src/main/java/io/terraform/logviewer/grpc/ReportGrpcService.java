package io.terraform.logviewer.grpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.terraform.logviewer.entity.LogEntryEntity;
import io.terraform.logviewer.grpc.LogRecord;
import io.terraform.logviewer.grpc.QueryRequest;
import io.terraform.logviewer.grpc.ReportChunk;
import io.terraform.logviewer.grpc.ReportExportRequest;
import io.terraform.logviewer.grpc.ReportFormat;
import io.terraform.logviewer.grpc.ReportServiceGrpc;
import io.terraform.logviewer.service.LogQueryService;
import io.terraform.logviewer.service.dto.LogGroupResult;
import io.terraform.logviewer.service.dto.QueryParameters;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ReportGrpcService extends ReportServiceGrpc.ReportServiceImplBase {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String CSV_HEADER = String.join(
            ",",
            "timestamp",
            "level",
            "section",
            "module",
            "message",
            "req_id",
            "resource_type",
            "status_code",
            "import_id"
    );
    private static final float PDF_MARGIN = 40f;
    private static final float PDF_LINE_HEIGHT = 14f;

    private final LogQueryService queryService;
    private final GrpcMapper mapper;
    private final QueryRequestMapper requestMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void export(ReportExportRequest request, StreamObserver<ReportChunk> responseObserver) {
        QueryRequest base = request.hasQuery() ? request.getQuery() : QueryRequest.getDefaultInstance();
        QueryParameters parameters = requestMapper.toParameters(base);
        ReportFormat format = request.getFormat();
        if (format == ReportFormat.REPORT_FORMAT_UNSPECIFIED) {
            format = ReportFormat.REPORT_FORMAT_CSV;
        }

        boolean grouped = parameters.groupByReqId();
        String fileName = buildFileName(format, grouped);
        responseObserver.onNext(ReportChunk.newBuilder().setFileName(fileName).build());

        try {
            switch (format) {
                case REPORT_FORMAT_JSON -> streamJson(parameters, grouped, responseObserver);
                case REPORT_FORMAT_PDF -> streamPdf(parameters, grouped, responseObserver);
                case REPORT_FORMAT_CSV -> streamCsv(parameters, grouped, responseObserver);
                case REPORT_FORMAT_UNSPECIFIED -> {
                    // unreachable due to guard above
                }
            }
            responseObserver.onNext(ReportChunk.newBuilder().setEof(true).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Failed to export report", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to export report")
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    private void streamCsv(QueryParameters parameters,
                           boolean grouped,
                           StreamObserver<ReportChunk> observer) {
        sendTextChunk(observer, CSV_HEADER + "\n");
        if (grouped) {
            List<LogGroupResult> groups = queryService.exportGroups(parameters);
            for (LogGroupResult group : groups) {
                String header = "# group: " + (group.reqId() == null || group.reqId().isBlank()
                        ? "Без req_id"
                        : group.reqId());
                sendTextChunk(observer, header + "\n");
                for (LogEntryEntity entity : group.entries()) {
                    sendTextChunk(observer, recordToCsv(mapper.toLogRecord(entity)) + "\n");
                }
            }
            return;
        }
        try (Stream<LogEntryEntity> stream = queryService.export(parameters)) {
            stream.forEach(entity -> sendTextChunk(observer, recordToCsv(mapper.toLogRecord(entity)) + "\n"));
        }
    }

    private void streamJson(QueryParameters parameters,
                            boolean grouped,
                            StreamObserver<ReportChunk> observer) throws IOException {
        if (grouped) {
            List<LogGroupResult> groups = queryService.exportGroups(parameters);
            sendTextChunk(observer, "[");
            boolean first = true;
            for (LogGroupResult group : groups) {
                if (!first) {
                    sendTextChunk(observer, ",");
                }
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("req_id", group.reqId());
                payload.put("group_first_ts", formatTs(group.firstTimestamp()));
                payload.put("group_last_ts", formatTs(group.lastTimestamp()));
                List<Map<String, Object>> items = group.entries().stream()
                        .map(entity -> recordToMap(mapper.toLogRecord(entity)))
                        .collect(Collectors.toList());
                payload.put("items", items);
                sendTextChunk(observer, objectMapper.writeValueAsString(payload));
                first = false;
            }
            sendTextChunk(observer, "]");
            return;
        }

        sendTextChunk(observer, "[");
        AtomicBoolean first = new AtomicBoolean(true);
        try (Stream<LogEntryEntity> stream = queryService.export(parameters)) {
            stream.forEach(entity -> {
                try {
                    if (!first.getAndSet(false)) {
                        sendTextChunk(observer, ",");
                    }
                    sendTextChunk(observer, objectMapper.writeValueAsString(recordToMap(mapper.toLogRecord(entity))));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        sendTextChunk(observer, "]");
    }

    private void streamPdf(QueryParameters parameters,
                           boolean grouped,
                           StreamObserver<ReportChunk> observer) throws IOException {
        byte[] pdfBytes;
        if (grouped) {
            List<LogGroupResult> groups = queryService.exportGroups(parameters);
            pdfBytes = renderPdfForGroups(groups);
        } else {
            List<String> lines = new ArrayList<>();
            lines.add("Terraform Log Report");
            lines.add("Generated: " + ISO.format(OffsetDateTime.now(ZoneOffset.UTC)));
            lines.add("");
            try (Stream<LogEntryEntity> stream = queryService.export(parameters)) {
                stream.map(mapper::toLogRecord).forEach(record -> lines.add(renderRecordLine(record)));
            }
            pdfBytes = renderLines(lines);
        }
        observer.onNext(ReportChunk.newBuilder()
                .setPayload(ByteString.copyFrom(pdfBytes))
                .build());
    }

    private byte[] renderPdfForGroups(List<LogGroupResult> groups) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("Terraform Log Report (grouped)");
        lines.add("Generated: " + ISO.format(OffsetDateTime.now(ZoneOffset.UTC)));
        lines.add("");
        for (LogGroupResult group : groups) {
            lines.add("Group: " + (group.reqId() == null ? "Без req_id" : group.reqId()));
            lines.add("  Range: " + formatTs(group.firstTimestamp()) + " → " + formatTs(group.lastTimestamp()));
            lines.add("  Items: " + group.entries().size());
            for (LogEntryEntity entity : group.entries()) {
                lines.add("    " + renderRecordLine(mapper.toLogRecord(entity)));
            }
            lines.add("");
        }
        return renderLines(lines);
    }

    private byte[] renderLines(List<String> lines) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDPageContentStream content = new PDPageContentStream(document, page);
            content.setFont(PDType1Font.HELVETICA, 10);
            content.beginText();
            float y = page.getMediaBox().getHeight() - PDF_MARGIN;
            content.newLineAtOffset(PDF_MARGIN, y);
            content.setLeading(PDF_LINE_HEIGHT);

            for (String raw : lines) {
                String line = sanitizeForPdf(raw);
                if (line.isEmpty()) {
                    content.newLine();
                    y -= PDF_LINE_HEIGHT;
                    continue;
                }
                if (y <= PDF_MARGIN) {
                    content.endText();
                    content.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    content = new PDPageContentStream(document, page);
                    content.setFont(PDType1Font.HELVETICA, 10);
                    content.beginText();
                    y = page.getMediaBox().getHeight() - PDF_MARGIN;
                    content.newLineAtOffset(PDF_MARGIN, y);
                    content.setLeading(PDF_LINE_HEIGHT);
                }
                content.showText(line);
                content.newLine();
                y -= PDF_LINE_HEIGHT;
            }

            content.endText();
            content.close();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private String renderRecordLine(LogRecord record) {
        String ts = record.getTs();
        String level = record.getLevel();
        String module = record.getModule();
        String message = record.getMessage();
        return String.format(Locale.ROOT, "[%s] %s %s - %s",
                ts,
                level == null || level.isBlank() ? "?" : level,
                module == null || module.isBlank() ? "—" : module,
                message == null ? "" : message);
    }

    private Map<String, Object> recordToMap(LogRecord record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", Long.toString(record.getId()));
        map.put("ts", record.getTs());
        map.put("level", emptyToNull(record.getLevel()));
        map.put("section", emptyToNull(record.getSection()));
        map.put("module", emptyToNull(record.getModule()));
        map.put("message", emptyToNull(record.getMessage()));
        map.put("req_id", emptyToNull(record.getReqId()));
        map.put("trans_id", emptyToNull(record.getTransId()));
        map.put("rpc", emptyToNull(record.getRpc()));
        map.put("resource_type", emptyToNull(record.getResourceType()));
        map.put("data_source_type", emptyToNull(record.getDataSourceType()));
        map.put("http_op_type", emptyToNull(record.getHttpOpType()));
        map.put("status_code", record.getStatusCode() == 0 ? null : record.getStatusCode());
        map.put("file_name", emptyToNull(record.getFileName()));
        map.put("import_id", emptyToNull(record.getImportId()));
        map.put("unread", record.getUnread());
        return map;
    }

    private String recordToCsv(LogRecord record) {
        return String.join(
                ",",
                escapeCsv(record.getTs()),
                escapeCsv(record.getLevel()),
                escapeCsv(record.getSection()),
                escapeCsv(record.getModule()),
                escapeCsv(record.getMessage()),
                escapeCsv(record.getReqId()),
                escapeCsv(record.getResourceType()),
                record.getStatusCode() == 0 ? "" : Integer.toString(record.getStatusCode()),
                escapeCsv(record.getImportId())
        );
    }

    private void sendTextChunk(StreamObserver<ReportChunk> observer, String data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        observer.onNext(ReportChunk.newBuilder()
                .setPayload(ByteString.copyFromUtf8(data))
                .build());
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replace("\n", " ").replace("\r", " ");
        if (sanitized.contains(",") || sanitized.contains("\"") || sanitized.contains(";")) {
            return "\"" + sanitized.replace("\"", "\"\"") + "\"";
        }
        return sanitized;
    }

    private String sanitizeForPdf(String value) {
        if (value == null) {
            return "";
        }
        String withoutBreaks = value.replaceAll("[\r\n]+", " ");
        StringBuilder builder = new StringBuilder();
        for (char ch : withoutBreaks.toCharArray()) {
            if (ch < 32 || ch > 126) {
                builder.append('?');
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String formatTs(OffsetDateTime timestamp) {
        return timestamp == null ? "—" : ISO.format(timestamp);
    }

    private String buildFileName(ReportFormat format, boolean grouped) {
        String suffix = switch (format) {
            case REPORT_FORMAT_CSV -> "csv";
            case REPORT_FORMAT_JSON -> "json";
            case REPORT_FORMAT_PDF -> "pdf";
            case REPORT_FORMAT_UNSPECIFIED -> "dat";
            default -> "dat";
        };
        String groupedFlag = grouped ? "_groupedByReqId" : "";
        String timestamp = FILE_TIMESTAMP.format(OffsetDateTime.now(ZoneOffset.UTC));
        return "report_" + timestamp + groupedFlag + '.' + suffix;
    }
}
