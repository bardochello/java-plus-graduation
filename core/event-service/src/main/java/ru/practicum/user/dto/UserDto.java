package ru.practicum.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO для представления данных пользователя.
 */
@Builder
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    private long id;

    @Email(message = "Некорректный формат email")
    private String email;

    @NotBlank(message = "Имя не может быть пустым")
    private String name;
}