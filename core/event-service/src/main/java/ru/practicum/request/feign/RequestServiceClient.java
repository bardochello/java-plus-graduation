package ru.practicum.request.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import ru.practicum.request.dto.EventRequestStatusUpdateRequest;
import ru.practicum.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.request.dto.ParticipationRequestDto;

import java.util.List;

/**
 * Feign-клиент для взаимодействия с request-service.
 */
@FeignClient(name = "request-service", fallback = RequestServiceClientFallback.class)
public interface RequestServiceClient {

    @GetMapping("/users/{userId}/requests")
    List<ParticipationRequestDto> getRequestsByUserId(@PathVariable("userId") Long userId);

    @PostMapping("/users/{userId}/requests")
    ParticipationRequestDto createRequest(@PathVariable("userId") Long userId,
                                          @RequestParam("eventId") Long eventId);

    @PatchMapping("/users/{userId}/requests/{requestId}/cancel")
    ParticipationRequestDto cancelRequest(@PathVariable("userId") Long userId,
                                          @PathVariable("requestId") Long requestId);

    @GetMapping("/internal/requests/events/{eventId}")
    List<ParticipationRequestDto> getRequestsByEventId(@PathVariable("eventId") Long eventId,
                                                       @RequestParam("userId") Long userId);

    @PatchMapping("/internal/requests/events/{eventId}")
    EventRequestStatusUpdateResult updateRequestStatus(@PathVariable("eventId") Long eventId,
                                                       @RequestParam("userId") Long userId,
                                                       @RequestBody EventRequestStatusUpdateRequest updateRequest);

    @GetMapping("/internal/requests/events/{eventId}/count")
    Long countConfirmedRequests(@PathVariable("eventId") Long eventId);

    @GetMapping("/internal/requests/confirmed")
    List<ParticipationRequestDto> getConfirmedRequestsByEventIds(@RequestParam("eventIds") List<Long> eventIds);
}