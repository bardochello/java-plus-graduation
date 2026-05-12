package ru.practicum.request.feign;


import ru.practicum.dto.event.EventInternalDto;

public class EventServiceClientFallback implements EventServiceClient {

    @Override
    public EventInternalDto getEventById(Long eventId) {
        return null;
    }
}