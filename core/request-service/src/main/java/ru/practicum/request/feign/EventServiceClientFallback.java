package ru.practicum.request.feign;

import org.springframework.stereotype.Component;
import ru.practicum.request.dto.EventDto;

/**
 * Fallback для EventServiceClient при недоступности event-service.
 */
@Component
public class EventServiceClientFallback implements EventServiceClient {

    @Override
    public EventDto getEventById(Long eventId) {
        return null;
    }
}