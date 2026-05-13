package ru.practicum.aggregator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Сервис инкрементального расчёта косинусного сходства мероприятий.
 * Формула: similarity(A, B) = S_min(A,B) / (S_A * S_B)
 * Веса: VIEW=0.4, REGISTER=0.8, LIKE=1.0
 */
@Slf4j
@Service
public class SimilarityService {

    private static final String SIMILARITY_TOPIC = "stats.events-similarity.v1";
    private static final double WEIGHT_VIEW     = 0.4;
    private static final double WEIGHT_REGISTER = 0.8;
    private static final double WEIGHT_LIKE     = 1.0;

    // eventId -> (userId -> maxWeight)
    private final Map<Long, Map<Long, Double>> userEventWeights = new HashMap<>();
    // eventId -> S_A (сумма весов всех пользователей для мероприятия)
    private final Map<Long, Double> eventWeightSums = new HashMap<>();
    // min(eventA,eventB) -> max(eventA,eventB) -> S_min
    private final Map<Long, Map<Long, Double>> minWeightSums = new HashMap<>();

    private final KafkaTemplate<String, EventSimilarityAvro> kafkaTemplate;

    public SimilarityService(KafkaTemplate<String, EventSimilarityAvro> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void processUserAction(UserActionAvro action) {
        long userId  = action.getUserId();
        long eventId = action.getEventId();
        double newWeight = toWeight(action.getActionType());

        // action.getTimestamp() возвращает Instant (логический тип timestamp-millis)
        Instant timestamp = action.getTimestamp();

        Map<Long, Double> usersForEvent = userEventWeights
                .computeIfAbsent(eventId, id -> new HashMap<>());
        double oldWeight = usersForEvent.getOrDefault(userId, 0.0);

        if (newWeight <= oldWeight) {
            log.debug("Action weight {} <= old weight {} for user={}, event={}. Skip.",
                    newWeight, oldWeight, userId, eventId);
            return;
        }

        usersForEvent.put(userId, newWeight);

        double deltaWeight = newWeight - oldWeight;
        double oldSA = eventWeightSums.getOrDefault(eventId, 0.0);
        double newSA = oldSA + deltaWeight;
        eventWeightSums.put(eventId, newSA);

        log.debug("Updated S_A for event={}: {} -> {} (delta={})", eventId, oldSA, newSA, deltaWeight);

        for (long otherEventId : userEventWeights.keySet()) {
            if (otherEventId == eventId) continue;

            Map<Long, Double> usersForOther = userEventWeights.get(otherEventId);
            double userWeightForOther = usersForOther.getOrDefault(userId, 0.0);

            if (userWeightForOther == 0.0) {
                double sMin = getMinWeightSum(eventId, otherEventId);
                if (sMin > 0) {
                    double sB = eventWeightSums.getOrDefault(otherEventId, 0.0);
                    recalculateAndPublish(eventId, otherEventId, sMin, newSA, sB, timestamp);
                }
                continue;
            }

            double oldMinContribution = Math.min(oldWeight, userWeightForOther);
            double newMinContribution = Math.min(newWeight, userWeightForOther);
            double deltaMin = newMinContribution - oldMinContribution;

            double oldSMin = getMinWeightSum(eventId, otherEventId);
            double newSMin = oldSMin + deltaMin;
            putMinWeightSum(eventId, otherEventId, newSMin);

            double sB = eventWeightSums.getOrDefault(otherEventId, 0.0);
            recalculateAndPublish(eventId, otherEventId, newSMin, newSA, sB, timestamp);
        }
    }

    private void recalculateAndPublish(long eventId, long otherEventId,
                                       double sMin, double sA, double sB,
                                       Instant timestamp) {
        if (sA <= 0 || sB <= 0) return;

        double similarity = sMin / (sA * sB);
        long eventA = Math.min(eventId, otherEventId);
        long eventB = Math.max(eventId, otherEventId);

        EventSimilarityAvro avro = EventSimilarityAvro.newBuilder()
                .setEventA(eventA)
                .setEventB(eventB)
                .setScore(similarity)
                // setTimestamp принимает Instant для timestamp-millis
                .setTimestamp(timestamp)
                .build();

        kafkaTemplate.send(SIMILARITY_TOPIC, eventA + "-" + eventB, avro);
        log.debug("Published similarity({},{}) = {}", eventA, eventB, similarity);
    }

    private double getMinWeightSum(long eventA, long eventB) {
        long first  = Math.min(eventA, eventB);
        long second = Math.max(eventA, eventB);
        return minWeightSums.computeIfAbsent(first, id -> new HashMap<>())
                .getOrDefault(second, 0.0);
    }

    private void putMinWeightSum(long eventA, long eventB, double value) {
        long first  = Math.min(eventA, eventB);
        long second = Math.max(eventA, eventB);
        minWeightSums.computeIfAbsent(first, id -> new HashMap<>()).put(second, value);
    }

    private double toWeight(ActionTypeAvro actionType) {
        return switch (actionType) {
            case VIEW     -> WEIGHT_VIEW;
            case REGISTER -> WEIGHT_REGISTER;
            case LIKE     -> WEIGHT_LIKE;
        };
    }
}
