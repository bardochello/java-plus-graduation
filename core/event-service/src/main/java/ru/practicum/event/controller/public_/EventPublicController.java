package ru.practicum.event.controller.public_;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.event.dto.EventFullDto;
import ru.practicum.event.dto.EventShortDto;
import ru.practicum.event.service.EventService;
import ru.practicum.event.utill.EventGetPublicParam;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Публичный контроллер для операций с событиями.
 *
 * Изменения по ТЗ:
 * - GET /events          — больше НЕ отправляет VIEW
 * - GET /events/{id}     — отправляет VIEW через CollectorClient (userId из заголовка)
 * - GET /events/recommendations — новый эндпоинт рекомендаций
 * - PUT /events/{eventId}/like  — новый эндпоинт лайка
 */
@Validated
@RequestMapping("/events")
@RestController
@RequiredArgsConstructor
public class EventPublicController {

    private final EventService eventService;

    /** GET /events — список событий без отправки статистики. */
    @GetMapping
    public List<EventShortDto> getEvents(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) List<Long> categories,
            @RequestParam(required = false) Boolean paid,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime rangeStart,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime rangeEnd,
            @RequestParam(defaultValue = "false") Boolean onlyAvailable,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") @PositiveOrZero int from,
            @RequestParam(defaultValue = "10") @Positive int size) {

        EventGetPublicParam param = EventGetPublicParam.builder()
                .text(text)
                .categories(categories)
                .paid(paid)
                .rangeStart(rangeStart == null && rangeEnd == null ? LocalDateTime.now() : rangeStart)
                .rangeEnd(rangeEnd)
                .onlyAvailable(onlyAvailable)
                .sort(sort)
                .from(from)
                .size(size)
                .build();

        // По ТЗ: при GET /events информацию о просмотре НЕ отправляем
        return eventService.getEventsByPublic(param);
    }

    /**
     * GET /events/recommendations — персональные рекомендации мероприятий для пользователя.
     * Идентификатор пользователя из заголовка X-EWM-USER-ID.
     */
    @GetMapping("/recommendations")
    public List<EventShortDto> getRecommendations(
            @RequestHeader("X-EWM-USER-ID") long userId,
            @RequestParam(defaultValue = "10") @Positive int maxResults) {
        return eventService.getRecommendations(userId, maxResults);
    }

    /**
     * GET /events/{id} — детали события.
     * По ТЗ: отправляем VIEW в Collector с userId из заголовка.
     */
    @GetMapping("/{id}")
    public EventFullDto getEvent(
            @PathVariable @Positive long id,
            @RequestHeader("X-EWM-USER-ID") long userId) {
        return eventService.getEventByPublic(id, userId);
    }

    /**
     * PUT /events/{eventId}/like — пользователь лайкает мероприятие.
     * Пользователь может лайкать только посещённые мероприятия (иначе 400).
     */
    @PutMapping("/{eventId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addLike(
            @PathVariable @Positive long eventId,
            @RequestHeader("X-EWM-USER-ID") long userId) {
        eventService.addLike(userId, eventId);
    }
}
