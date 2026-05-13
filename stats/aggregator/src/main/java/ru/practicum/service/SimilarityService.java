package ru.practicum.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ru.practicum.config.KafkaProperties;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class SimilarityService {

    private final Map<Long, Map<Long, Integer>> weights = new HashMap<>();

    private final Map<Long, Integer> eventWeightsSum = new HashMap<>();

    private final KafkaTemplate<String, EventSimilarityAvro> kafkaTemplate;
    private final KafkaProperties props;

    public SimilarityService(KafkaTemplate<String, EventSimilarityAvro> kafkaTemplate,
                             KafkaProperties props) {
        this.kafkaTemplate = kafkaTemplate;
        this.props = props;
    }

    public void processUserAction(UserActionAvro action) {
        long userId  = action.getUserId();
        long eventId = action.getEventId();
        int newWeight = convertActionType(action.getActionType());
        Instant timestamp = Instant.ofEpochMilli(action.getTimestamp());

        Map<Long, Integer> userMap = weights.computeIfAbsent(eventId, e -> new HashMap<>());
        int oldWeight = userMap.getOrDefault(userId, 0);

        if (newWeight <= oldWeight) {
            return;
        }

        userMap.put(userId, newWeight);

        int diff = newWeight - oldWeight;
        eventWeightsSum.merge(eventId, diff, Integer::sum);

        weights.keySet().stream()
                .filter(otherEvent -> !otherEvent.equals(eventId))
                .forEach(otherEvent -> updatePairSimilarity(eventId, otherEvent, timestamp));
    }

    private void updatePairSimilarity(long eventA, long eventB, Instant timestamp) {
        double sMin = calcSMin(eventA, eventB);

        if (sMin == 0) {
            return;
        }

        double sA = eventWeightsSum.getOrDefault(eventA, 0);
        double sB = eventWeightsSum.getOrDefault(eventB, 0);
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

        kafkaTemplate.send(props.getProducer().getTopic(), first + "-" + second, msg);
        log.debug("Similarity (A={}, B={}) = {}", first, second, similarity);
    }

    private double calcSMin(long eventA, long eventB) {
        Map<Long, Integer> userMapA = weights.getOrDefault(eventA, Map.of());
        Map<Long, Integer> userMapB = weights.getOrDefault(eventB, Map.of());

        return userMapA.entrySet().stream()
                .filter(e -> userMapB.containsKey(e.getKey()))
                .mapToDouble(e -> Math.min(e.getValue(), userMapB.get(e.getKey())))
                .sum();
    }

    private int convertActionType(ActionTypeAvro actionType) {
        return switch (actionType) {
            case REGISTER -> 2;
            case LIKE     -> 3;
            default       -> 1; // VIEW
        };
    }
}
