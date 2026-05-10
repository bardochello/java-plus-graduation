package ru.practicum.request.controller;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.request.dto.ParticipationRequestDto;
import ru.practicum.request.service.RequestService;

import java.util.List;
import java.util.Map;

/**
 * Внутренний контроллер — только для межсервисного чтения данных event-service.
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

    @GetMapping("/confirmed-counts")
    public Map<Long, Long> countConfirmedByEventIds(@RequestParam List<Long> eventIds) {
        return requestService.countConfirmedByEventIds(eventIds);
    }
}