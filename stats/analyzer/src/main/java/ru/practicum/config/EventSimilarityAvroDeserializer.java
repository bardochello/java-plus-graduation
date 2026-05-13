package ru.practicum.config;

import ru.practicum.AvroDeserializer;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;

public class EventSimilarityAvroDeserializer extends AvroDeserializer<EventSimilarityAvro> {
    public EventSimilarityAvroDeserializer() {
        super(EventSimilarityAvro.class);
    }
}
