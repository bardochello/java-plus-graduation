package ru.practicum.request.dto;

import lombok.*;

import java.util.List;

/**
 * DTO для результата обновления статусов заявок (используется в Feign-клиенте).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventRequestStatusUpdateResult {
    private List<ParticipationRequestDto> confirmedRequests;
    private List<ParticipationRequestDto> rejectedRequests;
}