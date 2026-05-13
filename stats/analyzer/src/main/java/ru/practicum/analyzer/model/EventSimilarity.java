package ru.practicum.analyzer.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Хранит коэффициент сходства пары мероприятий.
 * Пары всегда записываются с eventA < eventB.
 */
@Entity
@Table(
        name = "event_similarity",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_a", "event_b"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventSimilarity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_a", nullable = false)
    private Long eventA;

    @Column(name = "event_b", nullable = false)
    private Long eventB;

    @Column(nullable = false)
    private Double score;

    // Храним как Instant — соответствует типу из Avro timestamp-millis
    @Column(nullable = false)
    private Instant timestamp;
}
