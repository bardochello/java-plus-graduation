package ru.practicum.request.exception;

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
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class ErrorHandler {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @ExceptionHandler(NotFoundResource.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(NotFoundResource e) {
        log.warn("404: {}", e.getMessage());
        return Map.of(
                "status", "NOT_FOUND",
                "reason", "The required object was not found.",
                "message", e.getMessage(),
                "timestamp", LocalDateTime.now().format(FORMATTER)
        );
    }

    @ExceptionHandler(ConflictResource.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleConflict(ConflictResource e) {
        log.warn("409: {}", e.getMessage());
        return Map.of(
                "status", "CONFLICT",
                "reason", "Integrity constraint has been violated.",
                "message", e.getMessage(),
                "timestamp", LocalDateTime.now().format(FORMATTER)
        );
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(Exception e) {
        log.warn("400 BAD REQUEST: {}", e.getMessage());

        // Специальная обработка для ConstraintViolationException
        if (e instanceof ConstraintViolationException) {
            ConstraintViolationException cve = (ConstraintViolationException) e;
            String violationMessages = cve.getConstraintViolations().stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining(", "));
            return Map.of(
                    "status", "BAD_REQUEST",
                    "reason", "Incorrectly made request.",
                    "message", violationMessages.isEmpty() ? "Validation failed" : violationMessages,
                    "timestamp", LocalDateTime.now().format(FORMATTER)
            );
        }

        // Специальная обработка для MissingServletRequestParameterException
        if (e instanceof MissingServletRequestParameterException) {
            MissingServletRequestParameterException mspe = (MissingServletRequestParameterException) e;
            return Map.of(
                    "status", "BAD_REQUEST",
                    "reason", "Incorrectly made request.",
                    "message", "Required parameter '" + mspe.getParameterName() + "' is missing",
                    "timestamp", LocalDateTime.now().format(FORMATTER)
            );
        }

        // Универсальная обработка
        return Map.of(
                "status", "BAD_REQUEST",
                "reason", "Incorrectly made request.",
                "message", e.getMessage() == null ? "Validation failed" : e.getMessage(),
                "timestamp", LocalDateTime.now().format(FORMATTER)
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleGeneric(Exception e) {
        log.error("500 INTERNAL SERVER ERROR: {}", e.getMessage(), e);
        return Map.of(
                "status", "INTERNAL_SERVER_ERROR",
                "reason", "An unexpected error occurred.",
                "message", e.getMessage() == null ? "Unknown error" : e.getMessage(),
                "timestamp", LocalDateTime.now().format(FORMATTER)
        );
    }
}