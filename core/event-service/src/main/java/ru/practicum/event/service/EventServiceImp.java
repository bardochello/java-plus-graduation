package ru.practicum.event.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.AnalyzerClient;
import ru.practicum.CollectorClient;
import ru.practicum.category.service.CategoryService;
import ru.practicum.event.dto.*;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.Event;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.event.utill.EventGetAdminParam;
import ru.practicum.event.utill.EventGetPublicParam;
import ru.practicum.event.utill.State;
import ru.practicum.exception.BadRequestException;
import ru.practicum.exception.ConflictResource;
import ru.practicum.exception.NotFoundResource;
import ru.practicum.ewm.stats.proto.RecommendationsMessages.RecommendedEventProto;
import ru.practicum.ewm.stats.proto.ActionTypeProto;
import ru.practicum.request.feign.RequestServiceClient;
import ru.practicum.user.service.UserService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static ru.practicum.event.specification.EventSpecification.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImp implements EventService {

    private final CategoryService categoryService;
    private final UserService userService;
    private final EventRepository eventRepository;
    private final RequestServiceClient requestServiceClient;
    private final CollectorClient collectorClient;
    private final AnalyzerClient analyzerClient;

    // ─── Private/Admin ─────────────────────────────────────────────────────────

    @Override
    public EventFullDto get(long userId, long eventId) {
        Event event = getEventByIdAndInitiatorId(eventId, userId);
        Long confirmedRequests = safeCountConfirmedRequests(eventId);
        Double rating = getRatingForEvent(eventId);
        return EventMapper.toEventFullDto(event, confirmedRequests, rating);
    }

    @Override
    public List<EventShortDto> getAll(long userId, int from, int size) {
        Pageable pageable = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findByInitiatorId(userId, pageable).stream().toList();
        return enrichWithStats(events).stream()
                .map(EventMapper::mapToEventShortDto)
                .toList();
    }

    @Override
    @Transactional
    public EventFullDto create(long userId, NewEventDto eventDto) {
        if (!eventDto.getEventDate().isAfter(LocalDateTime.now().plusHours(2))) {
            throw new BadRequestException("Дата должна быть не ранее текущей + 2 часа");
        }
        eventDto.setCategoryObject(categoryService.getCategoryById(eventDto.getCategory()));
        eventDto.setInitiatorObject(userService.getUserById(userId));
        Event event = EventMapper.mapFromNewEventDto(eventDto);
        Event savedEvent = eventRepository.save(event);
        return EventMapper.toEventFullDto(savedEvent, 0L, 0.0);
    }

    @Override
    @Transactional
    public EventFullDto update(long userId, long eventId, UpdateEventUserRequest updateEvent) {
        Event event = getEventByIdAndInitiatorId(eventId, userId);
        if (event.getState() == State.PUBLISHED) {
            throw new ConflictResource("Нельзя редактировать опубликованное событие");
        }
        if (updateEvent.getEventDate() != null
                && updateEvent.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new BadRequestException("Дата события должна быть не ранее чем через 2 часа");
        }
        updateEventFields(event, updateEvent);
        if (updateEvent.getStateAction() != null) {
            switch (updateEvent.getStateAction()) {
                case SEND_TO_REVIEW -> event.setState(State.PENDING);
                case CANCEL_REVIEW  -> event.setState(State.CANCELED);
            }
        }
        Event updatedEvent = eventRepository.save(event);
        Long confirmedRequests = safeCountConfirmedRequests(eventId);
        Double rating = getRatingForEvent(eventId);
        return EventMapper.toEventFullDto(updatedEvent, confirmedRequests, rating);
    }

    @Override
    public List<EventFullDto> getEventsByAdmin(EventGetAdminParam param) {
        Pageable pageable = PageRequest.of(param.getFrom() / param.getSize(), param.getSize());
        Specification<Event> spec = Specification.where(null);
        if (param.getUsers() != null)      spec = spec.and(byUser(param.getUsers()));
        if (param.getStates() != null)     spec = spec.and(byStates(param.getStates()));
        if (param.getCategories() != null) spec = spec.and(byCategories(param.getCategories()));
        if (param.getRangeStart() != null) spec = spec.and(byRangeStart(param.getRangeStart()));
        if (param.getRangeEnd() != null)   spec = spec.and(byRangeEnd(param.getRangeEnd()));
        List<Event> events = eventRepository.findAll(spec, pageable).stream().toList();
        return enrichWithStats(events).stream()
                .map(EventMapper::mapToEventFullDto)
                .toList();
    }

    @Override
    @Transactional
    public EventFullDto updateEventByAdmin(long eventId, UpdateEventAdminRequest req) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundResource("Событие %d не найдено".formatted(eventId)));
        checkUpdateEventAdmin(event, req);
        if (req.getCategory() != null) {
            req.setCategoryObj(categoryService.getCategoryById(req.getCategory()));
        }
        EventMapper.updateEventFromAdminRequest(event, req);
        Event saved = eventRepository.save(event);
        return EventMapper.mapToEventFullDto(enrichWithStats(List.of(saved)).getFirst());
    }

    // ─── Public ────────────────────────────────────────────────────────────────

    @Override
    public List<EventShortDto> getEventsByPublic(EventGetPublicParam param) {
        if (param.getRangeStart() != null && param.getRangeEnd() != null
                && param.getRangeEnd().isBefore(param.getRangeStart())) {
            throw new BadRequestException("Некорректный интервал дат");
        }
        Sort sort = null;
        Specification<Event> spec = Specification.where(null);
        if (param.getText() != null && !param.getText().isBlank())
            spec = spec.and(byText(param.getText()));
        if (param.getCategories() != null)  spec = spec.and(byCategories(param.getCategories()));
        if (param.getPaid() != null)        spec = spec.and(byPaid(param.getPaid()));
        if (param.getRangeStart() != null)  spec = spec.and(byRangeStart(param.getRangeStart()));
        if (param.getRangeEnd() != null)    spec = spec.and(byRangeEnd(param.getRangeEnd()));
        if (param.getSort() != null && param.getSort().equals("EVENT_DATE"))
            sort = Sort.by("eventDate");
        spec = spec.and(byStates(param.getStates()));
        Pageable pageable = sort == null
                ? PageRequest.of(param.getFrom() / param.getSize(), param.getSize())
                : PageRequest.of(param.getFrom() / param.getSize(), param.getSize(), sort);
        List<Event> events = eventRepository.findAll(spec, pageable).stream().toList();
        List<Event> enriched = enrichWithStats(events);
        if (Boolean.TRUE.equals(param.getOnlyAvailable())) {
            enriched = enriched.stream()
                    .filter(e -> e.getParticipantLimit() == 0
                            || e.getConfirmedRequests() < e.getParticipantLimit())
                    .toList();
        }
        return enriched.stream().map(EventMapper::mapToEventShortDto).toList();
    }

    @Override
    @Transactional
    public EventFullDto getEventByPublic(long eventId, long userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundResource(
                        "Событие с id %d не найдено".formatted(eventId)));
        if (!event.getState().equals(State.PUBLISHED))
            throw new NotFoundResource("Событие с id %d не опубликовано".formatted(eventId));

        collectorClient.sendUserAction(userId, eventId, ActionTypeProto.ACTION_VIEW);

        Event enriched = enrichWithStats(List.of(event)).getFirst();
        return EventMapper.mapToEventFullDto(enriched);
    }

    // ─── Рекомендации ──────────────────────────────────────────────────────────

    @Override
    public List<EventShortDto> getRecommendations(long userId, int maxResults) {
        Map<Long, Double> scores = analyzerClient
                .getRecommendationsForUser(userId, maxResults)
                .collect(Collectors.toMap(
                        RecommendedEventProto::getEventId,
                        RecommendedEventProto::getScore,
                        (a, b) -> a
                ));

        if (scores.isEmpty()) return List.of();

        List<Long> recommendedIds = new ArrayList<>(scores.keySet());
        List<Event> events = eventRepository.findByIdIn(recommendedIds);

        Map<Long, Long> confirmed = safeCountConfirmedByIds(
                events.stream().map(Event::getId).collect(Collectors.toList()));

        return events.stream()
                .map(e -> e.toBuilder()
                        .confirmedRequests(confirmed.getOrDefault(e.getId(), 0L))
                        .rating(scores.getOrDefault(e.getId(), 0.0))
                        .build())
                .map(EventMapper::mapToEventShortDto)
                .toList();
    }

    // ─── Лайки ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void addLike(long userId, long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundResource(
                        "Событие с id=%d не найдено".formatted(eventId)));
        if (!event.getState().equals(State.PUBLISHED)) {
            throw new BadRequestException("Нельзя лайкнуть неопубликованное событие");
        }
        boolean hasRegistration = safeCheckRegistration(userId, eventId);
        if (!hasRegistration) {
            throw new BadRequestException(
                    "Лайкнуть можно только посещённое мероприятие. " +
                            "Пользователь %d не зарегистрирован на событие %d".formatted(userId, eventId));
        }
        collectorClient.sendUserAction(userId, eventId, ActionTypeProto.ACTION_LIKE);
    }

    // ─── Вспомогательные ───────────────────────────────────────────────────────

    @Override
    public Event getEventById(long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundResource(
                        "Событие с id=%d не найдено".formatted(eventId)));
    }

    private Event getEventByIdAndInitiatorId(long eventId, long userId) {
        Event event = getEventById(eventId);
        if (event.getInitiator().getId() != userId) {
            throw new NotFoundResource(
                    "Событие с id=%d не найдено или недоступно пользователю".formatted(eventId));
        }
        return event;
    }

    private List<Event> enrichWithStats(List<Event> events) {
        if (events.isEmpty()) return events;
        List<Long> eventIds = events.stream().map(Event::getId).collect(Collectors.toList());
        Map<Long, Long> confirmedByEvent = safeCountConfirmedByIds(eventIds);
        Map<Long, Double> ratings = safeGetRatings(eventIds);
        return events.stream()
                .map(e -> e.toBuilder()
                        .confirmedRequests(confirmedByEvent.getOrDefault(e.getId(), 0L))
                        .rating(ratings.getOrDefault(e.getId(), 0.0))
                        .build())
                .collect(Collectors.toList());
    }

    private Double getRatingForEvent(long eventId) {
        return safeGetRatings(List.of(eventId)).getOrDefault(eventId, 0.0);
    }

    private Map<Long, Double> safeGetRatings(List<Long> eventIds) {
        try {
            return analyzerClient.getInteractionsCount(eventIds)
                    .collect(Collectors.toMap(
                            RecommendedEventProto::getEventId,
                            RecommendedEventProto::getScore,
                            (a, b) -> a
                    ));
        } catch (Exception e) {
            log.warn("Failed to get ratings from Analyzer: {}", e.getMessage());
            return Map.of();
        }
    }

    private Long safeCountConfirmedRequests(long eventId) {
        try {
            Long count = requestServiceClient.countConfirmedRequests(eventId);
            return count != null ? count : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private Map<Long, Long> safeCountConfirmedByIds(List<Long> eventIds) {
        try {
            Map<Long, Long> result = requestServiceClient.countConfirmedByEventIds(eventIds);
            return result != null ? result : Map.of();
        } catch (Exception e) {
            log.warn("Failed to get confirmed requests: {}", e.getMessage());
            return Map.of();
        }
    }

    private boolean safeCheckRegistration(long userId, long eventId) {
        try {
            List<ru.practicum.request.dto.ParticipationRequestDto> requests =
                    requestServiceClient.getConfirmedRequestsByEventId(eventId);
            return requests.stream()
                    .anyMatch(r -> r.getRequester() != null && r.getRequester().equals(userId));
        } catch (Exception e) {
            log.warn("Failed to check registration for userId={}, eventId={}: {}",
                    userId, eventId, e.getMessage());
            return false;
        }
    }

    private void updateEventFields(Event event, UpdateEventUserRequest u) {
        if (u.getAnnotation() != null)       event.setAnnotation(u.getAnnotation());
        if (u.getCategory() != null)
            event.setCategory(categoryService.getCategoryById(u.getCategory()));
        if (u.getDescription() != null)      event.setDescription(u.getDescription());
        if (u.getEventDate() != null)        event.setEventDate(u.getEventDate());
        if (u.getLocation() != null)         event.setLocation(u.getLocation());
        if (u.getPaid() != null)             event.setPaid(u.getPaid());
        if (u.getParticipantLimit() != null) event.setParticipantLimit(u.getParticipantLimit());
        if (u.getRequestModeration() != null) event.setRequestModeration(u.getRequestModeration());
        if (u.getTitle() != null)            event.setTitle(u.getTitle());
    }

    private void checkUpdateEventAdmin(Event event, UpdateEventAdminRequest u) {
        if (u.hasStateAction()) {
            switch (u.getStateAction()) {
                case PUBLISH_EVENT -> {
                    if (!event.getState().equals(State.PENDING))
                        throw new ConflictResource("Событие можно публиковать только в статусе 'Ожидание'");
                }
                case REJECT_EVENT -> {
                    if (event.getState().equals(State.PUBLISHED))
                        throw new ConflictResource("Событие можно отклонить, только если оно не опубликовано");
                }
            }
        }
        LocalDateTime eventDate = u.hasEventDate() ? u.getEventDate() : event.getEventDate();
        if (!eventDate.isAfter(LocalDateTime.now().minusHours(1)))
            throw new BadRequestException("Дата начала события должна быть не ранее чем за час от публикации");
    }
}