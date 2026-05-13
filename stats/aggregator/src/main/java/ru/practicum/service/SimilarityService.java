package ru.practicum.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class SimilarityService {
    private final Map<Long, Map<Long, Double>> weights = new HashMap<>();

    private final Map<Long, Double> eventWeightsSumOfSquares = new HashMap<>();

    private final MinWeightsMatrix minWeightsMatrix = new MinWeightsMatrix();

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
        Instant timestamp = Instant.ofEpochMilli(action.getTimestamp());

        Map<Long, Double> userMap = weights.computeIfAbsent(eventId, e -> new HashMap<>());
        double oldWeight = userMap.getOrDefault(userId, 0.0);

        if (newWeight <= oldWeight) {
            return;
        }

        userMap.put(userId, newWeight);

        double oldSumSq = eventWeightsSumOfSquares.getOrDefault(eventId, 0.0);
        double newSumSq = oldSumSq - (oldWeight * oldWeight) + (newWeight * newWeight);
        eventWeightsSumOfSquares.put(eventId, newSumSq);

        weights.keySet().stream()
                .filter(otherEvent -> !otherEvent.equals(eventId))
                .forEach(otherEvent -> updatePairSimilarity(eventId, otherEvent, timestamp));
    }

    private void updatePairSimilarity(long eventA, long eventB, Instant timestamp) {
        double sMin = calcSMin(eventA, eventB);
        minWeightsMatrix.put(eventA, eventB, sMin);

        if (sMin == 0) {
            return;
        }

        double sA = eventWeightsSumOfSquares.getOrDefault(eventA, 0.0);
        double sB = eventWeightsSumOfSquares.getOrDefault(eventB, 0.0);
        if (sA == 0 || sB == 0) {
            return;
        }

        float similarity = (float) (sMin / (Math.sqrt(sA) * Math.sqrt(sB)));

        long first  = Math.min(eventA, eventB);
        long second = Math.max(eventA, eventB);

        EventSimilarityAvro msg = EventSimilarityAvro.newBuilder()
                .setEventA(first)
                .setEventB(second)
                .setScore(similarity)
                .setTimestamp(timestamp)
                .build();

        kafkaTemplate.send(similarityTopic, msg);
        log.debug("Similarity (A={}, B={}) = {}", first, second, similarity);
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
            default       -> 0.4; // VIEW
        };
    }
}