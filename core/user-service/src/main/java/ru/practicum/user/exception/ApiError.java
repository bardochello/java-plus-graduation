package ru.practicum.user.exception;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class ApiError {
    private List<String> errors;
    private String message;
    private String reason;
    private String status;
    private String timestamp;
}
