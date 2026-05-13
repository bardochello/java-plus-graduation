package ru.practicum.aggregator.config;

import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.kafka.common.serialization.Deserializer;
import ru.practicum.ewm.stats.avro.UserActionAvro;

public class UserActionAvroDeserializer implements Deserializer<UserActionAvro> {

    private final DatumReader<UserActionAvro> reader = new SpecificDatumReader<>(UserActionAvro.class);

    @Override
    public UserActionAvro deserialize(String topic, byte[] data) {
        if (data == null) return null;
        try {
            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(data, null);
            return reader.read(null, decoder);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize UserActionAvro", e);
        }
    }
}
