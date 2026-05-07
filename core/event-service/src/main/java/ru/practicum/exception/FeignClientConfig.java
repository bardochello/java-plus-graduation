package ru.practicum.exception;

import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

/**
 * Конфигурация для Feign-клиентов.
 */
public class FeignClientConfig {

    @Bean
    public ErrorDecoder feignErrorDecoder() {
        return new FeignErrorDecoder();
    }
}
