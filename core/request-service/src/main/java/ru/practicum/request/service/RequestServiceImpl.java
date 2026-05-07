package ru.practicum.request.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequestServiceImpl implements RequestService {

    private final RequestRepository requestRepository;
    private final EventServiceClient mainServiceClient;

    @Override
    @Transactional
    public ParticipationRequestDto createRequest(Long userId, Long eventId) {
        // Простая проверка на дубликат
        if (requestRepository.existsByRequesterIdAndEventId(userId, eventId)) {
            throw new ConflictResource("Нельзя добавить повторный запрос на участие в событии");
        }

        Request request = new Request();
        request.setEventId(eventId);
        request.setRequesterId(userId);
        request.setCreated(LocalDateTime.now());
        request.setStatus(Status.PENDING); // по умолчанию PENDING (модерация решается в event-service)

        Request saved = requestRepository.save(request);
        return RequestMapper.mapToDto(saved);
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundResource("Request with id=" + requestId + " was not found"));

        if (!request.getRequesterId().equals(userId)) {
            throw new ConflictResource("User is not the owner of this request");
        }

        if (request.getStatus() == Status.CONFIRMED) {
            throw new ConflictResource("Нельзя отменить уже принятую заявку");
        }

        request.setStatus(Status.CANCELED);
        return RequestMapper.mapToDto(requestRepository.save(request));
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateRequestStatus(Long userId, Long eventId,
                                                              EventRequestStatusUpdateRequest updateRequest,
                                                              Integer participantLimit, Boolean requestModeration) {

        if (updateRequest.getRequestIds() == null || updateRequest.getRequestIds().isEmpty()) {
            return EventRequestStatusUpdateResult.builder()
                    .confirmedRequests(new ArrayList<>())
                    .rejectedRequests(new ArrayList<>())
                    .build();
        }

        int limit = participantLimit != null ? participantLimit : 0;

        Long confirmedCount = requestRepository.countByEventIdAndStatus(eventId, Status.CONFIRMED);
        if (confirmedCount == null) confirmedCount = 0L;

        if (updateRequest.getStatus() == Status.CONFIRMED && limit > 0 && confirmedCount >= limit) {
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
                if (limit > 0 && confirmedCount >= limit) {
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
    public List<ParticipationRequestDto> getUserRequests(Long userId) {
        List<Request> requests = requestRepository.findByRequesterId(userId);
        return requests.stream()
                .map(RequestMapper::mapToDto)
                .toList();
    }

    @Override
    public List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        List<Request> requests = requestRepository.findByEventId(eventId);
        return requests.stream()
                .map(RequestMapper::mapToDto)
                .toList();
    }

    @Override
    public List<ParticipationRequestDto> getRequestsByEventId(Long eventId) {
        List<Request> requests = requestRepository.findByEventId(eventId);
        return requests.stream()
                .map(RequestMapper::mapToDto)
                .toList();
    }

    @Override
    public Long countConfirmedRequests(Long eventId) {
        Long count = requestRepository.countByEventIdAndStatus(eventId, Status.CONFIRMED);
        return count != null ? count : 0L;
    }
}