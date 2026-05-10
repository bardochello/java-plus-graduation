package ru.practicum.exception;

import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.stereotype.Component;

/**
 * Кастомный декодер ошибок Feign.
 * Конвертирует HTTP-ошибки от удалённых сервисов в локальные исключения,
 * чтобы ErrorHandler мог их корректно обработать.
 */
public class FeignErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        return switch (response.status()) {
            case 404 -> new NotFoundResource(
                    "Запрашиваемый ресурс не найден (статус 404 от удалённого сервиса: " + methodKey + ")");
            case 409 -> new ConflictResource(
                    "Конфликт при обращении к удалённому сервису: " + methodKey);
            case 400 -> new BadRequestException(
                    "Некорректный запрос к удалённому сервису: " + methodKey);
            default -> defaultDecoder.decode(methodKey, response);
        };
    }
}