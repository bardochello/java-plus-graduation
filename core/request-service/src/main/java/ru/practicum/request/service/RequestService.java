package ru.practicum.request.service;

import ru.practicum.request.dto.EventRequestStatusUpdateRequest;
import ru.practicum.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.request.dto.ParticipationRequestDto;

import java.util.List;

/**
 * Сервис для работы с заявками на участие в событиях.
 */
public interface RequestService {

    List<ParticipationRequestDto> getRequestsByUserId(Long userId);

    /** Алиас для getRequestsByUserId — для совместимости. */
    default List<ParticipationRequestDto> getUserRequests(Long userId) {
        return getRequestsByUserId(userId);
    }

    ParticipationRequestDto createRequest(Long userId, Long eventId);

    ParticipationRequestDto cancelRequest(Long userId, Long requestId);

    List<ParticipationRequestDto> getRequestsByEventId(Long userId, Long eventId);

    EventRequestStatusUpdateResult updateRequestStatus(Long userId, Long eventId,
                                                       EventRequestStatusUpdateRequest updateRequest,
                                                       Integer participantLimit, Boolean requestModeration);

    Long countConfirmedRequests(Long eventId);

    List<ParticipationRequestDto> getRequestsByEventIdIn(List<Long> eventIds);
}