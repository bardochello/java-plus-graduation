package ru.practicum.user.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class ErrorHandler {
    private static final DateTimeFormatter FORMAT_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleNotFoundResource(NotFoundResource ex) {
        log.error("Resource not found: {}", ex.getMessage());
        return ApiError.builder()
                .errors(List.of())
                .message(ex.getMessage())
                .reason("The required object was not found.")
                .status("NOT_FOUND")
                .timestamp(LocalDateTime.now().format(FORMAT_DATE_TIME))
                .build();
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleConflictResource(ConflictResource ex) {
        log.error("Conflict: {}", ex.getMessage());
        return ApiError.builder()
                .errors(List.of())
                .message(ex.getMessage())
                .reason("For the requested operation the conditions are not met.")
                .status("CONFLICT")
                .timestamp(LocalDateTime.now().format(FORMAT_DATE_TIME))
                .build();
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleBadRequest(Exception ex) {
        log.error("Validation/Bad request: {}", ex.getMessage());
        String message = ex.getMessage();
        if (ex instanceof MethodArgumentNotValidException manve) {
            message = manve.getBindingResult().getFieldErrors().stream()
                    .map(e -> e.getField() + ": " + e.getDefaultMessage())
                    .findFirst()
                    .orElse(ex.getMessage());
        }
        return ApiError.builder()
                .errors(List.of())
                .message(message)
                .reason("Incorrectly made request.")
                .status("BAD_REQUEST")
                .timestamp(LocalDateTime.now().format(FORMAT_DATE_TIME))
                .build();
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ApiError.builder()
                .errors(List.of())
                .message(ex.getMessage())
                .reason("Internal server error.")
                .status("INTERNAL_SERVER_ERROR")
                .timestamp(LocalDateTime.now().format(FORMAT_DATE_TIME))
                .build();
    }
}
