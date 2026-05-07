package ru.practicum.event.controller.private_;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.event.dto.*;
import ru.practicum.event.service.EventService;
import ru.practicum.request.dto.EventRequestStatusUpdateRequest;
import ru.practicum.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.request.dto.ParticipationRequestDto;

import java.util.List;

/**
 * Контроллер для приватных операций с событиями пользователей.
 */
@Validated
@RequestMapping("/users/{userId}/events")
@RestController
@RequiredArgsConstructor
public class EventPrivateController {
    private final EventService eventService;

    @GetMapping("/{eventId}/requests")
    public List<ParticipationRequestDto> getRequests(@PathVariable @Positive long userId,
                                                     @PathVariable @Positive long eventId) {
        return eventService.getRequests(userId, eventId);
    }

    @GetMapping("/{eventId}")
    public EventFullDto get(@PathVariable @Positive long userId,
                            @PathVariable @Positive long eventId) {
        return eventService.get(userId, eventId);
    }

    @GetMapping
    public List<EventShortDto> getAll(@PathVariable @Positive long userId,
                                      @RequestParam(defaultValue = "0") @PositiveOrZero int from,
                                      @RequestParam(defaultValue = "10") @Positive int size) {
        return eventService.getAll(userId, from, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    EventFullDto create(@PathVariable @Positive long userId,
                        @RequestBody @Valid NewEventDto eventDto) {
        return eventService.create(userId, eventDto);
    }

    @PatchMapping("/{eventId}")
    public EventFullDto update(@PathVariable @Positive long userId,
                               @PathVariable @Positive long eventId,
                               @RequestBody @Valid UpdateEventUserRequest updateEvent) {
        return eventService.update(userId, eventId, updateEvent);
    }

    @PatchMapping("/{eventId}/requests")
    public EventRequestStatusUpdateResult updateRequestStatus(
            @PathVariable @Positive long userId,
            @PathVariable @Positive long eventId,
            @RequestBody @Valid EventRequestStatusUpdateRequest eventRequestStatus) {
        return eventService.updateRequestStatus(userId, eventId, eventRequestStatus);
    }
}