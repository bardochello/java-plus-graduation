package ru.practicum.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * DTO для представления ошибок API согласно спецификации.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
public class ApiError {
    private List<String> errors;
    private String status;
    private String reason;
    private String message;
    private String timestamp;
}