package ru.practicum.request.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    public List<ParticipationRequestDto> getRequestsByUserId(Long userId) {
        log.debug("Getting requests for user {}", userId);
        return requestRepository.findByRequesterId(userId).stream()
                .map(RequestMapper::mapToDto)
                .toList();
    }

    @Override
    @Transactional
    public ParticipationRequestDto createRequest(Long userId, Long eventId) {
        log.info("=== CREATE REQUEST START: userId={}, eventId={} ===", userId, eventId);

        EventDto event = getEventOrThrow(eventId);

        if (event.getInitiatorId().equals(userId)) {
            log.warn("Event initiator cannot create request");
            throw new ConflictResource("Инициатор события не может добавить запрос на участие в своём событии");
        }
        if (!"PUBLISHED".equals(event.getState())) {
            log.warn("Event is not published");
            throw new ConflictResource("Нельзя участвовать в неопубликованном событии");
        }
        if (requestRepository.existsByRequesterIdAndEventId(userId, eventId)) {
            log.warn("Request already exists");
            throw new ConflictResource("Заявка уже существует");
        }

        Long confirmedCount = requestRepository.countByEventIdAndStatus(eventId, Status.CONFIRMED);
        confirmedCount = confirmedCount != null ? confirmedCount : 0L;

        if (event.getParticipantLimit() != null && event.getParticipantLimit() > 0
                && confirmedCount >= event.getParticipantLimit()) {
            log.warn("Participant limit reached");
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

        Request savedRequest = requestRepository.save(request);
        log.info("=== CREATE REQUEST END: id={}, status={} ===", savedRequest.getId(), savedRequest.getStatus());

        return RequestMapper.mapToDto(savedRequest);
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        log.info("Canceling request: id={}", requestId);

        Request request = requestRepository.findByIdAndRequesterId(requestId, userId)
                .orElseThrow(() -> new NotFoundResource("Request with id=" + requestId + " was not found"));

        if (!Status.PENDING.equals(request.getStatus())) {
            throw new ConflictResource("Нельзя отменить заявку, не находящуюся в состоянии ожидания");
        }

        request.setStatus(Status.CANCELED);
        Request canceledRequest = requestRepository.save(request);
        log.info("Request cancelled: id={}", canceledRequest.getId());

        return RequestMapper.mapToDto(canceledRequest);
    }

    @Override
    public List<ParticipationRequestDto> getRequestsByEventId(Long userId, Long eventId) {
        log.debug("Getting requests for event {}", eventId);
        return requestRepository.findByEventId(eventId).stream()
                .map(RequestMapper::mapToDto)
                .toList();
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateRequestStatus(Long userId, Long eventId,
                                                              EventRequestStatusUpdateRequest updateRequest) {
        log.info("=== UPDATE REQUEST STATUS START: eventId={}, status={} ===", eventId, updateRequest.getStatus());

        EventDto event = getEventOrThrow(eventId);

        if (!event.getInitiatorId().equals(userId)) {
            throw new NotFoundResource("Событие " + eventId + " не найдено или недоступно пользователю " + userId);
        }

        int limit = event.getParticipantLimit() != null ? event.getParticipantLimit() : 0;

        List<Long> requestIds = updateRequest.getRequestIds();
        if (requestIds == null || requestIds.isEmpty()) {
            log.debug("No request IDs provided");
            return new EventRequestStatusUpdateResult(List.of(), List.of());
        }

        log.info("Processing {} requests", requestIds.size());

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
        log.info("Current confirmed count: {}", currentConfirmed);

        if (Status.CONFIRMED.equals(updateRequest.getStatus())
                && limit > 0 && currentConfirmed >= limit) {
            throw new ConflictResource("The participant limit has been reached");
        }

        List<ParticipationRequestDto> confirmedList = new ArrayList<>();
        List<ParticipationRequestDto> rejectedList = new ArrayList<>();

        for (Request req : requests) {
            if (Status.CONFIRMED.equals(updateRequest.getStatus())) {
                if (limit > 0 && currentConfirmed >= limit) {
                    log.info("Rejecting request {} due to limit", req.getId());
                    req.setStatus(Status.REJECTED);
                    requestRepository.save(req);
                    rejectedList.add(RequestMapper.mapToDto(req));
                } else {
                    log.info("Accepting request {}", req.getId());
                    req.setStatus(Status.CONFIRMED);
                    requestRepository.save(req);
                    confirmedList.add(RequestMapper.mapToDto(req));
                    currentConfirmed++;
                }
            } else {
                log.info("Rejecting request {}", req.getId());
                req.setStatus(Status.REJECTED);
                requestRepository.save(req);
                rejectedList.add(RequestMapper.mapToDto(req));
            }
        }

        // Auto-reject remaining PENDING requests if limit is reached
        if (limit > 0 && currentConfirmed >= limit) {
            List<Request> pendingRequests = requestRepository.findByEventId(eventId).stream()
                    .filter(r -> Status.PENDING.equals(r.getStatus()))
                    .toList();

            for (Request pending : pendingRequests) {
                log.info("Auto-rejecting pending request {}", pending.getId());
                pending.setStatus(Status.REJECTED);
                requestRepository.save(pending);
            }
        }

        // Verify the counts
        Long finalConfirmedCount = requestRepository.countByEventIdAndStatus(eventId, Status.CONFIRMED);
        log.info("=== UPDATE REQUEST STATUS END: confirmed={}, rejected={}, final_db_count={} ===",
                confirmedList.size(), rejectedList.size(), finalConfirmedCount);

        return EventRequestStatusUpdateResult.builder()
                .confirmedRequests(confirmedList)
                .rejectedRequests(rejectedList)
                .build();
    }

    @Override
    public Long countConfirmedRequests(Long eventId) {
        Long count = requestRepository.countByEventIdAndStatus(eventId, Status.CONFIRMED);
        Long result = count != null ? count : 0L;
        log.info("Count confirmed requests for eventId {}: {}", eventId, result);
        return result;
    }

    @Override
    public List<ParticipationRequestDto> getRequestsByEventIdIn(List<Long> eventIds) {
        log.info("========== GET CONFIRMED REQUESTS FOR EVENTS ==========");
        log.info("Requesting for eventIds: {}", eventIds);

        List<Request> allRequests = requestRepository.findAllByEventIdInAndStatus(eventIds, Status.CONFIRMED);
        log.info("Step 1 - Total CONFIRMED requests found in DB: {}", allRequests.size());

        for (Request req : allRequests) {
            log.info("  -> Request: id={}, eventId={}, status={}", req.getId(), req.getEventId(), req.getStatus());
        }


        List<ParticipationRequestDto> result = allRequests.stream()
                .map(RequestMapper::mapToDto)
                .toList();

        log.info("Step 3 - Converted to DTOs: {}", result.size());
        for (ParticipationRequestDto dto : result) {
            log.info("  -> DTO: id={}, event={}, status={}", dto.getId(), dto.getEvent(), dto.getStatus());
        }

        for (Long eventId : eventIds) {
            long countForEvent = result.stream()
                    .filter(r -> r.getEvent().equals(eventId))
                    .count();
            log.info("Step 4 - Event {}: {} confirmed requests", eventId, countForEvent);
        }

        log.info("========== RETURNING {} REQUESTS ==========", result.size());
        return result;
    }

    private EventDto getEventOrThrow(Long eventId) {
        try {
            EventDto event = eventServiceClient.getEventById(eventId);
            if (event == null) {
                throw new NotFoundResource("Event with id=" + eventId + " not found");
            }
            return event;
        } catch (Exception e) {
            if (e instanceof NotFoundResource) {
                throw (NotFoundResource) e;
            }
            throw new NotFoundResource("Event with id=" + eventId + " not found");
        }
    }
}