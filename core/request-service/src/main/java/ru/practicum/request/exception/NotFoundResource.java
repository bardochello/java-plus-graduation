package ru.practicum.request.exception;

public class NotFoundResource extends RuntimeException {
    public NotFoundResource(String message) {
        super(message);
    }
}