package ru.practicum.request.controller;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.request.dto.ParticipationRequestDto;
import ru.practicum.request.service.RequestService;

import java.util.List;

/**
 * Внутренний контроллер для межсервисного взаимодействия.
 * Используется event-service для получения данных о заявках.
 */
@Validated
@RestController
@RequestMapping("/internal/requests")
@RequiredArgsConstructor
public class RequestInternalController {

    private final RequestService requestService;

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