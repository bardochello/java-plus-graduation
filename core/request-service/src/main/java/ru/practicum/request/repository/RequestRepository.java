package ru.practicum.request.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.request.model.Request;
import ru.practicum.request.utill.Status;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RequestRepository extends JpaRepository<Request, Long> {

    List<Request> findByRequesterId(Long requesterId);

    boolean existsByRequesterIdAndEventId(Long requesterId, Long eventId);

    Long countByEventIdAndStatus(Long eventId, Status status);

    List<Request> findByEventId(Long eventId);

    Optional<Request> findByIdAndRequesterId(Long requestId, Long requesterId);

    List<Request> findByIdInAndEventId(List<Long> requestIds, Long eventId);

    /** Подтверждённые заявки на мероприятие — для проверки факта посещения. */
    List<Request> findByEventIdAndStatus(Long eventId, Status status);

    @Query("SELECT r FROM Request r WHERE r.eventId IN :eventIds AND r.status = :status")
    List<Request> findAllByEventIdInAndStatus(@Param("eventIds") Collection<Long> eventIds,
                                              @Param("status") Status status);

    @Query("SELECT r.eventId, COUNT(r.id) FROM Request r " +
            "WHERE r.eventId IN :eventIds AND r.status = 'CONFIRMED' " +
            "GROUP BY r.eventId")
    List<Object[]> countConfirmedByEventIds(@Param("eventIds") List<Long> eventIds);
}
