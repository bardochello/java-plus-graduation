package ru.practicum.event.controller.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.practicum.event.dto.EventFullDto;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.Event;
import ru.practicum.event.service.EventService;

/**
 * Внутренний контроллер для request-service.
 * Предоставляет событие по id другим микросервисам (Feign-клиент).
 */
@RestController
@RequestMapping("/internal/events")
@RequiredArgsConstructor
public class EventInternalController {

    private final EventService eventService;

    @GetMapping("/{eventId}")
    @Transactional(readOnly = true)
    public EventFullDto getEventById(@PathVariable Long eventId) {
        Event event = eventService.getEventById(eventId);
        return EventMapper.mapToEventFullDto(event);
    }
}