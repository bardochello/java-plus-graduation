package ru.practicum.request.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.request.dto.EventRequestStatusUpdateRequest;
import ru.practicum.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.debug.AgentNdjsonLog;
import ru.practicum.request.dto.ParticipationRequestDto;
import ru.practicum.request.service.RequestService;

import java.util.List;
import java.util.Locale;

/**
 * Контроллер для операций с заявками на участие в событиях.
 */
@Validated
@RestController
@RequiredArgsConstructor
public class RequestController {

    private final RequestService requestService;

    @GetMapping("/users/{userId}/requests")
    public List<ParticipationRequestDto> getRequests(@PathVariable @Positive Long userId) {
        return requestService.getRequestsByUserId(userId);
    }

    @PostMapping("/users/{userId}/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ParticipationRequestDto createRequest(
            @PathVariable @Positive Long userId,
            @RequestParam(required = false) Long eventId) {
        // #region agent log
        AgentNdjsonLog.log("H1", "RequestController.java:createRequest", "enter", "pre-fix",
                String.format(Locale.US, "{\"userId\":%d,\"eventIdNull\":%b}",
                        userId, eventId == null));
        // #endregion
        if (eventId == null) {
            throw new IllegalArgumentException("Required parameter 'eventId' is missing");
        }
        if (eventId <= 0) {
            throw new IllegalArgumentException("eventId must be positive");
        }
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

    /**
     * Обновление статуса заявок на участие в событии.
     * КРИТИЧНО: updateRequest должен содержать requestIds и status!
     */
    @PatchMapping("/users/{userId}/events/{eventId}/requests")
    public EventRequestStatusUpdateResult updateRequestStatus(
            @PathVariable @Positive Long userId,
            @PathVariable @Positive Long eventId,
            @RequestBody @NotNull(message = "Request body is required") @Valid EventRequestStatusUpdateRequest updateRequest) {
        return requestService.updateRequestStatus(userId, eventId, updateRequest);
    }
}
