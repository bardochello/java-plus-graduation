package ru.practicum.analyzer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.analyzer.model.EventSimilarity;

import java.util.List;
import java.util.Optional;

public interface EventSimilarityRepository extends JpaRepository<EventSimilarity, Long> {

    /**
     * Находит все записи о сходстве, где участвует данное мероприятие
     * (в любой из двух позиций — eventA или eventB).
     */
    @Query("SELECT es FROM EventSimilarity es " +
            "WHERE es.eventA = :eventId OR es.eventB = :eventId " +
            "ORDER BY es.score DESC")
    List<EventSimilarity> findAllByEventId(@Param("eventId") long eventId);

    /**
     * Находит конкретную запись о сходстве пары (с упорядоченными ID).
     */
    @Query("SELECT es FROM EventSimilarity es " +
            "WHERE es.eventA = :eventA AND es.eventB = :eventB")
    Optional<EventSimilarity> findByEventPair(@Param("eventA") long eventA,
                                              @Param("eventB") long eventB);

    /**
     * Находит K мероприятий, наиболее похожих на данное,
     * с которыми пользователь уже взаимодействовал.
     * Используется для вычисления предсказанной оценки.
     *
     * @param eventId    мероприятие, для которого ищем соседей
     * @param userEvents список мероприятий, с которыми пользователь взаимодействовал
     */
    @Query("SELECT es FROM EventSimilarity es " +
            "WHERE (es.eventA = :eventId AND es.eventB IN :userEvents) " +
            "   OR (es.eventB = :eventId AND es.eventA IN :userEvents) " +
            "ORDER BY es.score DESC")
    List<EventSimilarity> findTopNeighbors(@Param("eventId") long eventId,
                                           @Param("userEvents") List<Long> userEvents);

    /**
     * Находит мероприятия, похожие на одно из указанных,
     * которые НЕ входят в список уже просмотренных пользователем.
     * Используется для подбора кандидатов при формировании рекомендаций.
     */
    @Query("SELECT es FROM EventSimilarity es " +
            "WHERE (es.eventA IN :seenEvents AND es.eventB NOT IN :seenEvents) " +
            "   OR (es.eventB IN :seenEvents AND es.eventA NOT IN :seenEvents) " +
            "ORDER BY es.score DESC")
    List<EventSimilarity> findSimilarNotSeen(@Param("seenEvents") List<Long> seenEvents);
}
