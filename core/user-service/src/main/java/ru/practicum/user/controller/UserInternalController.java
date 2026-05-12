package ru.practicum.user.controller;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.user.dto.UserDto;
import ru.practicum.user.mapper.UserMapper;
import ru.practicum.user.model.User;
import ru.practicum.user.service.UserService;

/**
 * Внутренний контроллер пользователей для межсервисного взаимодействия.
 * Предоставляет API для main-service.
 */
@Validated
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class UserInternalController {

    private final UserService userService;

    /**
     * Возвращает данные о пользователе для main-service.
     *
     * @param userId идентификатор пользователя
     * @return DTO пользователя
     */
    @GetMapping("/{userId}")
    public UserDto getUserById(@PathVariable @Positive Long userId) {
        User user = userService.getUserById(userId);
        return UserMapper.mapToDto(user);
    }
}
