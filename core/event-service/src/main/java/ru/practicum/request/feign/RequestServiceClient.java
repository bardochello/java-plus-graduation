package ru.practicum.request.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import ru.practicum.request.dto.ParticipationRequestDto;

import java.util.List;
import java.util.Map;

/**
 * Feign-клиент для взаимодействия с request-service.
 */
@FeignClient(name = "request-service", path = "/internal/requests")
public interface RequestServiceClient {

    @GetMapping("/count/{eventId}")
    Long countConfirmedRequests(@PathVariable("eventId") Long eventId);

    @PostMapping("/count-by-events")
    Map<Long, Long> countConfirmedByEventIds(@RequestBody List<Long> eventIds);

    /**
     * Возвращает список подтверждённых заявок на мероприятие.
     * Используется для проверки: посещал ли пользователь мероприятие перед лайком.
     */
    @GetMapping("/confirmed/{eventId}")
    List<ParticipationRequestDto> getConfirmedRequestsByEventId(@PathVariable("eventId") Long eventId);
}
