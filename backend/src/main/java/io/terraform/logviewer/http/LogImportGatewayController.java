package io.terraform.logviewer.http;

import com.google.protobuf.ByteString;
import io.terraform.logviewer.grpc.ImportFile;
import io.terraform.logviewer.grpc.ImportResult;
import io.terraform.logviewer.grpc.LogIngestGrpc;
import io.terraform.logviewer.service.LogQueryService;
import io.terraform.logviewer.service.dto.ImportSummary;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/imports")
@ConditionalOnProperty(name = "logviewer.ingest.http-mode", havingValue = "gateway")
@RequiredArgsConstructor
public class LogImportGatewayController {

    @GrpcClient("logviewer")
    private LogIngestGrpc.LogIngestBlockingStub ingestStub;

    private final LogQueryService queryService;

    @PostMapping(value = "/upload", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ImportResponse upload(@RequestBody GatewayRequest request) {
        String fileName = StringUtils.hasText(request.fileName()) ? request.fileName() : "upload.json";
        byte[] payload = request.decode();

        ImportResult result = ingestStub.ingestFile(ImportFile.newBuilder()
                .setFileName(fileName)
                .setContent(ByteString.copyFrom(payload))
                .build());

        return new ImportResponse(
                result.getImportId(),
                result.getFileName(),
                result.getTotal(),
                result.getSaved(),
                result.getFailed()
        );
    }

    @GetMapping
    public List<ImportSummaryResponse> listImports() {
        return queryService.listImports().stream()
                .map(ImportSummaryResponse::fromSummary)
                .toList();
    }

    public record GatewayRequest(String fileName, String contentBase64) {
        byte[] decode() {
            if (!StringUtils.hasText(contentBase64)) {
                return new byte[0];
            }
            return Base64.getDecoder().decode(contentBase64);
        }
    }

    public record ImportResponse(String importId, String fileName, long total, long saved, long failed) {
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
