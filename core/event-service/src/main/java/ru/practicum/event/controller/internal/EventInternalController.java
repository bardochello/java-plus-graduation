package ru.practicum.event.controller.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.event.dto.EventInternalDto;
import ru.practicum.event.model.Event;
import ru.practicum.event.service.EventService;

/**
 * Внутренний контроллер для request-service.
 */
@Validated
@RestController
@RequestMapping("/internal/events")
@RequiredArgsConstructor
public class EventInternalController {

    private final EventService eventService;

    @GetMapping("/internal/events/{eventId}")
    @Transactional(readOnly = true)
    public EventInternalDto getEventById(@PathVariable Long eventId) {
        Event event = eventService.getEventById(eventId);
        return EventInternalDto.builder()
                .id(event.getId())
                .initiatorId(event.getInitiator().getId())
                .state(event.getState().name())
                .participantLimit(event.getParticipantLimit())
                .requestModeration(event.getRequestModeration())
                .build();
    }
}