package ru.practicum.analyzer.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.practicum.analyzer.model.EventSimilarity;
import ru.practicum.analyzer.repository.EventSimilarityRepository;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventSimilarityKafkaHandler {

    private final EventSimilarityRepository similarityRepository;

    @KafkaListener(
            topics = "stats.events-similarity.v1",
            containerFactory = "eventSimilarityListenerContainerFactory"
    )
    public void handle(EventSimilarityAvro avro) {
        long eventA = avro.getEventA();
        long eventB = avro.getEventB();

        log.info("Received similarity: eventA={}, eventB={}, score={}", eventA, eventB, avro.getScore());

        // avro.getTimestamp() возвращает Instant — передаём напрямую в модель (поле Instant)
        similarityRepository.findByEventPair(eventA, eventB)
                .ifPresentOrElse(
                        existing -> {
                            existing.setScore(avro.getScore());
                            existing.setTimestamp(avro.getTimestamp());
                            similarityRepository.save(existing);
                        },
                        () -> {
                            EventSimilarity similarity = EventSimilarity.builder()
                                    .eventA(eventA)
                                    .eventB(eventB)
                                    .score(avro.getScore())
                                    .timestamp(avro.getTimestamp())
                                    .build();
                            similarityRepository.save(similarity);
                        }
                );
    }
}
