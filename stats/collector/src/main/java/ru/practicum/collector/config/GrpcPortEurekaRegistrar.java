package ru.practicum.collector.config;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
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

        // Записываем порт в metadata инстанса
        applicationInfoManager.getInfo()
                .getMetadata()
                .put("grpcPort", String.valueOf(grpcPort));

        // Помечаем инстанс как UP — это триггерит отправку обновлённой
        // регистрации (с metadata) на Eureka-сервер
        applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.UP);

        log.info("Registered grpcPort={} in Eureka metadata", grpcPort);
    }
}