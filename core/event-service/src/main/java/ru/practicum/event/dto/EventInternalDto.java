package ru.practicum.event.dto;

import lombok.*;
import ru.practicum.event.utill.State;

/**
 * DTO для внутреннего API события.
 * Используется request-service для получения данных о событии.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventInternalDto {
    private Long id;
    private Long initiatorId;
    private State state;
    private Integer participantLimit;
    private Boolean requestModeration;
}