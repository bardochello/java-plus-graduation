package ru.practicum.config;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.cloud.netflix.eureka.EurekaInstanceConfigBean;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

@Slf4j
@Component
@RequiredArgsConstructor
public class EurekaPortFixer implements ApplicationListener<WebServerInitializedEvent> {

    private final EurekaInstanceConfigBean eurekaInstanceConfig;
    private final ApplicationInfoManager applicationInfoManager;

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        int realPort = event.getWebServer().getPort();

        if (realPort <= 0) {
            log.warn("Invalid port {}, skipping Eureka fix", realPort);
            return;
        }

        String ip = resolveLocalIp();
        String appName = eurekaInstanceConfig.getAppname().toLowerCase();
        log.info("Fixing Eureka: app={}, ip={}, port={}", appName, ip, realPort);

        eurekaInstanceConfig.setNonSecurePort(realPort);
        eurekaInstanceConfig.setPreferIpAddress(true);
        eurekaInstanceConfig.setIpAddress(ip);
        eurekaInstanceConfig.setHostname(ip);
        eurekaInstanceConfig.setInstanceId(appName + ":" + realPort);

        applicationInfoManager.getInfo().setIsDirty();

        applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.STARTING);
        applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.UP);

        log.info("Eureka registration fixed: {}:{}", ip, realPort);
    }

    private String resolveLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.warn("Could not resolve local IP: {}", e.getMessage());
            return "127.0.0.1";
        }
    }
}