package ru.practicum.request.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.request.dto.EventDto;
import ru.practicum.request.dto.EventRequestStatusUpdateRequest;
import ru.practicum.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.request.dto.ParticipationRequestDto;
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

/**
 * Реализация сервиса для работы с заявками на участие в событиях.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequestServiceImpl implements RequestService {

    private final RequestRepository requestRepository;
    private final EventServiceClient mainServiceClient;

    @Override
    public List<ParticipationRequestDto> getRequestsByUserId(Long userId) {
        return requestRepository.findByRequesterId(userId).stream()
                .map(RequestMapper::mapToDto)
                .toList();
    }

    @Override
    @Transactional
    public ParticipationRequestDto createRequest(Long userId, Long eventId) {
        EventDto event = getEventOrThrow(eventId);

        if (event.getInitiatorId().equals(userId)) {
            throw new ConflictResource("Инициатор события не может подать заявку на участие в своём событии");
        }

        if (!"PUBLISHED".equals(event.getState())) {
            throw new ConflictResource("Нельзя участвовать в неопубликованном событии");
        }

        if (requestRepository.existsByRequesterIdAndEventId(userId, eventId)) {
            throw new ConflictResource("Заявка на участие в этом событии уже существует");
        }

        Long confirmedRequests = requestRepository.countByEventIdAndStatus(eventId, Status.CONFIRMED);
        if (event.getParticipantLimit() > 0 && confirmedRequests >= event.getParticipantLimit()) {
            throw new ConflictResource("Достигнут лимит участников для этого события");
        }

        Status status = Status.PENDING;
        if (!event.getRequestModeration() || event.getParticipantLimit() == 0) {
            status = Status.CONFIRMED;
        }

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
                .orElseThrow(() -> new NotFoundResource("Заявка с id=" + requestId + " не найдена"));

        request.setStatus(Status.CANCELED);
        return RequestMapper.mapToDto(requestRepository.save(request));
    }

    @Override
    public List<ParticipationRequestDto> getRequestsByEventId(Long userId, Long eventId) {
        return requestRepository.findByEventId(eventId).stream()
                .map(RequestMapper::mapToDto)
                .toList();
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateRequestStatus(Long userId, Long eventId,
                                                              EventRequestStatusUpdateRequest updateRequest) {
        EventDto event = getEventOrThrow(eventId);

        if (!event.getRequestModeration() || event.getParticipantLimit() == 0) {
            throw new ConflictResource("Подтверждение заявок не требуется для этого события");
        }

        Long confirmedCount = requestRepository.countByEventIdAndStatus(eventId, Status.CONFIRMED);

        if (updateRequest.getStatus() == Status.CONFIRMED
                && event.getParticipantLimit() > 0
                && confirmedCount >= event.getParticipantLimit()) {
            throw new ConflictResource("Достигнут лимит участников");
        }

        List<Request> requests = requestRepository.findByIdInAndEventId(updateRequest.getRequestIds(), eventId);
        List<ParticipationRequestDto> confirmed = new ArrayList<>();
        List<ParticipationRequestDto> rejected = new ArrayList<>();

        for (Request request : requests) {
            if (request.getStatus() != Status.PENDING) {
                throw new ConflictResource("Можно изменить статус только заявок в состоянии ожидания");
            }

            if (updateRequest.getStatus() == Status.CONFIRMED) {
                if (event.getParticipantLimit() > 0 && confirmedCount >= event.getParticipantLimit()) {
                    request.setStatus(Status.REJECTED);
                    rejected.add(RequestMapper.mapToDto(requestRepository.save(request)));
                } else {
                    request.setStatus(Status.CONFIRMED);
                    confirmed.add(RequestMapper.mapToDto(requestRepository.save(request)));
                    confirmedCount++;
                }
            } else {
                request.setStatus(Status.REJECTED);
                rejected.add(RequestMapper.mapToDto(requestRepository.save(request)));
            }
        }

        return EventRequestStatusUpdateResult.builder()
                .confirmedRequests(confirmed)
                .rejectedRequests(rejected)
                .build();
    }

    @Override
    public Long countConfirmedRequests(Long eventId) {
        return requestRepository.countByEventIdAndStatus(eventId, Status.CONFIRMED);
    }

    @Override
    public List<ParticipationRequestDto> getRequestsByEventIdIn(List<Long> eventIds) {
        return requestRepository.findAllByEventIdInAndStatus(eventIds, Status.CONFIRMED).stream()
                .map(RequestMapper::mapToDto)
                .toList();
    }

    private EventDto getEventOrThrow(Long eventId) {
        EventDto event = mainServiceClient.getEventById(eventId);
        if (event == null) {
            throw new NotFoundResource("Событие с id=" + eventId + " не найдено");
        }
        return event;
    }
}