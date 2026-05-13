package ru.practicum;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация модуля stats-client.
 *
 * Регистрирует CollectorClient и AnalyzerClient как Spring-бины.
 * Подключается к сервисам через Eureka (discovery:///collector, discovery:///analyzer).
 *
 * Потребители модуля (event-service, request-service) должны добавить
 * в свой application.yaml / config-server yaml:
 *
 * grpc:
 *   client:
 *     collector:
 *       address: 'discovery:///collector'
 *       enableKeepAlive: true
 *       keepAliveWithoutCalls: true
 *       negotiationType: plaintext
 *     analyzer:
 *       address: 'discovery:///analyzer'
 *       enableKeepAlive: true
 *       keepAliveWithoutCalls: true
 *       negotiationType: plaintext
 */
@Configuration
@ComponentScan(basePackages = "ru.practicum")
public class StatsClientConfig {
}