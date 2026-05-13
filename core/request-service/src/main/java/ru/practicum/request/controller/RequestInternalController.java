package ru.practicum.request.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.practicum.request.dto.ParticipationRequestDto;
import ru.practicum.request.service.RequestService;

import java.util.List;
import java.util.Map;

/**
 * Внутренний контроллер для межсервисного взаимодействия.
 */
@RestController
@RequestMapping("/internal/requests")
@RequiredArgsConstructor
public class RequestInternalController {

    private final RequestService requestService;

    @GetMapping("/count/{eventId}")
    public Long countConfirmedRequests(@PathVariable Long eventId) {
        return requestService.countConfirmedRequests(eventId);
    }

    @PostMapping("/count-by-events")
    public Map<Long, Long> countConfirmedByEventIds(@RequestBody List<Long> eventIds) {
        return requestService.countConfirmedByEventIds(eventIds);
    }

    /**
     * Список подтверждённых заявок на мероприятие.
     * Нужен event-service для проверки: посещал ли пользователь событие (перед лайком).
     */
    @GetMapping("/confirmed/{eventId}")
    public List<ParticipationRequestDto> getConfirmedRequestsByEventId(@PathVariable Long eventId) {
        return requestService.getConfirmedRequestsByEventId(eventId);
    }
}
