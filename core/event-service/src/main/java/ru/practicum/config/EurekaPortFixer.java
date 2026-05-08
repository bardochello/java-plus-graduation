package ru.practicum.config;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.cloud.netflix.eureka.EurekaInstanceConfigBean;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EurekaPortFixer implements ApplicationListener<WebServerInitializedEvent> {

    private final EurekaInstanceConfigBean eurekaInstanceConfig;
    private final ApplicationInfoManager applicationInfoManager;

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        int realPort = event.getWebServer().getPort();
        log.info("Updating Eureka registration: port {} -> {}",
                eurekaInstanceConfig.getNonSecurePort(), realPort);

        eurekaInstanceConfig.setNonSecurePort(realPort);

        applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.UP);

        log.info("Eureka port updated to {}", realPort);
    }
}