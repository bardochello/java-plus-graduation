package ru.practicum.analyzer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.analyzer.model.UserEventInteraction;

import java.util.List;
import java.util.Optional;

public interface UserEventInteractionRepository extends JpaRepository<UserEventInteraction, Long> {

    Optional<UserEventInteraction> findByUserIdAndEventId(long userId, long eventId);

    /**
     * Последние N мероприятий, с которыми взаимодействовал пользователь
     * (сортировка по времени, от новых к старым).
     */
    @Query("SELECT u FROM UserEventInteraction u " +
            "WHERE u.userId = :userId " +
            "ORDER BY u.timestamp DESC " +
            "LIMIT :limit")
    List<UserEventInteraction> findRecentByUserId(@Param("userId") long userId,
                                                  @Param("limit") int limit);

    /**
     * Все мероприятия, с которыми взаимодействовал пользователь (только ID).
     */
    @Query("SELECT u.eventId FROM UserEventInteraction u WHERE u.userId = :userId")
    List<Long> findEventIdsByUserId(@Param("userId") long userId);

    /**
     * Оценки пользователя для конкретных мероприятий.
     * Используется при вычислении взвешенной суммы для предсказания.
     */
    @Query("SELECT u FROM UserEventInteraction u " +
            "WHERE u.userId = :userId AND u.eventId IN :eventIds")
    List<UserEventInteraction> findByUserIdAndEventIds(@Param("userId") long userId,
                                                       @Param("eventIds") List<Long> eventIds);

    /**
     * Для GetInteractionsCount: сумма максимальных весов всех пользователей
     * для каждого из запрошенных мероприятий.
     * Возвращает пары [eventId, sumOfWeights].
     */
    @Query("SELECT u.eventId, SUM(u.maxWeight) FROM UserEventInteraction u " +
            "WHERE u.eventId IN :eventIds " +
            "GROUP BY u.eventId")
    List<Object[]> sumWeightsByEventIds(@Param("eventIds") List<Long> eventIds);
}
