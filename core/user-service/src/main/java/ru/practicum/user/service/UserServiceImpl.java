package ru.practicum.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.user.dto.NewUserRequest;
import ru.practicum.user.dto.UserDto;
import ru.practicum.user.exception.ConflictResource;
import ru.practicum.user.exception.NotFoundResource;
import ru.practicum.user.mapper.UserMapper;
import ru.practicum.user.model.User;
import ru.practicum.user.repository.UserRepository;
import ru.practicum.user.util.UserGetParam;

import java.util.List;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;

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

    @Override
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundResource("Пользователь с id=" + userId + " не найден"));
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
