package ru.practicum.collector.mapper;

import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.ewm.stats.message.ActionTypeProto;
import ru.practicum.ewm.stats.message.UserActionProto;

@Component
public class UserActionMapper {

    public UserActionAvro toAvro(UserActionProto proto) {
        return UserActionAvro.newBuilder()
                .setUserId(proto.getUserId())
                .setEventId(proto.getEventId())
                .setActionType(toAvroActionType(proto.getActionType()))
                .setTimestamp(toMillis(proto))
                .build();
    }

    private ActionTypeAvro toAvroActionType(ActionTypeProto actionType) {
        return switch (actionType) {
            case ACTION_VIEW -> ActionTypeAvro.VIEW;
            case ACTION_REGISTER -> ActionTypeAvro.REGISTER;
            case ACTION_LIKE -> ActionTypeAvro.LIKE;
            default -> throw new IllegalArgumentException("Unknown action type: " + actionType);
        };
    }

    private long toMillis(UserActionProto proto) {
        return proto.getTimestamp().getSeconds() * 1000L
                + proto.getTimestamp().getNanos() / 1_000_000L;
    }
}
