package ru.practicum.request.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.request.dto.EventRequestStatusUpdateRequest;
import ru.practicum.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.request.dto.ParticipationRequestDto;
import ru.practicum.request.service.RequestService;

import java.util.List;

/**
 * Контроллер для операций с заявками на участие в событиях.
 * Обслуживает как /users/{userId}/requests так и /users/{userId}/events/{eventId}/requests
 */
@Validated
@RestController
@RequiredArgsConstructor
public class RequestController {

    private final RequestService requestService;

    // ---- /users/{userId}/requests ----

    @GetMapping("/users/{userId}/requests")
    public List<ParticipationRequestDto> getRequests(@PathVariable @Positive Long userId) {
        return requestService.getRequestsByUserId(userId);
    }

    @PostMapping("/users/{userId}/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ParticipationRequestDto createRequest(@PathVariable @Positive Long userId,
                                                 @RequestParam @Positive Long eventId) {
        return requestService.createRequest(userId, eventId);
    }

    @PatchMapping("/users/{userId}/requests/{requestId}/cancel")
    public ParticipationRequestDto cancelRequest(@PathVariable @Positive Long userId,
                                                 @PathVariable @Positive Long requestId) {
        return requestService.cancelRequest(userId, requestId);
    }

    @GetMapping("/users/{userId}/events/{eventId}/requests")
    public List<ParticipationRequestDto> getEventRequests(@PathVariable @Positive Long userId,
                                                          @PathVariable @Positive Long eventId) {
        return requestService.getRequestsByEventId(userId, eventId);
    }

    @PatchMapping("/users/{userId}/events/{eventId}/requests")
    public EventRequestStatusUpdateResult updateRequestStatus(
            @PathVariable @Positive Long userId,
            @PathVariable @Positive Long eventId,
            @RequestBody(required = false) EventRequestStatusUpdateRequest updateRequest) {
        return requestService.updateRequestStatus(userId, eventId,
                updateRequest != null ? updateRequest : new EventRequestStatusUpdateRequest());
    }
}