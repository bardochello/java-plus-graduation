package ru.practicum.request.feign;

import ru.practicum.event.dto.EventInternalDto;

public class EventServiceClientFallback implements EventServiceClient {

    @Override
    public EventInternalDto getEventById(Long eventId) {
        return null;
    }
}