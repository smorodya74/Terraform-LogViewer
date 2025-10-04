package io.terraform.logviewer.grpc;

import io.grpc.stub.StreamObserver;
import io.terraform.logviewer.service.LogImportService;
import io.terraform.logviewer.service.LogImportService.ImportSession;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.util.StringUtils;

/**
 * gRPC сервис для импорта логов (LogIngest).
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class LogIngestGrpcService extends LogIngestGrpc.LogIngestImplBase {

    private final LogImportService importService;

    @Override
    public StreamObserver<ImportEnvelope> ingest(StreamObserver<ImportResult> responseObserver) {
        return new StreamObserver<>() {
            private ImportSession session;

            @Override
            public void onNext(ImportEnvelope value) {
                if (value.hasMeta()) {
                    session = importService.startSession(value.getMeta().getFileName());
                    return;
                }
                if (value.hasLine()) {
                    ensureSession();
                    String raw = value.getLine().getRawJson();
                    if (StringUtils.hasText(raw)) {
                        importService.ingestLine(session, raw);
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                log.warn("Import stream error: {}", t.getMessage());
            }

            @Override
            public void onCompleted() {
                ensureSession();
                responseObserver.onNext(toResult(session));
                responseObserver.onCompleted();
            }

            private void ensureSession() {
                if (session == null) {
                    session = importService.startSession("grpc");
                }
            }
        };
    }

    @Override
    public void ingestFile(ImportFile request, StreamObserver<ImportResult> responseObserver) {
        String fileName = StringUtils.hasText(request.getFileName()) ? request.getFileName() : "upload.json";
        ImportSession session = importService.startSession(fileName);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(request.getContent().toByteArray()),
                StandardCharsets.UTF_8
        ))) {
            reader.lines()
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .forEach(line -> importService.ingestLine(session, line));
        } catch (Exception e) {
            log.warn("Failed to ingest file {}: {}", fileName, e.getMessage());
        }

        responseObserver.onNext(toResult(session));
        responseObserver.onCompleted();
    }

    private ImportResult toResult(ImportSession session) {
        Objects.requireNonNull(session, "Import session must not be null");
        return ImportResult.newBuilder()
                .setImportId(session.getImportId())
                .setFileName(session.getFileName())
                .setTotal(session.getTotal())
                .setSaved(session.getSaved())
                .setFailed(session.getFailed())
                .build();
    }
}
