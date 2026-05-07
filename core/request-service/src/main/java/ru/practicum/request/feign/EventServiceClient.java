package ru.practicum.request.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.practicum.request.dto.EventDto;

/**
 * Feign-клиент для получения данных о событиях из main-service.
 */
@FeignClient(name = "event-service", fallback = EventServiceClientFallback.class)
public interface EventServiceClient {

    @GetMapping("/internal/events/{eventId}")
    EventDto getEventById(@PathVariable("eventId") Long eventId);
}