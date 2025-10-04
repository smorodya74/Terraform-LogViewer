package io.terraform.logviewer.http;

import io.terraform.logviewer.service.LogImportService;
import io.terraform.logviewer.service.LogQueryService;
import io.terraform.logviewer.service.dto.ImportSummary;
import io.terraform.logviewer.service.LogImportService.ImportSession;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "logviewer.ingest.http-mode", havingValue = "rest", matchIfMissing = true)
public class LogImportController {

    private final LogImportService importService;
    private final LogQueryService queryService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResponse upload(@RequestPart("file") MultipartFile file) throws Exception {
        String fileName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename()
                : "upload.json";
        ImportSession session = importService.startSession(fileName);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                file.getInputStream(),
                StandardCharsets.UTF_8
        ))) {
            reader.lines()
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .forEach(line -> importService.ingestLine(session, line));
        }
        return ImportResponse.fromSession(session);
    }

    @GetMapping
    public List<ImportSummaryResponse> listImports() {
        return queryService.listImports().stream()
                .map(ImportSummaryResponse::fromSummary)
                .toList();
    }

    public record ImportResponse(String importId, String fileName, long total, long saved, long failed) {
        static ImportResponse fromSession(ImportSession session) {
            return new ImportResponse(
                    session.getImportId(),
                    session.getFileName(),
                    session.getTotal(),
                    session.getSaved(),
                    session.getFailed()
            );
        }
    }

    public record ImportSummaryResponse(
            String importId,
            String fileName,
            long total,
            String firstTimestamp,
            String lastTimestamp
    ) {
        static ImportSummaryResponse fromSummary(ImportSummary summary) {
            return new ImportSummaryResponse(
                    summary.importId(),
                    summary.fileName(),
                    summary.total(),
                    summary.firstTimestamp() != null ? summary.firstTimestamp().toString() : null,
                    summary.lastTimestamp() != null ? summary.lastTimestamp().toString() : null
            );
        }
    }
}
