package ru.practicum.request.feign;

import org.springframework.stereotype.Component;
import ru.practicum.request.dto.EventDto;

/**
 * Fallback для MainServiceClient при недоступности main-service.
 */
@Component
public class MainServiceClientFallback implements MainServiceClient {

    @Override
    public EventDto getEventById(Long eventId) {
        return null;
    }
}