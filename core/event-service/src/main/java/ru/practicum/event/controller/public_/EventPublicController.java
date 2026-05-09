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
import ru.practicum.debug.AgentNdjsonLog;
import ru.practicum.event.dto.EventShortDto;
import ru.practicum.event.service.EventService;
import ru.practicum.event.utill.EventGetPublicParam;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

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

        // #region agent log
        String qs = request.getQueryString();
        AgentNdjsonLog.log("H3", "EventPublicController.java:getEvents", "http_params", "pre-fix",
                String.format(Locale.US,
                        "{\"queryStringLen\":%d,\"textLen\":%d,\"categoriesNull\":%b,\"categoriesSize\":%d,"
                                + "\"paid\":%s,\"rangeStartNull\":%b,\"rangeEndNull\":%b,\"onlyAvailable\":%s,"
                                + "\"sortNull\":%b,\"from\":%d,\"size\":%d}",
                        qs == null ? 0 : qs.length(),
                        text == null ? 0 : text.length(),
                        categories == null,
                        categories == null ? 0 : categories.size(),
                        paid == null ? "null" : paid.toString(),
                        rangeStart == null,
                        rangeEnd == null,
                        onlyAvailable.toString(),
                        sort == null,
                        from,
                        size));
        // #endregion

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

        List<EventShortDto> events = eventService.getEventsByPublic(param);

        // Используем новый метод addHit
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