package ru.practicum.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.practicum.event.validate.ErrorCustomFuture;

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
                .status(HttpStatus.NOT_FOUND.toString())
                .timestamp(LocalDateTime.now().format(FORMAT_DATE_TIME))
                .build();
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleConflictResource(ConflictResource ex) {
        log.error("Resource conflict: {}", ex.getMessage());
        return ApiError.builder()
                .errors(List.of())
                .message(ex.getMessage())
                .reason("For the requested operation the conditions are not met.")
                .status(HttpStatus.CONFLICT.toString())
                .timestamp(LocalDateTime.now().format(FORMAT_DATE_TIME))
                .build();
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleErrorCustomFuture(ErrorCustomFuture ex) {
        log.error("Date validation error: {}", ex.getMessage());
        return ApiError.builder()
                .errors(List.of())
                .message(ex.getMessage())
                .reason("For the requested operation the conditions are not met.")
                .status(HttpStatus.CONFLICT.toString())
                .timestamp(LocalDateTime.now().format(FORMAT_DATE_TIME))
                .build();
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler
    public ApiError handleArgumentNotValidException(MethodArgumentNotValidException ex) {
        log.error("Argument validation error: {}", ex.getMessage());
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse(ex.getMessage());
        return ApiError.builder()
                .errors(List.of())
                .message(message)
                .reason("Incorrectly made request.")
                .status(HttpStatus.BAD_REQUEST.toString())
                .timestamp(LocalDateTime.now().format(FORMAT_DATE_TIME))
                .build();
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler
    public ApiError handleConstraintViolationException(ConstraintViolationException ex) {
        log.error("Constraint violation: {}", ex.getMessage());
        return ApiError.builder()
                .errors(List.of())
                .message(ex.getMessage())
                .reason("Incorrectly made request.")
                .status(HttpStatus.BAD_REQUEST.toString())
                .timestamp(LocalDateTime.now().format(FORMAT_DATE_TIME))
                .build();
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler
    public ApiError handleBadRequestException(BadRequestException ex) {
        log.error("Bad request: {}", ex.getMessage());
        return ApiError.builder()
                .errors(List.of())
                .message(ex.getMessage())
                .reason("Incorrectly made request.")
                .status(HttpStatus.BAD_REQUEST.toString())
                .timestamp(LocalDateTime.now().format(FORMAT_DATE_TIME))
                .build();
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        log.error("Missing parameter: {}", ex.getMessage());
        return ApiError.builder()
                .errors(List.of())
                .message(ex.getMessage())
                .reason("Incorrectly made request.")
                .status(HttpStatus.BAD_REQUEST.toString())
                .timestamp(LocalDateTime.now().format(FORMAT_DATE_TIME))
                .build();
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiError handleForbiddenResource(ForbiddenResource ex) {
        log.error("Forbidden: {}", ex.getMessage());
        return ApiError.builder()
                .errors(List.of())
                .message(ex.getMessage())
                .reason("Access is denied.")
                .status(HttpStatus.FORBIDDEN.toString())
                .timestamp(LocalDateTime.now().format(FORMAT_DATE_TIME))
                .build();
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleException(Exception ex) {
        log.error("Internal server error: {}", ex.getMessage(), ex);
        return ApiError.builder()
                .errors(List.of())
                .message(ex.getMessage())
                .reason("Unexpected error occurred.")
                .status(HttpStatus.INTERNAL_SERVER_ERROR.toString())
                .timestamp(LocalDateTime.now().format(FORMAT_DATE_TIME))
                .build();
    }
}