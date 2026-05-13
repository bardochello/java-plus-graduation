package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaMessageProducer implements MessageProducer {

    private final KafkaTemplate<String, UserActionAvro> kafkaTemplate;

    @Value("${collector.kafka.topic}")
    private String topic;

    @Override
    public void sendUserAction(UserActionAvro userActionAvro) {
        kafkaTemplate.send(topic, userActionAvro)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Ошибка отправки в Kafka: {}", ex.getMessage(), ex);
                    } else {
                        log.info("Отправлено в топик {}, offset={}",
                                topic, result.getRecordMetadata().offset());
                    }
                });
    }
}