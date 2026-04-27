package ru.practicum;

import dto.EndpointHitDto;
import dto.ViewStatsDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Клиент статистики через Eureka + RestTemplate.
 * Сервис обнаруживается через DiscoveryClient (Eureka).
 */
@Component
public class StatsClient {

    private final DiscoveryClient discoveryClient;
    private final RestTemplate restTemplate;

    @Value("${stats.server.id:stats-server}")
    private String statsServiceId;

    public StatsClient(DiscoveryClient discoveryClient, RestTemplate restTemplate) {
        this.discoveryClient = discoveryClient;
        this.restTemplate = restTemplate;
    }

    /**
     * Получаем экземпляр stats-server из Eureka с повторными попытками.
     * Метод публичный, так как @Retryable работает только с публичными методами через прокси.
     */
    @Retryable(maxAttempts = 5, backoff = @Backoff(delay = 2000, multiplier = 1.5))
    public ServiceInstance getInstance() {
        List<ServiceInstance> instances = discoveryClient.getInstances(statsServiceId);
        if (instances == null || instances.isEmpty()) {
            throw new RuntimeException(
                    "Экземпляр stats-server не найден в Eureka: " + statsServiceId
            );
        }
        return instances.get(0);
    }

    private URI makeUri(String path) {
        ServiceInstance instance = getInstance();
        return URI.create("http://" + instance.getHost() + ":" + instance.getPort() + path);
    }

    /**
     * Отправка информации о просмотре (hit).
     *
     * @param endpointHitDto данные о просмотре
     */
    public void addHit(EndpointHitDto endpointHitDto) {
        restTemplate.postForEntity(makeUri("/hit"), endpointHitDto, Void.class);
    }

    /**
     * Получение статистики просмотров.
     *
     * @param start  начало периода (формат yyyy-MM-dd HH:mm:ss)
     * @param end    конец периода (формат yyyy-MM-dd HH:mm:ss)
     * @param uris   список URI для фильтрации (может быть null)
     * @param unique учитывать только уникальные IP
     * @return список статистики просмотров
     */
    public List<ViewStatsDto> getStats(String start, String end, List<String> uris, Boolean unique) {
        StringBuilder url = new StringBuilder("/stats?start=")
                .append(start)
                .append("&end=")
                .append(end);

        if (uris != null && !uris.isEmpty()) {
            uris.forEach(uri -> url.append("&uris=").append(uri));
        }
        if (unique != null) {
            url.append("&unique=").append(unique);
        }

        ResponseEntity<ViewStatsDto[]> response = restTemplate.exchange(
                makeUri(url.toString()),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                ViewStatsDto[].class
        );

        ViewStatsDto[] body = response.getBody();
        return body != null ? Arrays.asList(body) : Collections.emptyList();
    }
}