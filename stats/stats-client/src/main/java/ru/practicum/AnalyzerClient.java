package ru.practicum;

import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.message.InteractionsCountRequestProto;
import ru.practicum.ewm.stats.message.RecommendedEventProto;
import ru.practicum.ewm.stats.message.SimilarEventsRequestProto;
import ru.practicum.ewm.stats.message.UserPredictionsRequestProto;
import ru.practicum.ewm.stats.service.dashboard.RecommendationsControllerGrpc;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * gRPC-клиент для получения рекомендаций из сервиса Analyzer.
 * Адрес сервиса разрешается через Eureka (discovery:///analyzer).
 */
@Slf4j
@Component
public class AnalyzerClient {

    @GrpcClient("analyzer")
    private RecommendationsControllerGrpc.RecommendationsControllerBlockingStub analyzerStub;

    /**
     * Возвращает поток рекомендованных мероприятий для пользователя.
     * Оценка (score) — предсказанный рейтинг.
     */
    public Stream<RecommendedEventProto> getRecommendationsForUser(long userId, int maxResults) {
        try {
            UserPredictionsRequestProto request = UserPredictionsRequestProto.newBuilder()
                    .setUserId(userId)
                    .setMaxResults(maxResults)
                    .build();
            Iterator<RecommendedEventProto> iterator = analyzerStub.getRecommendationsForUser(request);
            return toStream(iterator);
        } catch (Exception e) {
            log.warn("Failed to get recommendations for userId={}: {}", userId, e.getMessage());
            return Stream.empty();
        }
    }

    /**
     * Возвращает поток мероприятий, похожих на указанное,
     * с которыми пользователь ещё не взаимодействовал.
     * Оценка (score) — коэффициент сходства.
     */
    public Stream<RecommendedEventProto> getSimilarEvents(long eventId, long userId, int maxResults) {
        try {
            SimilarEventsRequestProto request = SimilarEventsRequestProto.newBuilder()
                    .setEventId(eventId)
                    .setUserId(userId)
                    .setMaxResults(maxResults)
                    .build();
            Iterator<RecommendedEventProto> iterator = analyzerStub.getSimilarEvents(request);
            return toStream(iterator);
        } catch (Exception e) {
            log.warn("Failed to get similar events for eventId={}: {}", eventId, e.getMessage());
            return Stream.empty();
        }
    }

    /**
     * Возвращает поток мероприятий с суммой максимальных весов взаимодействий (рейтинг).
     * Используется для заполнения поля rating в event-service.
     */
    public Stream<RecommendedEventProto> getInteractionsCount(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Stream.empty();
        }
        try {
            InteractionsCountRequestProto request = InteractionsCountRequestProto.newBuilder()
                    .addAllEventId(eventIds)
                    .build();
            Iterator<RecommendedEventProto> iterator = analyzerStub.getInteractionsCount(request);
            return toStream(iterator);
        } catch (Exception e) {
            log.warn("Failed to get interactions count for events {}: {}", eventIds, e.getMessage());
            return Stream.empty();
        }
    }

    /**
     * Преобразует Iterator от gRPC-стрима в Java Stream.
     */
    private Stream<RecommendedEventProto> toStream(Iterator<RecommendedEventProto> iterator) {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED),
                false
        );
    }
}
