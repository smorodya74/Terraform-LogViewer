package io.terraform.logviewer.grpc;

import io.terraform.logviewer.grpc.BodyItem;
import io.terraform.logviewer.grpc.LogDetails;
import io.terraform.logviewer.grpc.LogGroupItems;
import io.terraform.logviewer.grpc.LogRecord;
import io.terraform.logviewer.grpc.TimelineItem;
import io.terraform.logviewer.entity.LogBodyEntity;
import io.terraform.logviewer.entity.LogEntryEntity;
import io.terraform.logviewer.service.dto.LogGroupResult;
import io.terraform.logviewer.service.dto.TimelinePoint;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GrpcMapper {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public LogRecord toLogRecord(LogEntryEntity entity) {
        LogRecord.Builder builder = LogRecord.newBuilder()
                .setId(entity.getId())
                .setTs(ISO.format(entity.getTimestamp()))
                .setSection(Optional.ofNullable(entity.getSection()).orElse("unknown"))
                .setModule(Optional.ofNullable(entity.getModule()).orElse(""))
                .setMessage(Optional.ofNullable(entity.getMessage()).orElse(""))
                .setReqId(Optional.ofNullable(entity.getReqId()).orElse(""))
                .setTransId(Optional.ofNullable(entity.getTransactionId()).orElse(""))
                .setRpc(Optional.ofNullable(entity.getRpc()).orElse(""))
                .setResourceType(Optional.ofNullable(entity.getResourceType()).orElse(""))
                .setDataSourceType(Optional.ofNullable(entity.getDataSourceType()).orElse(""))
                .setHttpOpType(Optional.ofNullable(entity.getHttpOperationType()).orElse(""))
                .setStatusCode(Optional.ofNullable(entity.getStatusCode()).orElse(0))
                .setFileName(Optional.ofNullable(entity.getFileName()).orElse(""))
                .setImportId(Optional.ofNullable(entity.getImportId()).orElse(""))
                .setUnread(entity.isUnread());

        if (StringUtils.hasText(entity.getLevel())) {
            builder.setLevel(entity.getLevel().toUpperCase());
        }
        return builder.build();
    }

    public LogDetails toLogDetails(LogEntryEntity entry, List<LogBodyEntity> bodies) {
        return LogDetails.newBuilder()
                .setRecord(toLogRecord(entry))
                .setRawJson(Optional.ofNullable(entry.getRawJson()).orElse(""))
                .setAttrsJson(Optional.ofNullable(entry.getAttrsJson()).orElse(""))
                .setAnnotationsJson(Optional.ofNullable(entry.getAnnotationsJson()).orElse(""))
                .setBodiesCount(bodies.size())
                .build();
    }

    public BodyItem toBodyItem(LogBodyEntity body) {
        return BodyItem.newBuilder()
                .setId(body.getId())
                .setKind(Optional.ofNullable(body.getKind()).orElse(""))
                .setBodyJson(Optional.ofNullable(body.getBodyJson()).orElse(""))
                .build();
    }

    public TimelineItem toTimelineItem(TimelinePoint point) {
        TimelineItem.Builder builder = TimelineItem.newBuilder()
                .setReqId(Optional.ofNullable(point.reqId()).orElse(""))
                .setCount((int) point.count())
                .setImportId(Optional.ofNullable(point.importId()).orElse(""));
        if (point.start() != null) {
            builder.setStartTs(ISO.format(point.start()));
        }
        if (point.end() != null) {
            builder.setEndTs(ISO.format(point.end()));
        }
        return builder.build();
    }

    public LogGroupItems toLogGroupItems(LogGroupResult result) {
        LogGroupItems.Builder builder = LogGroupItems.newBuilder()
                .setReqId(Optional.ofNullable(result.reqId()).orElse(""));
        if (result.firstTimestamp() != null) {
            builder.setGroupFirstTs(ISO.format(result.firstTimestamp()));
        }
        if (result.lastTimestamp() != null) {
            builder.setGroupLastTs(ISO.format(result.lastTimestamp()));
        }
        result.entries().stream()
                .map(this::toLogRecord)
                .forEach(builder::addItems);
        return builder.build();
    }
}
