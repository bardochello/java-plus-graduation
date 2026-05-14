package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.stats.proto.RecommendationsMessages;
import ru.practicum.entity.EventSimilarity;
import ru.practicum.entity.RecommendedEvent;
import ru.practicum.entity.UserAction;
import ru.practicum.repository.EventSimilarityRepository;
import ru.practicum.repository.UserActionRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final UserActionRepository userActionRepo;
    private final EventSimilarityRepository similarityRepo;

    public List<RecommendedEvent> getSimilarEvents(RecommendationsMessages.SimilarEventsRequestProto request) {
        long eventId = request.getEventId();
        long userId  = request.getUserId();
        int maxRes   = request.getMaxResults();

        Set<Long> interacted = userInteracted(userId);
        List<RecommendedEvent> result, recList = new ArrayList<>();

        similarityRepo.findByEventAOrEventB(eventId, eventId)
                .forEach(e -> {
                    long other = (e.getEventA().equals(eventId)) ? e.getEventB() : e.getEventA();
                    if (!interacted.contains(other)) {
                        recList.add(new RecommendedEvent(other, e.getScore()));
                    }
                });
        result = recList.stream()
                .sorted(Comparator.comparingDouble(RecommendedEvent::score).reversed()).toList();

        return result.size() <= maxRes ? result : result.subList(0, maxRes);
    }

    public List<RecommendedEvent> getRecommendationsForUser(RecommendationsMessages.UserPredictionsRequestProto request) {
        long userId = request.getUserId();
        int maxRes  = request.getMaxResults();

        // Шаг 1: Получить все взаимодействия пользователя, отсортировать по дате, взять последние N
        List<UserAction> all = userActionRepo.findByUserId(userId);
        if (all.isEmpty()) {
            return Collections.emptyList();
        }

        all.sort((a, b) -> b.getLastInteraction().compareTo(a.getLastInteraction()));

        int recentCount = Math.min(maxRes, all.size());
        List<UserAction> recent = all.subList(0, recentCount);

        Set<Long> interacted = userInteracted(userId);

        // Шаг 2: Найти мероприятия, похожие на недавно просмотренные, с которыми пользователь ещё не взаимодействовал
        // Собираем: кандидат -> список пар (схожесть, оценка_пользователя_для_соседа)
        // Для вычисления взвешенного среднего KNN: score = Σ(sim_i * userRating_i) / Σ(sim_i)
        Map<Long, Double> weightedScoreSum = new HashMap<>();
        Map<Long, Double> simSum = new HashMap<>();

        for (UserAction viewed : recent) {
            long viewedEventId = viewed.getEventId();
            double userRating = viewed.getMaxWeight(); // оценка пользователя для этого мероприятия

            List<EventSimilarity> simList = similarityRepo.findByEventAOrEventB(viewedEventId, viewedEventId);
            for (EventSimilarity sim : simList) {
                long candidateId = (sim.getEventA().equals(viewedEventId)) ? sim.getEventB() : sim.getEventA();
                if (interacted.contains(candidateId)) {
                    continue; // пропускаем уже просмотренные
                }
                double similarity = sim.getScore();

                // Накапливаем для взвешенного среднего
                weightedScoreSum.merge(candidateId, similarity * userRating, Double::sum);
                simSum.merge(candidateId, similarity, Double::sum);
            }
        }

        // Шаг 3: Вычисляем предсказанную оценку = взвешенная сумма / сумма схожестей
        return weightedScoreSum.entrySet().stream()
                .filter(e -> simSum.getOrDefault(e.getKey(), 0.0) > 0)
                .map(e -> {
                    double predictedScore = e.getValue() / simSum.get(e.getKey());
                    return new RecommendedEvent(e.getKey(), predictedScore);
                })
                .sorted(Comparator.comparingDouble(RecommendedEvent::score).reversed())
                .limit(maxRes)
                .collect(Collectors.toList());
    }

    public List<RecommendedEvent> getInteractionsCount(RecommendationsMessages.InteractionsCountRequestProto request) {
        List<Long> events = request.getEventIdList();
        List<RecommendedEvent> result = new ArrayList<>();

        for (Long e : events) {
            List<UserAction> list = userActionRepo.findByEventId(e);
            double sum = 0.0;
            for (UserAction uae : list) {
                sum += uae.getMaxWeight();
            }
            result.add(new RecommendedEvent(e, sum));
        }
        return result;
    }

    private Set<Long> userInteracted(long userId) {
        return userActionRepo.findByUserId(userId)
                .stream()
                .map(UserAction::getEventId)
                .collect(Collectors.toSet());
    }
}