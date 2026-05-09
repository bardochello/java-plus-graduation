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

/**
 * Внутренний контроллер — только для межсервисного общения с event-service.
 * Предоставляет API, который вызывает RequestServiceClient (Feign).
 */
@Validated
@RestController
@RequestMapping("/internal/requests")
@RequiredArgsConstructor
public class RequestInternalController {

    private final RequestService requestService;

    @GetMapping("/events/{eventId}/count")
    public Long countConfirmedRequests(@PathVariable @Positive Long eventId) {
        return requestService.countConfirmedRequests(eventId);
    }

    @GetMapping("/confirmed")
    public List<ParticipationRequestDto> getConfirmedRequestsByEventIds(@RequestParam List<Long> eventIds) {
        return requestService.getRequestsByEventIdIn(eventIds);
    }

    /**
     * Получение всех заявок на участие в событии (используется event-service).
     */
    @GetMapping("/events/{eventId}")
    public List<ParticipationRequestDto> getRequestsByEventId(@PathVariable @Positive Long eventId,
                                                              @RequestParam @Positive Long userId) {
        return requestService.getRequestsByEventId(userId, eventId);
    }

    /**
     * Обновление статуса заявок (используется event-service при публикации/обновлении события).
     * Дополнительные query-параметры participantLimit/requestModeration передаются Feign-клиентом,
     * но не используются здесь (сервис сам запрашивает EventDto через Feign).
     */
    @PatchMapping("/events/{eventId}")
    public EventRequestStatusUpdateResult updateRequestStatus(
            @PathVariable @Positive Long eventId,
            @RequestParam @Positive Long userId,
            @RequestParam(required = false) Integer participantLimit,
            @RequestParam(required = false) Boolean requestModeration,
            @RequestBody EventRequestStatusUpdateRequest updateRequest) {
        return requestService.updateRequestStatus(userId, eventId, updateRequest);
    }
}