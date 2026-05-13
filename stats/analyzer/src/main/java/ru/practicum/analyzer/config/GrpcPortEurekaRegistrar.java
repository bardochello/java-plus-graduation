package ru.practicum.analyzer.config;

import com.netflix.appinfo.ApplicationInfoManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.event.GrpcServerStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class GrpcPortEurekaRegistrar {

    private final ApplicationInfoManager applicationInfoManager;

    @EventListener
    public void onGrpcServerStarted(GrpcServerStartedEvent event) {
        int grpcPort = event.getServer().getPort();
        log.info("gRPC server started on port {}, registering in Eureka metadata", grpcPort);

        applicationInfoManager.getInfo()
                .getMetadata()
                .put("grpcPort", String.valueOf(grpcPort));

        log.info("Registered grpcPort={} in Eureka metadata", grpcPort);
    }
}