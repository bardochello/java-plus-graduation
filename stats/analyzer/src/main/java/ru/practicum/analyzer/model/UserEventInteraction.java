package ru.practicum.analyzer.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Хранит максимальный вес взаимодействия пользователя с мероприятием
 * и временну́ю метку последнего взаимодействия.
 */
@Entity
@Table(
        name = "user_event_interaction",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "event_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEventInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    /**
     * Максимальный вес действия: VIEW=0.4, REGISTER=0.8, LIKE=1.0
     */
    @Column(name = "max_weight", nullable = false)
    private Double maxWeight;

    // Храним как Instant — соответствует типу из Avro timestamp-millis
    // Используется для сортировки «недавних» взаимодействий
    @Column(nullable = false)
    private Instant timestamp;
}
