package ru.practicum.request.controller;

import jakarta.validation.Valid;
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
 * Внутренний контроллер для межсервисного взаимодействия.
 * Используется main-service для получения данных о заявках.
 */
@Validated
@RestController
@RequestMapping("/internal/requests")
@RequiredArgsConstructor
public class RequestInternalController {

    private final RequestService requestService;

    /**
     * Получает заявки по событию (для владельца события).
     */
    @GetMapping("/events/{eventId}")
    public List<ParticipationRequestDto> getRequestsByEventId(@PathVariable @Positive Long eventId,
                                                              @RequestParam @Positive Long userId) {
        return requestService.getRequestsByEventId(userId, eventId);
    }

    /**
     * Обновляет статусы заявок (для владельца события).
     */
    @PatchMapping("/events/{eventId}")
    public EventRequestStatusUpdateResult updateRequestStatus(@PathVariable @Positive Long eventId,
                                                              @RequestParam @Positive Long userId,
                                                              @RequestParam(defaultValue = "0") Integer participantLimit,
                                                              @RequestParam(defaultValue = "true") Boolean requestModeration,
                                                              @RequestBody @Valid EventRequestStatusUpdateRequest updateRequest) {
        return requestService.updateRequestStatus(userId, eventId, updateRequest, participantLimit, requestModeration);
    }

    /**
     * Возвращает количество подтверждённых заявок для события.
     */
    @GetMapping("/events/{eventId}/count")
    public Long countConfirmedRequests(@PathVariable @Positive Long eventId) {
        return requestService.countConfirmedRequests(eventId);
    }

    /**
     * Возвращает подтверждённые заявки для списка событий (batch).
     */
    @GetMapping("/confirmed")
    public List<ParticipationRequestDto> getConfirmedRequestsByEventIds(@RequestParam List<Long> eventIds) {
        return requestService.getRequestsByEventIdIn(eventIds);
    }
}