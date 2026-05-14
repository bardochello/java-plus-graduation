package ru.practicum.config;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;

import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaConfig {
    private final KafkaProperties props;

    @Bean
    public ConsumerFactory<String, UserActionAvro> userActionsConsumerFactory() {
        final Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, props.getBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, props.getUserActionsConsumer().getGroupId());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, props.getUserActionsConsumer().getKeyDeserializer());
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, props.getUserActionsConsumer().getValueDeserializer());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserActionAvro> userActionsKafkaListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, UserActionAvro> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(userActionsConsumerFactory());
        return factory;
    }

    @Bean
    public ConsumerFactory<String, EventSimilarityAvro> eventSimilarityConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, props.getBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, props.getEventsSimilarityConsumer().getGroupId());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, getClassFromString(props.getEventsSimilarityConsumer().getKeyDeserializer()));
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, getClassFromString(props.getEventsSimilarityConsumer().getValueDeserializer()));
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventSimilarityAvro> eventSimilarityKafkaListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, EventSimilarityAvro> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(eventSimilarityConsumerFactory());
        return factory;
    }

    private Class<?> getClassFromString(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Unable to load class: " + className, e);
        }
    }
}