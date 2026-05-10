package ru.practicum.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.user.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailContainingIgnoreCase(String email);

    List<User> findAllByIdIn(List<Long> ids);

    /**
     * Сохраняет пользователя с явным ID (кэш из user-service).
     * Использует INSERT ON CONFLICT DO NOTHING чтобы не дублировать записи.
     */
    @Modifying
    @Query(value = "INSERT INTO users (id, name, email) VALUES (:id, :name, :email) ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, email = EXCLUDED.email",
            nativeQuery = true)
    void upsertUser(@Param("id") Long id, @Param("name") String name, @Param("email") String email);
}