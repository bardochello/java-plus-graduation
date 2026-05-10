package ru.practicum.request.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.event.EventInternalDto;
import ru.practicum.request.dto.*;
import ru.practicum.request.exception.ConflictResource;
import ru.practicum.request.exception.NotFoundResource;
import ru.practicum.request.feign.EventServiceClient;
import ru.practicum.request.mapper.RequestMapper;
import ru.practicum.request.model.Request;
import ru.practicum.request.repository.RequestRepository;
import ru.practicum.request.utill.Status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequestServiceImpl implements RequestService {

    private final RequestRepository requestRepository;
    private final EventServiceClient eventServiceClient;

    @Override
    @Transactional
    public List<ParticipationRequestDto> getRequestsByUserId(Long userId) {
        return requestRepository.findByRequesterId(userId).stream()
                .map(RequestMapper::mapToDto)
                .toList();
    }

    @Override
    @Transactional
    public ParticipationRequestDto createRequest(Long userId, Long eventId) {
        EventInternalDto event = getEventOrThrow(eventId);

        if (event.getInitiatorId().equals(userId)) {
            throw new ConflictResource("Инициатор события не может добавить запрос на участие в своём событии");
        }
        if (!"PUBLISHED".equals(event.getState())) {
            throw new ConflictResource("Нельзя участвовать в неопубликованном событии");
        }
        if (requestRepository.existsByRequesterIdAndEventId(userId, eventId)) {
            throw new ConflictResource("Заявка уже существует");
        }

        Long confirmed = requestRepository.countByEventIdAndStatus(eventId, Status.CONFIRMED);
        if (confirmed == null) confirmed = 0L;

        if (event.getParticipantLimit() != null && event.getParticipantLimit() > 0
                && confirmed >= event.getParticipantLimit()) {
            throw new ConflictResource("Достигнут лимит участников события");
        }

        Status status = (Boolean.FALSE.equals(event.getRequestModeration())
                || (event.getParticipantLimit() != null && event.getParticipantLimit() == 0))
                ? Status.CONFIRMED : Status.PENDING;

        Request request = Request.builder()
                .created(LocalDateTime.now())
                .eventId(eventId)
                .requesterId(userId)
                .status(status)
                .build();

        return RequestMapper.mapToDto(requestRepository.save(request));
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        Request request = requestRepository.findByIdAndRequesterId(requestId, userId)
                .orElseThrow(() -> new NotFoundResource("Request with id=" + requestId + " was not found"));
        request.setStatus(Status.CANCELED);
        return RequestMapper.mapToDto(requestRepository.save(request));
    }

    @Override
    @Transactional
    public List<ParticipationRequestDto> getRequestsByEventId(Long userId, Long eventId) {
        return requestRepository.findByEventId(eventId).stream()
                .map(RequestMapper::mapToDto)
                .toList();
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateRequestStatus(Long userId, Long eventId,
                                                              EventRequestStatusUpdateRequest updateRequest) {
        EventInternalDto event = getEventOrThrow(eventId);

        if (!event.getInitiatorId().equals(userId)) {
            throw new NotFoundResource("Событие " + eventId + " не найдено или недоступно пользователю " + userId);
        }

        int limit = event.getParticipantLimit() != null ? event.getParticipantLimit() : 0;

        List<Long> requestIds = updateRequest.getRequestIds();
        if (requestIds == null || requestIds.isEmpty()) {
            return new EventRequestStatusUpdateResult(List.of(), List.of());
        }

        List<Request> requests = requestRepository.findByIdInAndEventId(requestIds, eventId);
        if (requests.size() != requestIds.size()) {
            throw new NotFoundResource("Не все заявки найдены для события " + eventId);
        }

        for (Request req : requests) {
            if (!Status.PENDING.equals(req.getStatus())) {
                throw new ConflictResource("Можно изменять статус только у заявок в состоянии PENDING");
            }
        }

        Long countResult = requestRepository.countByEventIdAndStatus(eventId, Status.CONFIRMED);
        long currentConfirmed = countResult != null ? countResult : 0L;

        if (Status.CONFIRMED.equals(updateRequest.getStatus())
                && limit > 0 && currentConfirmed >= limit) {
            throw new ConflictResource("The participant limit has been reached");
        }

        List<ParticipationRequestDto> confirmedList = new ArrayList<>();
        List<ParticipationRequestDto> rejectedList = new ArrayList<>();

        for (Request req : requests) {
            if (Status.CONFIRMED.equals(updateRequest.getStatus())) {
                if (limit > 0 && currentConfirmed >= limit) {
                    req.setStatus(Status.REJECTED);
                    rejectedList.add(RequestMapper.mapToDto(requestRepository.save(req)));
                } else {
                    req.setStatus(Status.CONFIRMED);
                    confirmedList.add(RequestMapper.mapToDto(requestRepository.save(req)));
                    currentConfirmed++;
                }
            } else {
                req.setStatus(Status.REJECTED);
                rejectedList.add(RequestMapper.mapToDto(requestRepository.save(req)));
            }
        }

        if (limit > 0 && currentConfirmed >= limit) {
            requestRepository.findByEventId(eventId).stream()
                    .filter(r -> Status.PENDING.equals(r.getStatus()))
                    .forEach(r -> {
                        r.setStatus(Status.REJECTED);
                        requestRepository.save(r);
                    });
        }

        return EventRequestStatusUpdateResult.builder()
                .confirmedRequests(confirmedList)
                .rejectedRequests(rejectedList)
                .build();
    }

    @Override
    @Transactional
    public Long countConfirmedRequests(Long eventId) {
        Long count = requestRepository.countByEventIdAndStatus(eventId, Status.CONFIRMED);
        return count != null ? count : 0L;
    }

    @Override
    @Transactional
    public List<ParticipationRequestDto> getRequestsByEventIdIn(List<Long> eventIds) {
        return requestRepository.findAllByEventIdInAndStatus(eventIds, Status.CONFIRMED)
                .stream()
                .map(RequestMapper::mapToDto)
                .toList();
    }

    private EventInternalDto getEventOrThrow(Long eventId) {
        EventInternalDto event = eventServiceClient.getEventById(eventId);
        if (event == null) {
            throw new NotFoundResource("Event with id=" + eventId + " not found");
        }
        return event;
    }
}