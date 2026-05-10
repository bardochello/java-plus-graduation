package ru.practicum.request.exception;

public class ConflictResource extends RuntimeException {
    public ConflictResource(String message) {
        super(message);
    }
}