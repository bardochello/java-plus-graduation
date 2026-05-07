package ru.practicum.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.exception.ConflictResource;
import ru.practicum.exception.NotFoundResource;
import ru.practicum.user.dto.NewUserRequest;
import ru.practicum.user.dto.UserDto;
import ru.practicum.user.feign.UserServiceClient;
import ru.practicum.user.mapper.UserMapper;
import ru.practicum.user.model.User;
import ru.practicum.user.repository.UserRepository;
import ru.practicum.user.utill.UserGetParam;

import java.util.List;

/**
 * Реализация сервиса для работы с пользователями в main-service.
 * Администрирование пользователей вынесено в user-service.
 * Данный сервис обеспечивает доступ к локальному кэшу пользователей
 * (необходим для FK в таблице events) и получение данных через Feign из user-service.
 */
@Slf4j
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class UserServiceImp implements UserService {
    private final UserRepository userRepository;
    private final UserServiceClient userServiceClient;

    @Override
    public List<UserDto> getUsers(UserGetParam userGetParam) {
        List<User> users;

        if (userGetParam.getIds() != null && !userGetParam.getIds().isEmpty()) {
            users = userRepository.findAllByIdIn(userGetParam.getIds());
        } else {
            Pageable pageable = PageRequest.of(userGetParam.getFrom() / userGetParam.getSize(), userGetParam.getSize());
            users = userRepository.findAll(pageable).getContent();
        }

        return users.stream()
                .map(UserMapper::mapToDto)
                .toList();
    }

    /**
     * Получает пользователя по идентификатору.
     * Сначала ищет в локальном кэше main-service.
     * Если не найден — запрашивает у user-service через Feign и кэширует через upsert.
     *
     * @param userId идентификатор пользователя
     * @return сущность пользователя
     * @throws NotFoundResource если пользователь не найден ни в кэше, ни в user-service
     */
    @Override
    @Transactional
    public User getUserById(Long userId) {
        return userRepository.findById(userId).orElseGet(() -> {
            log.info("Пользователь {} не найден в локальном кэше, запрашиваем у user-service", userId);
            UserDto userDto = userServiceClient.getUserById(userId);
            if (userDto == null) {
                throw new NotFoundResource("Пользователь с id=" + userId + " не найден");
            }
            // upsert: INSERT ... ON CONFLICT DO UPDATE — безопасно при параллельных запросах
            userRepository.upsertUser(userDto.getId(), userDto.getName(), userDto.getEmail());
            return userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundResource("Пользователь с id=" + userId + " не найден после синхронизации"));
        });
    }

    @Override
    @Transactional
    public UserDto createUser(NewUserRequest newUserRequest) {
        userRepository.findByEmailContainingIgnoreCase(newUserRequest.getEmail())
                .ifPresent(user -> {
                    throw new ConflictResource("Пользователь с email '" + newUserRequest.getEmail() + "' уже существует");
                });

        User user = UserMapper.mapToUser(newUserRequest);
        User savedUser = userRepository.save(user);

        return UserMapper.mapToDto(savedUser);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundResource("Пользователь с id=" + userId + " не найден");
        }
        userRepository.deleteById(userId);
    }
}