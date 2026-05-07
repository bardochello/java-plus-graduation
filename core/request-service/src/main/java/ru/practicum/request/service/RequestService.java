package ru.practicum.request.service;

import ru.practicum.request.dto.EventRequestStatusUpdateRequest;
import ru.practicum.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.request.dto.ParticipationRequestDto;

import java.util.List;

public interface RequestService {

    ParticipationRequestDto createRequest(Long userId, Long eventId);

    ParticipationRequestDto cancelRequest(Long userId, Long requestId);

    EventRequestStatusUpdateResult updateRequestStatus(Long userId, Long eventId,
                                                       EventRequestStatusUpdateRequest updateRequest,
                                                       Integer participantLimit, Boolean requestModeration);

    List<ParticipationRequestDto> getUserRequests(Long userId);

    List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId);

    /**
     * Получение всех заявок по id события (используется event-service)
     */
    List<ParticipationRequestDto> getRequestsByEventId(Long eventId);

    Long countConfirmedRequests(Long eventId);
}