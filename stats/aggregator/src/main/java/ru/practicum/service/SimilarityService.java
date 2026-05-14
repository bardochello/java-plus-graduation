package ru.practicum.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class SimilarityService {

    // Map<eventId, Map<userId, weight>> — веса пользователей по мероприятиям
    private final Map<Long, Map<Long, Double>> weights = new HashMap<>();
    // Map<eventId, totalWeightSum> — сумма весов всех пользователей для мероприятия
    private final Map<Long, Double> eventWeightsSum = new HashMap<>();
    // Map<userId, Set<eventId>> — какие мероприятия посещал каждый пользователь
    private final Map<Long, Set<Long>> userEvents = new HashMap<>();
    // Матрица последних отправленных значений схожести (для фильтрации дубликатов)
    private final MinWeightsMatrix lastSentSimilarity = new MinWeightsMatrix();

    private final KafkaTemplate<String, EventSimilarityAvro> kafkaTemplate;

    @Value("${kafka.producer.topic}")
    private String similarityTopic;

    public SimilarityService(KafkaTemplate<String, EventSimilarityAvro> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void processUserAction(UserActionAvro action) {
        long userId  = action.getUserId();
        long eventId = action.getEventId();
        double newWeight = convertActionType(action.getActionType());
        // timestamp_ms без javaTimeConversions генерирует long (epoch millis)
        long timestamp = action.getTimestamp();

        Map<Long, Double> userMap = weights.computeIfAbsent(eventId, e -> new HashMap<>());
        double oldWeight = userMap.getOrDefault(userId, 0.0);

        // Пропускаем, если вес не увеличился
        if (newWeight <= oldWeight) {
            return;
        }

        userMap.put(userId, newWeight);

        double diff = newWeight - oldWeight;
        eventWeightsSum.merge(eventId, diff, Double::sum);

        Set<Long> eventsForUser = userEvents.computeIfAbsent(userId, u -> new HashSet<>());

        // Пересчитываем схожесть только для пар (eventId, otherEvent),
        // где текущий пользователь взаимодействовал с ОБОИМИ мероприятиями
        for (Long otherEvent : eventsForUser) {
            if (!otherEvent.equals(eventId)) {
                updatePairSimilarity(eventId, otherEvent, timestamp);
            }
        }

        // Регистрируем текущее мероприятие в истории пользователя ПОСЛЕ расчёта
        eventsForUser.add(eventId);
    }

    private void updatePairSimilarity(long eventA, long eventB, long timestamp) {
        double sMin = calcSMin(eventA, eventB);

        double sA = eventWeightsSum.getOrDefault(eventA, 0.0);
        double sB = eventWeightsSum.getOrDefault(eventB, 0.0);

        if (sMin == 0 || sA == 0 || sB == 0) {
            return;
        }

        float newSimilarity = (float) (sMin / (Math.sqrt(sA) * Math.sqrt(sB)));

        long first  = Math.min(eventA, eventB);
        long second = Math.max(eventA, eventB);

        // Отправляем только если схожесть изменилась
        double previousSimilarity = lastSentSimilarity.get(first, second);
        if (Math.abs(newSimilarity - previousSimilarity) < 1e-9) {
            return;
        }

        lastSentSimilarity.put(first, second, newSimilarity);

        EventSimilarityAvro msg = EventSimilarityAvro.newBuilder()
                .setEventA(first)
                .setEventB(second)
                .setScore(newSimilarity)
                .setTimestamp(timestamp)
                .build();

        kafkaTemplate.send(similarityTopic, msg);
        log.debug("Схожесть изменилась (A={}, B={}) = {} (было {})", first, second, newSimilarity, previousSimilarity);
    }

    private double calcSMin(long eventA, long eventB) {
        Map<Long, Double> userMapA = weights.getOrDefault(eventA, Map.of());
        Map<Long, Double> userMapB = weights.getOrDefault(eventB, Map.of());

        return userMapA.entrySet().stream()
                .filter(e -> userMapB.containsKey(e.getKey()))
                .mapToDouble(e -> Math.min(e.getValue(), userMapB.get(e.getKey())))
                .sum();
    }

    private double convertActionType(ActionTypeAvro actionType) {
        return switch (actionType) {
            case REGISTER -> 0.8;
            case LIKE     -> 1.0;
            default       -> 0.4;
        };
    }
}