package ru.practicum.aggregator.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.practicum.aggregator.service.SimilarityService;
import ru.practicum.ewm.stats.avro.UserActionAvro;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserActionHandler {

    private final SimilarityService similarityService;

    @KafkaListener(
            topics = "stats.user-actions.v1",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handle(UserActionAvro action) {
        log.info("Consumed user action: userId={}, eventId={}, type={}",
                action.getUserId(), action.getEventId(), action.getActionType());
        similarityService.processUserAction(action);
    }
}
