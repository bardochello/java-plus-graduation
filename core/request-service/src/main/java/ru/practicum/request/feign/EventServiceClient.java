package ru.practicum.request.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.practicum.dto.event.EventInternalDto;

@FeignClient(name = "event-service")
public interface EventServiceClient {

    @GetMapping("/internal/events/{eventId}")
    EventInternalDto getEventById(@PathVariable("eventId") Long eventId);
}