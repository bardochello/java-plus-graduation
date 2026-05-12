package ru.practicum.event.controller.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.event.EventInternalDto;
import ru.practicum.event.model.Event;
import ru.practicum.event.service.EventService;

@RestController
@RequestMapping("/internal/events")
@RequiredArgsConstructor
public class EventInternalController {

    private final EventService eventService;

    @GetMapping("/{eventId}")
    @Transactional(readOnly = true)
    public EventInternalDto getEventById(@PathVariable Long eventId) {
        Event event = eventService.getEventById(eventId);

        return EventInternalDto.builder()
                .id(event.getId())
                .initiatorId(event.getInitiator() != null ? event.getInitiator().getId() : null)
                .state(event.getState() != null ? event.getState().name() : null)
                .participantLimit(event.getParticipantLimit())
                .requestModeration(event.getRequestModeration())
                .build();
    }
}