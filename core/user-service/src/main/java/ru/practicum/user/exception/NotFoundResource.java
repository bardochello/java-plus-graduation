package ru.practicum.user.exception;

public class NotFoundResource extends RuntimeException {
    public NotFoundResource(String message) {
        super(message);
    }
}
