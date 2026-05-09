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
        log.info("Creating request: userId={}, eventId={}", userId, eventId);

        EventDto event = getEventOrThrow(eventId);
        log.debug("Event found: state={}, requestModeration={}, participantLimit={}",
                event.getState(), event.getRequestModeration(), event.getParticipantLimit());

        if (event.getInitiatorId().equals(userId)) {
            log.warn("Event initiator {} tried to create request for their own event {}", userId, eventId);
            throw new ConflictResource("Инициатор события не может добавить запрос на участие в своём событии");
        }
        if (!"PUBLISHED".equals(event.getState())) {
            log.warn("Cannot create request for unpublished event: state={}", event.getState());
            throw new ConflictResource("Нельзя участвовать в неопубликованном событии");
        }
        if (requestRepository.existsByRequesterIdAndEventId(userId, eventId)) {
            log.warn("Request already exists: userId={}, eventId={}", userId, eventId);
            throw new ConflictResource("Заявка уже существует");
        }

        Long confirmedCount = requestRepository.countByEventIdAndStatus(eventId, Status.CONFIRMED);
        confirmedCount = confirmedCount != null ? confirmedCount : 0L;

        log.debug("Confirmed requests count for event {}: {}", eventId, confirmedCount);

        if (event.getParticipantLimit() != null && event.getParticipantLimit() > 0
                && confirmedCount >= event.getParticipantLimit()) {
            log.warn("Participant limit reached: eventId={}, limit={}, confirmed={}",
                    eventId, event.getParticipantLimit(), confirmedCount);
            throw new ConflictResource("Достигнут лимит участников события");
        }

        Status status = (Boolean.FALSE.equals(event.getRequestModeration())
                || (event.getParticipantLimit() != null && event.getParticipantLimit() == 0))
                ? Status.CONFIRMED : Status.PENDING;

        log.info("Creating request with status: {}", status);

        Request request = Request.builder()
                .created(LocalDateTime.now())
                .eventId(eventId)
                .requesterId(userId)
                .status(status)
                .build();

        Request savedRequest = requestRepository.save(request);
        log.info("Request created: id={}, status={}", savedRequest.getId(), savedRequest.getStatus());

        return RequestMapper.mapToDto(savedRequest);
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        log.info("Canceling request: userId={}, requestId={}", userId, requestId);

        Request request = requestRepository.findByIdAndRequesterId(requestId, userId)
                .orElseThrow(() -> {
                    log.error("Request not found: id={}, userId={}", requestId, userId);
                    return new NotFoundResource("Request with id=" + requestId + " was not found");
                });

        if (!Status.PENDING.equals(request.getStatus())) {
            log.warn("Cannot cancel request with status: {}", request.getStatus());
            throw new ConflictResource("Нельзя отменить заявку, не находящуюся в состоянии ожидания");
        }

        request.setStatus(Status.CANCELED);
        Request canceledRequest = requestRepository.save(request);
        log.info("Request cancelled: id={}", canceledRequest.getId());

        return RequestMapper.mapToDto(canceledRequest);
    }

    @Override
    public List<ParticipationRequestDto> getRequestsByEventId(Long userId, Long eventId) {
        log.debug("Getting requests for event {}: userId={}", eventId, userId);
        return requestRepository.findByEventId(eventId).stream()
                .map(RequestMapper::mapToDto)
                .toList();
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateRequestStatus(Long userId, Long eventId,
                                                              EventRequestStatusUpdateRequest updateRequest) {
        log.info("Updating request status: userId={}, eventId={}, newStatus={}",
                userId, eventId, updateRequest.getStatus());

        EventDto event = getEventOrThrow(eventId);
        log.debug("Event found: initiatorId={}, participantLimit={}, requestModeration={}",
                event.getInitiatorId(), event.getParticipantLimit(), event.getRequestModeration());

        if (!event.getInitiatorId().equals(userId)) {
            log.error("User {} is not initiator of event {}", userId, eventId);
            throw new NotFoundResource("Событие " + eventId + " не найдено или недоступно пользователю " + userId);
        }

        int limit = event.getParticipantLimit() != null ? event.getParticipantLimit() : 0;
        log.debug("Event limit: {}", limit);

        List<Long> requestIds = updateRequest.getRequestIds();
        if (requestIds == null || requestIds.isEmpty()) {
            log.debug("No request IDs provided");
            return new EventRequestStatusUpdateResult(List.of(), List.of());
        }

        List<Request> requests = requestRepository.findByIdInAndEventId(requestIds, eventId);
        log.debug("Found {} requests for event {}", requests.size(), eventId);

        if (requests.size() != requestIds.size()) {
            log.error("Not all requests found: expected={}, found={}", requestIds.size(), requests.size());
            throw new NotFoundResource("Не все заявки найдены для события " + eventId);
        }

        for (Request req : requests) {
            if (!Status.PENDING.equals(req.getStatus())) {
                log.warn("Request {} has status {}, only PENDING can be updated", req.getId(), req.getStatus());
                throw new ConflictResource("Можно изменять статус только у заявок в состоянии PENDING");
            }
        }

        Long countResult = requestRepository.countByEventIdAndStatus(eventId, Status.CONFIRMED);
        long currentConfirmed = countResult != null ? countResult : 0L;
        log.debug("Current confirmed requests: {}", currentConfirmed);

        if (Status.CONFIRMED.equals(updateRequest.getStatus())
                && limit > 0 && currentConfirmed >= limit) {
            log.error("Participant limit already reached: limit={}, confirmed={}", limit, currentConfirmed);
            throw new ConflictResource("The participant limit has been reached");
        }

        List<ParticipationRequestDto> confirmedList = new ArrayList<>();
        List<ParticipationRequestDto> rejectedList = new ArrayList<>();

        for (Request req : requests) {
            if (Status.CONFIRMED.equals(updateRequest.getStatus())) {
                if (limit > 0 && currentConfirmed >= limit) {
                    log.debug("Limit reached, rejecting request {}", req.getId());
                    req.setStatus(Status.REJECTED);
                    rejectedList.add(RequestMapper.mapToDto(requestRepository.save(req)));
                } else {
                    log.debug("Approving request {}", req.getId());
                    req.setStatus(Status.CONFIRMED);
                    confirmedList.add(RequestMapper.mapToDto(requestRepository.save(req)));
                    currentConfirmed++;
                }
            } else {
                log.debug("Rejecting request {}", req.getId());
                req.setStatus(Status.REJECTED);
                rejectedList.add(RequestMapper.mapToDto(requestRepository.save(req)));
            }
        }

        if (limit > 0 && currentConfirmed >= limit) {
            log.info("Limit reached, auto-rejecting remaining PENDING requests");
            List<Request> pendingRequests = requestRepository.findByEventId(eventId).stream()
                    .filter(r -> Status.PENDING.equals(r.getStatus()))
                    .toList();

            for (Request pending : pendingRequests) {
                log.debug("Auto-rejecting pending request {}", pending.getId());
                pending.setStatus(Status.REJECTED);
                requestRepository.save(pending);
            }
        }

        log.info("Request status update completed: confirmed={}, rejected={}",
                confirmedList.size(), rejectedList.size());

        return EventRequestStatusUpdateResult.builder()
                .confirmedRequests(confirmedList)
                .rejectedRequests(rejectedList)
                .build();
    }

    @Override
    public Long countConfirmedRequests(Long eventId) {
        log.debug("Counting confirmed requests for event {}", eventId);
        Long count = requestRepository.countByEventIdAndStatus(eventId, Status.CONFIRMED);
        Long result = count != null ? count : 0L;
        log.debug("Confirmed requests count for event {}: {}", eventId, result);
        return result;
    }

    @Override
    public List<ParticipationRequestDto> getRequestsByEventIdIn(List<Long> eventIds) {
        log.info("===== GETTING CONFIRMED REQUESTS =====");
        log.info("Requesting confirmed requests for eventIds: {}", eventIds);
        
        List<Request> allRequests = requestRepository.findAllByEventIdInAndStatus(eventIds, Status.CONFIRMED);
        log.info("Total CONFIRMED requests found in DB: {}", allRequests.size());
        
        // Логирование каждого найденного запроса для отладки
        for (Request req : allRequests) {
            log.info("Found CONFIRMED request: id={}, eventId={}, requesterId={}, status={}",
                    req.getId(), req.getEventId(), req.getRequesterId(), req.getStatus());
        }
        
        List<ParticipationRequestDto> result = allRequests.stream()
                .map(RequestMapper::mapToDto)
                .toList();
        
        log.info("Converted to DTOs: {}", result.size());
        for (ParticipationRequestDto dto : result) {
            log.info("DTO: id={}, event={}, status={}", dto.getId(), dto.getEvent(), dto.getStatus());
        }
        
        log.info("===== END GETTING CONFIRMED REQUESTS =====");
        return result;
    }

    private EventDto getEventOrThrow(Long eventId) {
        try {
            log.debug("Fetching event details: {}", eventId);
            EventDto event = eventServiceClient.getEventById(eventId);
            if (event == null) {
                log.error("Event not found: {}", eventId);
                throw new NotFoundResource("Event with id=" + eventId + " not found");
            }
            log.debug("Event fetched successfully: {}", eventId);
            return event;
        } catch (Exception e) {
            log.error("Failed to fetch event: {}", eventId, e);
            if (e instanceof NotFoundResource) {
                throw (NotFoundResource) e;
            }
            throw new NotFoundResource("Event with id=" + eventId + " not found");
        }
    }
}
