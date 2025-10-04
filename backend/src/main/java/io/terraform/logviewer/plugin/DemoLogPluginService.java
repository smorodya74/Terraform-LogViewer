package io.terraform.logviewer.plugin;

import io.grpc.stub.StreamObserver;
import io.terraform.logviewer.grpc.LogPluginGrpc;
import io.terraform.logviewer.grpc.PluginEvent;
import io.terraform.logviewer.grpc.PluginResult;
import java.util.Locale;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class DemoLogPluginService extends LogPluginGrpc.LogPluginImplBase {

    @Override
    public StreamObserver<PluginEvent> process(
            StreamObserver<PluginResult> responseObserver) {
        return new StreamObserver<>() {
            @Override
            public void onNext(PluginEvent value) {
                PluginResult.Builder result = PluginResult.newBuilder();
                String level = value.getLevel().toUpperCase(Locale.ROOT);
                if ("ERROR".equals(level) || "WARN".equals(level)) {
                    result.putAnnotations("severity", level);
                }
                if (value.getAttrsMap().containsKey("status_code")) {
                    result.putAnnotations("status", value.getAttrsMap().get("status_code"));
                }
                responseObserver.onNext(result.build());
            }

            @Override
            public void onError(Throwable t) {
                responseObserver.onCompleted();
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }
}
