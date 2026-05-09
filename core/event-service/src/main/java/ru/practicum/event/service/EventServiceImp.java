package ru.practicum.event.service;

import dto.ViewStatsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.StatsClient;
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
import ru.practicum.request.dto.ParticipationRequestDto;
import ru.practicum.request.feign.RequestServiceClient;
import ru.practicum.user.service.UserService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ru.practicum.event.specification.EventSpecification.*;

/**
 * Реализация сервиса для работы с событиями.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImp implements EventService {

    private static final String EVENT_URI_PATTERN = "/events/%d";
    private static final DateTimeFormatter DT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CategoryService categoryService;
    private final UserService userService;
    private final EventRepository eventRepository;
    private final RequestServiceClient requestServiceClient;
    private final StatsClient statsClient;

    @Override
    public EventFullDto get(long userId, long eventId) {
        Event event = getEventByIdAndInitiatorId(eventId, userId);
        Long confirmedRequests = safeCountConfirmedRequests(eventId);
        Long views = getViewsForEvent(event.getCreatedOn(), eventId);
        return EventMapper.toEventFullDto(event, confirmedRequests, views);
    }

    @Override
    public List<EventShortDto> getAll(long userId, int from, int size) {
        Pageable pageable = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findByInitiatorId(userId, pageable).stream().toList();
        return updateEventFieldStats(events).stream()
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
        return EventMapper.toEventFullDto(savedEvent, 0L, 0L);
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
            throw new BadRequestException(
                    "Дата события должна быть не ранее чем через 2 часа от текущего момента");
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
        Long views = getViewsForEvent(event.getCreatedOn(), eventId);
        return EventMapper.toEventFullDto(updatedEvent, confirmedRequests, views);
    }

    @Override
    public List<EventFullDto> getEventsByAdmin(EventGetAdminParam param) {
        Pageable pageable = PageRequest.of(param.getFrom() / param.getSize(), param.getSize());
        Specification<Event> specification = Specification.where(null);

        if (param.getUsers() != null)
            specification = specification.and(byUser(param.getUsers()));
        if (param.getStates() != null)
            specification = specification.and(byStates(param.getStates()));
        if (param.getCategories() != null)
            specification = specification.and(byCategories(param.getCategories()));
        if (param.getRangeStart() != null)
            specification = specification.and(byRangeStart(param.getRangeStart()));
        if (param.getRangeEnd() != null)
            specification = specification.and(byRangeEnd(param.getRangeEnd()));

        List<Event> events = eventRepository.findAll(specification, pageable).stream().toList();
        return updateEventFieldStats(events).stream()
                .map(EventMapper::mapToEventFullDto)
                .toList();
    }

    @Override
    @Transactional
    public EventFullDto updateEventByAdmin(long eventId, UpdateEventAdminRequest updateEventAdminRequest) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundResource("Событие %d не найдено".formatted(eventId)));

        checkUpdateEventAdmin(event, updateEventAdminRequest);

        if (updateEventAdminRequest.getCategory() != null) {
            updateEventAdminRequest.setCategoryObj(
                    categoryService.getCategoryById(updateEventAdminRequest.getCategory()));
        }

        EventMapper.updateEventFromAdminRequest(event, updateEventAdminRequest);
        return EventMapper.mapToEventFullDto(eventRepository.save(event));
    }

    @Override
    public List<EventShortDto> getEventsByPublic(EventGetPublicParam param) {
        if (param.getRangeStart() != null && param.getRangeEnd() != null
                && param.getRangeEnd().isBefore(param.getRangeStart())) {
            throw new BadRequestException("Некорректный интервал дат");
        }

        Sort sort = null;
        Specification<Event> specification = Specification.where(null);

        if (param.getText() != null && !param.getText().isBlank())
            specification = specification.and(byText(param.getText()));
        if (param.getCategories() != null)
            specification = specification.and(byCategories(param.getCategories()));
        if (param.getPaid() != null)
            specification = specification.and(byPaid(param.getPaid()));
        if (param.getRangeStart() != null)
            specification = specification.and(byRangeStart(param.getRangeStart()));
        if (param.getRangeEnd() != null)
            specification = specification.and(byRangeEnd(param.getRangeEnd()));
        if (param.getSort() != null && !param.getSort().isBlank()) {
            if (param.getSort().equals("EVENT_DATE"))
                sort = Sort.by("eventDate");
            else if (param.getSort().equals("VIEWS"))
                sort = Sort.by(Sort.Direction.DESC, "views");
        }

        specification = specification.and(byStates(param.getStates()));

        Pageable pageable = sort == null
                ? PageRequest.of(param.getFrom() / param.getSize(), param.getSize())
                : PageRequest.of(param.getFrom() / param.getSize(), param.getSize(), sort);

        List<Event> events = eventRepository.findAll(specification, pageable).stream().toList();

        List<Event> enrichedEvents = updateEventFieldStats(events);

        if (param.getOnlyAvailable() != null && param.getOnlyAvailable()) {
            enrichedEvents = enrichedEvents.stream()
                    .filter(e -> e.getParticipantLimit() == 0 || e.getConfirmedRequests() < e.getParticipantLimit())
                    .toList();
        }

        return enrichedEvents.stream()
                .map(EventMapper::mapToEventShortDto)
                .toList();
    }

    @Override
    @Transactional
    public EventFullDto getEventByPublic(long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundResource(
                        "Событие с id %d не найдено".formatted(eventId)));
        if (!event.getState().equals(State.PUBLISHED))
            throw new NotFoundResource("Событие с id %d не опубликовано".formatted(eventId));

        return EventMapper.mapToEventFullDto(updateEventFieldStats(List.of(event)).getFirst());
    }

    @Override
    public Event getEventById(long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundResource("Событие с id=%d не найдено".formatted(eventId)));
    }

    private Event getEventByIdAndInitiatorId(long eventId, long userId) {
        Event event = getEventById(eventId);
        if (event.getInitiator().getId() != userId) {
            throw new NotFoundResource("Событие с id=%d не найдено или недоступно пользователю".formatted(eventId));
        }
        return event;
    }

    private void updateEventFields(Event event, UpdateEventUserRequest updateEvent) {
        if (updateEvent.getAnnotation() != null)    event.setAnnotation(updateEvent.getAnnotation());
        if (updateEvent.getCategory() != null)
            event.setCategory(categoryService.getCategoryById(updateEvent.getCategory()));
        if (updateEvent.getDescription() != null)   event.setDescription(updateEvent.getDescription());
        if (updateEvent.getEventDate() != null)     event.setEventDate(updateEvent.getEventDate());
        if (updateEvent.getLocation() != null)      event.setLocation(updateEvent.getLocation());
        if (updateEvent.getPaid() != null)          event.setPaid(updateEvent.getPaid());
        if (updateEvent.getParticipantLimit() != null) event.setParticipantLimit(updateEvent.getParticipantLimit());
        if (updateEvent.getRequestModeration() != null) event.setRequestModeration(updateEvent.getRequestModeration());
        if (updateEvent.getTitle() != null)         event.setTitle(updateEvent.getTitle());
    }

    private Long getViewsForEvent(LocalDateTime start, Long eventId) {
        try {
            String startStr = start.format(DT_FORMATTER);
            String endStr   = LocalDateTime.now().format(DT_FORMATTER);
            List<ViewStatsDto> stats = statsClient.getStats(
                    startStr, endStr, List.of(EVENT_URI_PATTERN.formatted(eventId)), true);
            return stats.isEmpty() ? 0L : stats.getFirst().getHits();
        } catch (Exception e) {
            return 0L;
        }
    }

    private void checkUpdateEventAdmin(Event event, UpdateEventAdminRequest updateEvent) {
        if (updateEvent.hasStateAction()) {
            switch (updateEvent.getStateAction()) {
                case PUBLISH_EVENT -> {
                    if (!event.getState().equals(State.PENDING))
                        throw new ConflictResource("Событие можно публиковать только в статусе 'Ожидание'");
                }
                case REJECT_EVENT -> {
                    if (event.getState().equals(State.PUBLISHED))
                        throw new ConflictResource(
                                "Событие можно отклонить, только если оно еще не опубликовано");
                }
            }
        }

        LocalDateTime eventDate = updateEvent.hasEventDate()
                ? updateEvent.getEventDate()
                : event.getEventDate();

        if (!eventDate.isAfter(LocalDateTime.now().minusHours(1)))
            throw new BadRequestException(
                    "Дата начала изменяемого события должна быть не ранее чем за час от даты публикации");
    }

    private Long safeCountConfirmedRequests(long eventId) {
        try {
            Long count = requestServiceClient.countConfirmedRequests(eventId);
            return count == null ? 0L : count;
        } catch (Exception e) {
            return 0L;
        }
    }

    private List<Event> updateEventFieldStats(List<Event> events) {
        if (events.isEmpty()) {
            return events;
        }

        Map<Long, Event> eventMap = events.stream()
                .collect(Collectors.toMap(Event::getId, Function.identity()));

        List<Long> eventIds = List.copyOf(eventMap.keySet());

        Map<Long, Long> confirmedByEvent = eventIds.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        this::safeCountConfirmedRequests
                ));

        final Map<Long, Long> finalConfirmed = confirmedByEvent;

        List<String> listUrl = eventIds.stream()
                .map(EVENT_URI_PATTERN::formatted)
                .collect(Collectors.toList());

        Optional<LocalDateTime> minCreated = eventMap.values().stream()
                .map(Event::getCreatedOn)
                .min(LocalDateTime::compareTo);

        String startStr = minCreated.orElse(LocalDateTime.now().minusYears(1)).format(DT_FORMATTER);
        String endStr   = LocalDateTime.now().plusMinutes(1).format(DT_FORMATTER);

        Map<String, Long> statsCount;
        try {
            statsCount = statsClient.getStats(startStr, endStr, listUrl, true)
                    .stream()
                    .collect(Collectors.toMap(ViewStatsDto::getUri, ViewStatsDto::getHits));
        } catch (Exception e) {
            statsCount = Map.of();
        }
        final Map<String, Long> finalStats = statsCount;

        return eventMap.values().stream()
                .map(event -> event.toBuilder()
                        .confirmedRequests(finalConfirmed.getOrDefault(event.getId(), 0L))
                        .views(finalStats.getOrDefault(EVENT_URI_PATTERN.formatted(event.getId()), 0L))
                        .build())
                .toList();
    }
}