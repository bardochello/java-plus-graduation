package ru.practicum.event.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.event.model.Event;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с событиями.
 */
public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {

    Page<Event> findByInitiatorId(Long initiatorId, Pageable pageable);

    Optional<Event> findByIdAndInitiatorId(Long eventId, Long initiatorId);

    List<Event> findByIdIn(List<Long> eventIds);

    boolean existsByCategoryId(Long categoryId);

    // ===== Likes =====

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO likes_events (user_id, event_id) VALUES (:userId, :eventId)", nativeQuery = true)
    void addLike(Long userId, Long eventId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM likes_events WHERE user_id = :userId AND event_id = :eventId", nativeQuery = true)
    void deleteLike(Long userId, Long eventId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM likes_events WHERE user_id = :userId AND event_id = :eventId)", nativeQuery = true)
    boolean checkLikeExisting(Long userId, Long eventId);

    @Query(value = "SELECT COUNT(*) FROM likes_events WHERE event_id = :eventId", nativeQuery = true)
    long countLikesByEventId(Long eventId);

    @Query(value = "SELECT e.* FROM events e " +
            "LEFT JOIN (SELECT event_id, COUNT(*) AS likes FROM likes_events GROUP BY event_id) AS rate " +
            "ON e.id = rate.event_id " +
            "ORDER BY rate.likes DESC NULLS LAST " +
            "LIMIT :count", nativeQuery = true)
    List<Event> findTop(Integer count);
}
