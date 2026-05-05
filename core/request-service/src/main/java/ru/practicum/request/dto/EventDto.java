package ru.practicum.request.dto;

import lombok.*;

/**
 * DTO события для получения данных из main-service.
 * Содержит только поля, необходимые сервису заявок.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDto {
    private Long id;
    private Long initiatorId;
    private String state;
    private Integer participantLimit;
    private Boolean requestModeration;
}