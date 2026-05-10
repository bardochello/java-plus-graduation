package ru.practicum.request.feign;

import ru.practicum.request.dto.EventDto;

/**
 * Fallback для EventServiceClient.
 */
public class EventServiceClientFallback implements EventServiceClient {

    @Override
    public EventDto getEventById(Long eventId) {
        return null;
    }
}