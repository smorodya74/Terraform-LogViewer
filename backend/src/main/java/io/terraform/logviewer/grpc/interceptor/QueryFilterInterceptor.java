package io.terraform.logviewer.grpc.interceptor;

import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.terraform.logviewer.config.QueryFilterProperties;
import io.terraform.logviewer.grpc.ExportRequest;
import io.terraform.logviewer.grpc.GroupQueryRequest;
import io.terraform.logviewer.grpc.LogQueryGrpc;
import io.terraform.logviewer.grpc.QueryRequest;
import io.terraform.logviewer.grpc.ReportServiceGrpc;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Slf4j
@RequiredArgsConstructor
public class QueryFilterInterceptor implements ServerInterceptor {

    private final QueryFilterProperties properties;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                Metadata headers,
                                                                ServerCallHandler<ReqT, RespT> next) {
        if (!properties.isEnabled()) {
            return next.startCall(call, headers);
        }

        String methodName = call.getMethodDescriptor().getFullMethodName();
        boolean supported = LogQueryGrpc.getSearchMethod().getFullMethodName().equals(methodName)
                || LogQueryGrpc.getExportMethod().getFullMethodName().equals(methodName)
                || LogQueryGrpc.getSearchGroupsMethod().getFullMethodName().equals(methodName)
                || ReportServiceGrpc.getExportMethod().getFullMethodName().equals(methodName);

        if (!supported) {
            return next.startCall(call, headers);
        }

        var delegate = next.startCall(call, headers);
        return new SimpleForwardingServerCallListener<>(delegate) {
            @Override
            public void onMessage(ReqT message) {
                ReqT processed = sanitizeMessage(methodName, message);
                super.onMessage(processed);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private <ReqT> ReqT sanitizeMessage(String methodName, ReqT message) {
        if (message instanceof QueryRequest queryRequest) {
            return (ReqT) sanitizeQuery(methodName, queryRequest);
        }
        if (message instanceof ExportRequest exportRequest) {
            return (ReqT) sanitizeExport(methodName, exportRequest);
        }
        if (message instanceof GroupQueryRequest groupQueryRequest) {
            return (ReqT) sanitizeGroupQuery(methodName, groupQueryRequest);
        }
        return message;
    }

    private GroupQueryRequest sanitizeGroupQuery(String methodName, GroupQueryRequest request) {
        if (!request.hasQuery()) {
            return request;
        }
        QueryRequest sanitized = sanitizeQuery(methodName, request.getQuery());
        if (sanitized.equals(request.getQuery())) {
            return request;
        }
        return request.toBuilder().setQuery(sanitized).build();
    }

    private ExportRequest sanitizeExport(String methodName, ExportRequest request) {
        if (!request.hasQuery()) {
            return request;
        }
        QueryRequest sanitized = sanitizeQuery(methodName, request.getQuery());
        if (sanitized.equals(request.getQuery())) {
            return request;
        }
        return request.toBuilder().setQuery(sanitized).build();
    }

    private QueryRequest sanitizeQuery(String methodName, QueryRequest request) {
        Map<String, String> original = request.getFiltersMap();
        Map<String, String> sanitized = new LinkedHashMap<>(original);

        Set<String> blocked = properties.getBlockedFields().stream()
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(HashSet::new));

        boolean changed = false;
        if (!blocked.isEmpty()) {
            for (String key : new HashSet<>(sanitized.keySet())) {
                if (blocked.contains(key.toLowerCase(Locale.ROOT))) {
                    sanitized.remove(key);
                    changed = true;
                    if (log.isDebugEnabled()) {
                        log.debug("QueryFilterInterceptor removed blocked filter '{}'", key);
                    }
                }
            }
        }

        if (!CollectionUtils.isEmpty(properties.getForcedFilters())) {
            for (Map.Entry<String, String> entry : properties.getForcedFilters().entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (!StringUtils.hasText(key) || !StringUtils.hasText(value)) {
                    continue;
                }
                String previous = sanitized.put(key, value);
                if (!Objects.equals(previous, value)) {
                    changed = true;
                }
            }
        }

        if (!changed) {
            return request;
        }

        QueryRequest.Builder builder = request.toBuilder();
        builder.clearFilters();
        builder.putAllFilters(sanitized);

        log.info("QueryFilterInterceptor applied filters for {}: forced={}, resultKeys={}",
                methodName,
                properties.getForcedFilters(),
                sanitized.keySet());

        return builder.build();
    }
}
