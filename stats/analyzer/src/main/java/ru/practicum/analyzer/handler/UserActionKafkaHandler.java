package ru.practicum.analyzer.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.practicum.analyzer.model.UserEventInteraction;
import ru.practicum.analyzer.repository.UserEventInteractionRepository;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserActionKafkaHandler {

    private static final double WEIGHT_VIEW     = 0.4;
    private static final double WEIGHT_REGISTER = 0.8;
    private static final double WEIGHT_LIKE     = 1.0;

    private final UserEventInteractionRepository interactionRepository;

    @KafkaListener(
            topics = "stats.user-actions.v1",
            containerFactory = "userActionListenerContainerFactory"
    )
    public void handle(UserActionAvro action) {
        long userId  = action.getUserId();
        long eventId = action.getEventId();
        double newWeight = toWeight(action.getActionType());

        // action.getTimestamp() возвращает Instant — передаём напрямую в модель (поле Instant)
        Instant timestamp = action.getTimestamp();

        log.info("Received user action: userId={}, eventId={}, weight={}", userId, eventId, newWeight);

        interactionRepository.findByUserIdAndEventId(userId, eventId)
                .ifPresentOrElse(
                        existing -> {
                            if (newWeight > existing.getMaxWeight()) {
                                existing.setMaxWeight(newWeight);
                                existing.setTimestamp(timestamp);
                                interactionRepository.save(existing);
                                log.debug("Updated interaction: userId={}, eventId={}, weight={}->{}",
                                        userId, eventId, existing.getMaxWeight(), newWeight);
                            }
                        },
                        () -> {
                            UserEventInteraction interaction = UserEventInteraction.builder()
                                    .userId(userId)
                                    .eventId(eventId)
                                    .maxWeight(newWeight)
                                    .timestamp(timestamp)
                                    .build();
                            interactionRepository.save(interaction);
                            log.debug("Saved new interaction: userId={}, eventId={}, weight={}",
                                    userId, eventId, newWeight);
                        }
                );
    }

    private double toWeight(ActionTypeAvro actionType) {
        return switch (actionType) {
            case VIEW     -> WEIGHT_VIEW;
            case REGISTER -> WEIGHT_REGISTER;
            case LIKE     -> WEIGHT_LIKE;
        };
    }
}
