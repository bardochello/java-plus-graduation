package ru.practicum.request.controller;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.request.dto.EventRequestStatusUpdateRequest;
import ru.practicum.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.request.dto.ParticipationRequestDto;
import ru.practicum.request.service.RequestService;

import java.util.List;

@RestController
@RequestMapping("/internal/requests")
@RequiredArgsConstructor
@Validated
public class RequestInternalController {

    private final RequestService requestService;

    /**
     * Изменение статуса заявок на участие (вызывается из event-service)
     */
    @PatchMapping("/events/{eventId}")
    public EventRequestStatusUpdateResult updateRequestStatus(
            @PathVariable @Positive Long eventId,
            @RequestParam @Positive Long userId,
            @RequestParam(defaultValue = "0") Integer participantLimit,
            @RequestParam(defaultValue = "true") Boolean requestModeration,
            @RequestBody @Validated EventRequestStatusUpdateRequest updateRequest) {

        return requestService.updateRequestStatus(userId, eventId, updateRequest, participantLimit, requestModeration);
    }

    /**
     * Получение всех заявок по id события (вызывается из event-service)
     */
    @GetMapping("/events/{eventId}/requests")
    public List<ParticipationRequestDto> getRequestsByEventId(@PathVariable @Positive Long eventId) {
        return requestService.getRequestsByEventId(eventId);
    }

    /**
     * Получение количества подтверждённых заявок по id события
     */
    @GetMapping("/events/{eventId}/confirmed-count")
    public Long getConfirmedRequestsCount(@PathVariable @Positive Long eventId) {
        return requestService.countConfirmedRequests(eventId);
    }
}