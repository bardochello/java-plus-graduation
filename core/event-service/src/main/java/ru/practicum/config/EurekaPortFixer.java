package ru.practicum.config;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.cloud.netflix.eureka.EurekaInstanceConfigBean;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EurekaPortFixer implements ApplicationListener<WebServerInitializedEvent> {

    private final EurekaInstanceConfigBean eurekaInstanceConfig;
    private final ApplicationInfoManager applicationInfoManager;

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        try {
            int realPort = event.getWebServer().getPort();

            if (realPort <= 0) {
                log.warn("Invalid port {}, skipping Eureka fix", realPort);
                return;
            }

            String ip = resolveLocalIp();
            String appName = eurekaInstanceConfig.getAppname().toLowerCase();

            log.info("EurekaPortFixer: app={}, ip={}, port={}", appName, ip, realPort);

            eurekaInstanceConfig.setNonSecurePort(realPort);
            eurekaInstanceConfig.setPreferIpAddress(true);
            eurekaInstanceConfig.setIpAddress(ip);
            eurekaInstanceConfig.setHostname(ip);
            eurekaInstanceConfig.setInstanceId(appName + ":" + realPort);

            applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.UP);

            log.info("EurekaPortFixer done: {}:{}", ip, realPort);
        } catch (Exception e) {
            log.error("EurekaPortFixer failed (non-critical): {}", e.getMessage(), e);
        }
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