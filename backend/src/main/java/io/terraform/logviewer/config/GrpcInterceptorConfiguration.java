package io.terraform.logviewer.config;

import io.grpc.ServerInterceptor;
import io.terraform.logviewer.grpc.interceptor.QueryFilterInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcInterceptorConfiguration {

    @GrpcGlobalServerInterceptor
    public ServerInterceptor queryFilterInterceptor(QueryFilterProperties properties) {
        return new QueryFilterInterceptor(properties);
    }
}
