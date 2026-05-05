package ru.practicum.request.feign;

import org.springframework.stereotype.Component;
import ru.practicum.request.dto.EventRequestStatusUpdateRequest;
import ru.practicum.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.request.dto.ParticipationRequestDto;

import java.util.Collections;
import java.util.List;

/**
 * Fallback для RequestServiceClient при недоступности request-service.
 * Возвращает безопасные дефолтные значения.
 */
@Component
public class RequestServiceClientFallback implements RequestServiceClient {

    @Override
    public List<ParticipationRequestDto> getRequestsByUserId(Long userId) {
        return Collections.emptyList();
    }

    @Override
    public ParticipationRequestDto createRequest(Long userId, Long eventId) {
        return null;
    }

    @Override
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        return null;
    }

    @Override
    public List<ParticipationRequestDto> getRequestsByEventId(Long eventId, Long userId) {
        return Collections.emptyList();
    }

    @Override
    public EventRequestStatusUpdateResult updateRequestStatus(Long eventId, Long userId,
                                                              EventRequestStatusUpdateRequest updateRequest) {
        return EventRequestStatusUpdateResult.builder()
                .confirmedRequests(Collections.emptyList())
                .rejectedRequests(Collections.emptyList())
                .build();
    }

    @Override
    public Long countConfirmedRequests(Long eventId) {
        return 0L;
    }

    @Override
    public List<ParticipationRequestDto> getConfirmedRequestsByEventIds(List<Long> eventIds) {
        return Collections.emptyList();
    }
}