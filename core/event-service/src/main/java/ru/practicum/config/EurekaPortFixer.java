package ru.practicum.config;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.cloud.netflix.eureka.EurekaInstanceConfigBean;
import org.springframework.cloud.netflix.eureka.serviceregistry.EurekaRegistration;
import org.springframework.cloud.netflix.eureka.serviceregistry.EurekaServiceRegistry;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Fixes Eureka registration port when server.port=0 (random port).
 * When Tomcat starts on a random port, Eureka may register with default port 80.
 * This listener detects the real port and forces re-registration with the correct port.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EurekaPortFixer implements ApplicationListener<WebServerInitializedEvent> {

    private final EurekaInstanceConfigBean eurekaInstanceConfig;
    private final ApplicationInfoManager applicationInfoManager;
    private final EurekaServiceRegistry eurekaServiceRegistry;
    private final EurekaRegistration eurekaRegistration;

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        int realPort = event.getWebServer().getPort();
        int registeredPort = eurekaInstanceConfig.getNonSecurePort();

        log.info("WebServer started on port {}. Eureka has port {}.", realPort, registeredPort);

        if (realPort <= 0) {
            log.warn("Invalid real port: {}, skipping Eureka port fix", realPort);
            return;
        }

        // Update port in Eureka instance config
        eurekaInstanceConfig.setNonSecurePort(realPort);

        // Update instance-id to include real port
        String appName = eurekaInstanceConfig.getAppname().toLowerCase();
        eurekaInstanceConfig.setInstanceId(appName + ":" + realPort);

        // Deregister old instance (wrong port) and re-register with correct port
        try {
            eurekaServiceRegistry.deregister(eurekaRegistration);
            log.info("Deregistered old Eureka instance");
            Thread.sleep(300);
        } catch (Exception e) {
            log.debug("Deregister skipped: {}", e.getMessage());
        }

        try {
            eurekaServiceRegistry.register(eurekaRegistration);
            applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.UP);
            log.info("Re-registered with Eureka on port {}", realPort);
        } catch (Exception e) {
            log.error("Failed to re-register with Eureka: {}", e.getMessage());
            // Fallback: just update status which triggers heartbeat with new port
            applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.UP);
        }
    }
}