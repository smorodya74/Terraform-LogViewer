package io.terraform.logviewer.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.terraform.logviewer.entity.LogBodyEntity;
import io.terraform.logviewer.entity.LogEntryEntity;
import io.terraform.logviewer.service.LogQueryService;
import io.terraform.logviewer.service.dto.GroupQueryResult;
import io.terraform.logviewer.service.dto.QueryParameters;
import io.terraform.logviewer.service.dto.TimelinePoint;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;  // ВАЖНО: используем spring-транзакции
import org.springframework.util.StringUtils;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class LogQueryGrpcService extends LogQueryGrpc.LogQueryImplBase {

    private final LogQueryService queryService;
    private final GrpcMapper mapper;
    private final QueryRequestMapper requestMapper;



    @Override
    @Transactional(readOnly = true)
    public void search(QueryRequest request, StreamObserver<QueryResponse> responseObserver) {
        QueryParameters parameters = requestMapper.toParameters(request);
        Page<LogEntryEntity> page = queryService.search(parameters);

        QueryResponse.Builder builder = QueryResponse.newBuilder()
                .setTotal(page.getTotalElements())
                .setPage(page.getNumber())
                .setSize(page.getSize());

        // Материализация внутри активной транзакции
        page.getContent().stream()
                .map(mapper::toLogRecord)
                .forEach(builder::addItems);

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    @Transactional(readOnly = true)
    public void searchGroups(GroupQueryRequest request, StreamObserver<GroupQueryResponse> responseObserver) {
        QueryRequest base = request.hasQuery() ? request.getQuery() : QueryRequest.getDefaultInstance();
        QueryParameters parameters = requestMapper.toParameters(base);
        GroupQueryResult result = queryService.searchGroups(parameters);

        GroupQueryResponse.Builder builder = GroupQueryResponse.newBuilder()
                .setTotalGroups(result.totalGroups())
                .setPage(parameters.page())
                .setSize(parameters.size());

        result.groups().stream()
                .map(mapper::toLogGroupItems)
                .forEach(builder::addGroups);

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    @Transactional(readOnly = true) // ВАЖНО: потребление Stream строго внутри транзакции
    public void export(ExportRequest request, StreamObserver<LogRecord> responseObserver) {
        QueryParameters parameters = requestMapper.toParameters(request.getQuery());
        try (Stream<LogEntryEntity> stream = queryService.export(parameters)) {
            stream.map(mapper::toLogRecord).forEach(responseObserver::onNext);
        }
        responseObserver.onCompleted();
    }

    @Override
    @Transactional
    public void markRead(MarkReadRequest request, StreamObserver<MarkReadResponse> responseObserver) {
        List<Long> ids = new ArrayList<>(request.getIdsList());
        int updated = queryService.markRead(ids, request.getReqId(), request.getMarkRead());
        responseObserver.onNext(MarkReadResponse.newBuilder()
                .setUpdated(updated)
                .build());
        responseObserver.onCompleted();
    }


    @Override
    @Transactional(readOnly = true)
    public void timeline(TimelineRequest request, StreamObserver<TimelineItem> responseObserver) {
        Optional<String> reqId = Optional.ofNullable(request.getReqId()).filter(StringUtils::hasText);
        Optional<OffsetDateTime> from = parseTimestamp(request.getTsFrom());
        Optional<OffsetDateTime> to = parseTimestamp(request.getTsTo());
        Optional<String> importId = Optional.ofNullable(request.getImportId()).filter(StringUtils::hasText);

        List<TimelinePoint> points = queryService.timeline(reqId, from, to, importId);
        points.stream()
                .map(mapper::toTimelineItem)
                .forEach(responseObserver::onNext);

        responseObserver.onCompleted();
    }

    @Override
    @Transactional(readOnly = true)
    public void getLog(GetLogRequest request, StreamObserver<LogDetails> responseObserver) {
        // Вся загрузка entry + дочерних body выполняется внутри транзакции,
        // чтобы не было ленивых обращений к уже закрытому ResultSet/сессии.
        queryService.findById(request.getId()).ifPresentOrElse(entry -> {
            List<LogBodyEntity> bodies = queryService.bodies(entry); // здесь могут быть LAZY отношения
            responseObserver.onNext(mapper.toLogDetails(entry, bodies));
            responseObserver.onCompleted();
        }, () -> responseObserver.onError(
                Status.NOT_FOUND.withDescription("Log entry not found").asRuntimeException()
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public void bodies(BodiesRequest request, StreamObserver<BodyItem> responseObserver) {
        Optional<LogEntryEntity> entryOpt = queryService.findById(request.getLogId());
        if (entryOpt.isEmpty()) {
            responseObserver.onError(
                    Status.NOT_FOUND.withDescription("Log entry not found").asRuntimeException()
            );
            return;
        }
        // Материализация и маппинг внутри транзакции
        queryService.bodies(entryOpt.get()).stream()
                .map(mapper::toBodyItem)
                .forEach(responseObserver::onNext);
        responseObserver.onCompleted();
    }

    private Optional<OffsetDateTime> parseTimestamp(String value) {
        if (!StringUtils.hasText(value)) return Optional.empty();
        try {
            return Optional.of(OffsetDateTime.parse(value));
        } catch (DateTimeParseException e) {
            log.debug("Unable to parse timestamp {}", value, e);
            return Optional.empty();
        }
    }
}
