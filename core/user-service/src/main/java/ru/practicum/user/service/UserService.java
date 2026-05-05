package ru.practicum.user.service;

import ru.practicum.user.dto.NewUserRequest;
import ru.practicum.user.dto.UserDto;
import ru.practicum.user.model.User;
import ru.practicum.user.util.UserGetParam;

import java.util.List;

public interface UserService {

    List<UserDto> getUsers(UserGetParam userGetParam);

    User getUserById(Long userId);

    UserDto createUser(NewUserRequest newUserRequest);

    void deleteUser(Long userId);
}
