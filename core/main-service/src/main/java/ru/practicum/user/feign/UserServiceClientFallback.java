package ru.practicum.user.feign;

import org.springframework.stereotype.Component;
import ru.practicum.user.dto.UserDto;

/**
 * Fallback для UserServiceClient при недоступности user-service.
 */
@Component
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public UserDto getUserById(Long userId) {
        return null;
    }
}
