package io.terraform.logviewer.plugin;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.terraform.logviewer.config.PluginProperties;
import io.terraform.logviewer.entity.LogEntryEntity;
import io.terraform.logviewer.grpc.LogPluginGrpc;
import io.terraform.logviewer.grpc.PluginEvent;
import io.terraform.logviewer.grpc.PluginResult;
import io.terraform.logviewer.parser.ParsedLogRecord;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogPluginGateway {

    private final PluginProperties properties;
    private final List<ClientHolder> clients = new ArrayList<>();

    public LogPluginGateway init() {
        if (properties.isEnabled() && !CollectionUtils.isEmpty(properties.getPlugins())) {
            for (PluginProperties.PluginConfig plugin : properties.getPlugins()) {
                clients.add(new ClientHolder(plugin));
            }
        }
        return this;
    }

    public Map<String, String> process(ParsedLogRecord record, LogEntryEntity entity) {
        if (!properties.isEnabled() || clients.isEmpty()) {
            return Map.of();
        }
        Map<String, String> aggregated = new ConcurrentHashMap<>();
        for (ClientHolder holder : clients) {
            try {
                Map<String, String> result = holder.invoke(record, entity, properties.getDeadline());
                aggregated.putAll(result);
            } catch (Exception e) {
                log.warn("Plugin {} invocation failed: {}", holder.plugin.getId(), e.getMessage());
            }
        }
        return aggregated;
    }

    @PreDestroy
    public void shutdown() {
        for (ClientHolder holder : clients) {
            holder.shutdown();
        }
        clients.clear();
    }

    private static final class ClientHolder {
        private final PluginProperties.PluginConfig plugin;
        private final ManagedChannel channel;
        private final LogPluginGrpc.LogPluginStub stub;

        ClientHolder(PluginProperties.PluginConfig plugin) {
            this.plugin = plugin;
            ManagedChannelBuilder<?> builder = ManagedChannelBuilder
                    .forAddress(plugin.getHost(), plugin.getPort());
            if (plugin.isPlaintext()) {
                builder.usePlaintext();
            }
            this.channel = builder.build();
            this.stub = LogPluginGrpc.newStub(channel);
        }

        Map<String, String> invoke(ParsedLogRecord record, LogEntryEntity entity, Duration deadline)
                throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            Map<String, String> annotations = new ConcurrentHashMap<>();

            StreamObserver<PluginResult> responseObserver = new StreamObserver<>() {
                @Override
                public void onNext(PluginResult value) {
                    if (value != null && !value.getAnnotationsMap().isEmpty()) {
                        annotations.putAll(value.getAnnotationsMap());
                    }
                }

                @Override
                public void onError(Throwable t) {
                    latch.countDown();
                }

                @Override
                public void onCompleted() {
                    latch.countDown();
                }
            };

            StreamObserver<PluginEvent> requestObserver;
            try {
                requestObserver = stub.withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS)
                        .process(responseObserver);
            } catch (StatusRuntimeException e) {
                log.warn("Plugin {} connection failed: {}", plugin.getId(), e.getMessage());
                return Collections.emptyMap();
            }

            requestObserver.onNext(toEvent(record, entity));
            requestObserver.onCompleted();

            if (!latch.await(deadline.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("Plugin {} timed out", plugin.getId());
                return Collections.emptyMap();
            }
            return annotations;
        }

        private PluginEvent toEvent(ParsedLogRecord record, LogEntryEntity entity) {
            PluginEvent.Builder builder = PluginEvent.newBuilder()
                    .setId(entity.getId() != null ? entity.getId().toString() : "")
                    .setTs(Objects.toString(entity.getTimestamp(), ""))
                    .setLevel(Objects.toString(entity.getLevel(), ""))
                    .setSection(Objects.toString(entity.getSection(), ""))
                    .setMessage(Objects.toString(entity.getMessage(), ""));
            record.attributes().forEach((k, v) -> builder.putAttrs(k, Objects.toString(v, "")));
            return builder.build();
        }

        void shutdown() {
            try {
                channel.shutdown();
                if (!channel.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                channel.shutdownNow();
            }
        }
    }
}
