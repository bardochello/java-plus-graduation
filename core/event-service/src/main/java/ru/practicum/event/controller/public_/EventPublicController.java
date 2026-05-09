package ru.practicum.event.controller.public_;

import dto.EndpointHitDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.StatsClient;
import ru.practicum.event.dto.EventFullDto;
import ru.practicum.event.dto.EventShortDto;
import ru.practicum.event.service.EventService;
import ru.practicum.event.utill.EventGetPublicParam;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Публичный контроллер для операций с событиями.
 */
@Validated
@RequestMapping("/events")
@RestController
@RequiredArgsConstructor
public class EventPublicController {

    private static final String APPLICATION = "main-service";
    private final EventService eventService;
    private final StatsClient statsClient;

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
            @RequestParam(defaultValue = "10") @Positive int size,
            HttpServletRequest request) {

        EventGetPublicParam param = EventGetPublicParam.builder()
                .text(text)
                .categories(categories)
                .paid(paid)
                .rangeStart(rangeStart == null ? LocalDateTime.now() : rangeStart)  // ← ИСПРАВЛЕНО
                .rangeEnd(rangeEnd)
                .onlyAvailable(onlyAvailable)
                .sort(sort)
                .from(from)
                .size(size)
                .build();

        List<EventShortDto> events = eventService.getEventsByPublic(param);

        statsClient.addHit(EndpointHitDto.builder()
                .app(APPLICATION)
                .uri(request.getRequestURI())
                .ip(request.getRemoteAddr())
                .timestamp(LocalDateTime.now())
                .build());

        return events;
    }

    @GetMapping("/{id}")
    public EventFullDto getEvent(@PathVariable @Positive long id,
                                 HttpServletRequest request) {

        try {
            statsClient.addHit(EndpointHitDto.builder()
                    .app(APPLICATION)
                    .uri(request.getRequestURI())
                    .ip(request.getRemoteAddr())
                    .timestamp(LocalDateTime.now())
                    .build());
        } catch (Exception ignored) {
        }

        return eventService.getEventByPublic(id);
    }
}