package ru.practicum.user.exception;

public class ConflictResource extends RuntimeException {
    public ConflictResource(String message) {
        super(message);
    }
}
