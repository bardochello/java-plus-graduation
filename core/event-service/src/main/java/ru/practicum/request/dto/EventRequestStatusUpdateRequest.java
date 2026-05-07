package ru.practicum.request.dto;

import lombok.*;
import ru.practicum.request.utill.Status;

import java.util.List;

/**
 * DTO для запроса обновления статусов заявок (используется в Feign-клиенте).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventRequestStatusUpdateRequest {
    private List<Long> requestIds;
    private Status status;
}