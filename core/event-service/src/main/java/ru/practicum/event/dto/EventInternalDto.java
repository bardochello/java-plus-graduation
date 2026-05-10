package ru.practicum.event.dto;

import lombok.*;

/**
 * DTO для внутреннего API события (для request-service).
 * state — String, чтобы идеально совпадать с EventDto в request-service.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventInternalDto {
    private Long id;
    private Long initiatorId;
    private String state;
    private Integer participantLimit;
    private Boolean requestModeration;
}