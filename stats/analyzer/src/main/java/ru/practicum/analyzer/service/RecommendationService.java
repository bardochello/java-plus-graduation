package ru.practicum.analyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.analyzer.model.EventSimilarity;
import ru.practicum.analyzer.model.UserEventInteraction;
import ru.practicum.analyzer.repository.EventSimilarityRepository;
import ru.practicum.analyzer.repository.UserEventInteractionRepository;
import ru.practicum.ewm.stats.message.RecommendedEventProto;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    private static final int RECENT_EVENTS_LIMIT = 10;
    private static final int K_NEIGHBORS = 10;

    private final EventSimilarityRepository similarityRepository;
    private final UserEventInteractionRepository interactionRepository;

    // ─── GetSimilarEvents ──────────────────────────────────────────────────────

    /**
     * Возвращает Top-N мероприятий, максимально похожих на заданное,
     * с которыми пользователь ещё не взаимодействовал.
     */
    public Stream<RecommendedEventProto> getSimilarEvents(long eventId, long userId, int maxResults) {
        List<Long> userEvents = interactionRepository.findEventIdsByUserId(userId);
        Set<Long> seenSet = new HashSet<>(userEvents);

        List<EventSimilarity> similarities = similarityRepository.findAllByEventId(eventId);

        return similarities.stream()
                .map(es -> {
                    long otherId = (es.getEventA() == eventId) ? es.getEventB() : es.getEventA();
                    return Map.entry(otherId, es.getScore());
                })
                .filter(entry -> !seenSet.contains(entry.getKey()))
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(maxResults)
                .map(entry -> RecommendedEventProto.newBuilder()
                        .setEventId(entry.getKey())
                        .setScore(entry.getValue())
                        .build());
    }

    // ─── GetRecommendationsForUser ─────────────────────────────────────────────

    /**
     * Возвращает Top-N рекомендованных мероприятий для пользователя
     * на основе предсказанной оценки (item-based collaborative filtering).
     *
     * Алгоритм:
     * 1. Берём последние RECENT_EVENTS_LIMIT мероприятий, с которыми взаимодействовал пользователь.
     * 2. Находим похожие на них мероприятия, с которыми пользователь не взаимодействовал.
     * 3. Для каждого кандидата вычисляем предсказанную оценку:
     *    r̂ = Σ(sim(p,q) * r_u,q) / Σ sim(p,q)
     * 4. Возвращаем Top-N по убыванию предсказанной оценки.
     */
    public Stream<RecommendedEventProto> getRecommendationsForUser(long userId, int maxResults) {
        List<UserEventInteraction> recentInteractions =
                interactionRepository.findRecentByUserId(userId, RECENT_EVENTS_LIMIT);

        if (recentInteractions.isEmpty()) {
            log.debug("No interactions for userId={}, returning empty", userId);
            return Stream.empty();
        }

        List<Long> seenEventIds = recentInteractions.stream()
                .map(UserEventInteraction::getEventId)
                .collect(Collectors.toList());
        Set<Long> seenSet = new HashSet<>(seenEventIds);

        // Кандидаты: мероприятия похожие на просмотренные, но ещё не виденные пользователем
        List<EventSimilarity> similarNotSeen = similarityRepository.findSimilarNotSeen(seenEventIds);

        Set<Long> candidateIds = similarNotSeen.stream()
                .map(es -> seenSet.contains(es.getEventA()) ? es.getEventB() : es.getEventA())
                .collect(Collectors.toSet());

        if (candidateIds.isEmpty()) {
            return Stream.empty();
        }

        Map<Long, Double> userRatings = recentInteractions.stream()
                .collect(Collectors.toMap(
                        UserEventInteraction::getEventId,
                        UserEventInteraction::getMaxWeight
                ));

        // Предсказываем оценку для каждого кандидата
        Map<Long, Double> predictions = new HashMap<>();

        for (long candidateId : candidateIds) {
            List<EventSimilarity> neighbors =
                    similarityRepository.findTopNeighbors(candidateId, seenEventIds)
                            .stream()
                            .limit(K_NEIGHBORS)
                            .collect(Collectors.toList());

            if (neighbors.isEmpty()) continue;

            double weightedSum = 0.0;
            double simSum      = 0.0;

            for (EventSimilarity neighbor : neighbors) {
                long neighborId = (neighbor.getEventA() == candidateId)
                        ? neighbor.getEventB()
                        : neighbor.getEventA();

                Double userRating = userRatings.get(neighborId);
                if (userRating == null) continue;

                double sim = neighbor.getScore();
                weightedSum += sim * userRating;
                simSum      += sim;
            }

            if (simSum > 0) {
                predictions.put(candidateId, weightedSum / simSum);
            }
        }

        return predictions.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(maxResults)
                .map(entry -> RecommendedEventProto.newBuilder()
                        .setEventId(entry.getKey())
                        .setScore(entry.getValue())
                        .build());
    }

    // ─── GetInteractionsCount ──────────────────────────────────────────────────

    /**
     * Возвращает сумму максимальных весов взаимодействий всех пользователей
     * с каждым из запрошенных мероприятий (используется как рейтинг).
     */
    public Stream<RecommendedEventProto> getInteractionsCount(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Stream.empty();
        }

        List<Object[]> results = interactionRepository.sumWeightsByEventIds(eventIds);

        return results.stream()
                .map(row -> RecommendedEventProto.newBuilder()
                        .setEventId(((Number) row[0]).longValue())
                        .setScore(((Number) row[1]).doubleValue())
                        .build());
    }
}
