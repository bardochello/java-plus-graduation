package ru.practicum.request.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.request.model.Request;
import ru.practicum.request.utill.Status;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с заявками на участие в событиях.
 */
public interface RequestRepository extends JpaRepository<Request, Long> {

    List<Request> findByEventId(Long eventId);

    List<Request> findByIdInAndEventId(List<Long> requestIds, Long eventId);

    Long countByEventIdAndStatus(Long eventId, Status status);

    Optional<Request> findByEventIdAndRequesterId(Long eventId, Long requesterId);

    List<Request> findByRequesterId(Long requesterId);

    @Query("SELECT r FROM Request r WHERE r.eventId IN :eventIds AND r.status = :status")
    List<Request> findAllByEventIdInAndStatus(@Param("eventIds") Collection<Long> eventIds,
                                              @Param("status") Status status);

    List<Request> findByEventIdAndStatus(Long eventId, Status status);

    boolean existsByRequesterIdAndEventId(Long requesterId, Long eventId);

    Optional<Request> findByIdAndRequesterId(Long requestId, Long requesterId);
}