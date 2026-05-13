package ru.practicum;

import com.google.protobuf.Timestamp;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.message.ActionTypeProto;
import ru.practicum.ewm.stats.message.UserActionProto;
import ru.practicum.ewm.stats.service.collector.UserActionControllerGrpc;

import java.time.Instant;

/**
 * gRPC-клиент для отправки информации о действиях пользователей в сервис Collector.
 * Адрес сервиса разрешается через Eureka (discovery:///collector).
 */
@Slf4j
@Component
public class CollectorClient {

    @GrpcClient("collector")
    private UserActionControllerGrpc.UserActionControllerBlockingStub collectorStub;

    /**
     * Отправляет событие VIEW (просмотр мероприятия).
     */
    public void sendView(long userId, long eventId) {
        send(userId, eventId, ActionTypeProto.ACTION_VIEW);
    }

    /**
     * Отправляет событие REGISTER (регистрация на мероприятие).
     */
    public void sendRegister(long userId, long eventId) {
        send(userId, eventId, ActionTypeProto.ACTION_REGISTER);
    }

    /**
     * Отправляет событие LIKE (лайк мероприятия).
     */
    public void sendLike(long userId, long eventId) {
        send(userId, eventId, ActionTypeProto.ACTION_LIKE);
    }

    private void send(long userId, long eventId, ActionTypeProto actionType) {
        try {
            Instant now = Instant.now();
            UserActionProto request = UserActionProto.newBuilder()
                    .setUserId(userId)
                    .setEventId(eventId)
                    .setActionType(actionType)
                    .setTimestamp(Timestamp.newBuilder()
                            .setSeconds(now.getEpochSecond())
                            .setNanos(now.getNano())
                            .build())
                    .build();
            collectorStub.collectUserAction(request);
            log.debug("Sent {} for userId={}, eventId={}", actionType, userId, eventId);
        } catch (Exception e) {
            // Не роняем основную бизнес-логику из-за ошибки статистики
            log.warn("Failed to send {} to collector: userId={}, eventId={}: {}",
                    actionType, userId, eventId, e.getMessage());
        }
    }
}
