package ru.practicum.analyzer.controller;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.practicum.analyzer.service.RecommendationService;
import ru.practicum.ewm.stats.message.InteractionsCountRequestProto;
import ru.practicum.ewm.stats.message.RecommendedEventProto;
import ru.practicum.ewm.stats.message.SimilarEventsRequestProto;
import ru.practicum.ewm.stats.message.UserPredictionsRequestProto;
import ru.practicum.ewm.stats.service.dashboard.RecommendationsControllerGrpc;

import java.util.stream.Stream;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class RecommendationsControllerImpl
        extends RecommendationsControllerGrpc.RecommendationsControllerImplBase {

    private final RecommendationService recommendationService;

    /**
     * Возвращает стрим рекомендованных мероприятий для пользователя.
     * Оценка (score) = предсказанная оценка по item-based CF.
     */
    @Override
    public void getRecommendationsForUser(UserPredictionsRequestProto request,
                                          StreamObserver<RecommendedEventProto> responseObserver) {
        long userId     = request.getUserId();
        int  maxResults = request.getMaxResults();
        log.info("GetRecommendationsForUser: userId={}, maxResults={}", userId, maxResults);

        try {
            Stream<RecommendedEventProto> stream =
                    recommendationService.getRecommendationsForUser(userId, maxResults);
            streamToObserver(stream, responseObserver);
        } catch (Exception e) {
            log.error("Error in GetRecommendationsForUser for userId={}: {}", userId, e.getMessage(), e);
            responseObserver.onError(e);
        }
    }

    /**
     * Возвращает стрим мероприятий, похожих на заданное,
     * с которыми пользователь ещё не взаимодействовал.
     * Оценка (score) = коэффициент сходства.
     */
    @Override
    public void getSimilarEvents(SimilarEventsRequestProto request,
                                 StreamObserver<RecommendedEventProto> responseObserver) {
        long eventId    = request.getEventId();
        long userId     = request.getUserId();
        int  maxResults = request.getMaxResults();
        log.info("GetSimilarEvents: eventId={}, userId={}, maxResults={}", eventId, userId, maxResults);

        try {
            Stream<RecommendedEventProto> stream =
                    recommendationService.getSimilarEvents(eventId, userId, maxResults);
            streamToObserver(stream, responseObserver);
        } catch (Exception e) {
            log.error("Error in GetSimilarEvents for eventId={}: {}", eventId, e.getMessage(), e);
            responseObserver.onError(e);
        }
    }

    /**
     * Возвращает стрим мероприятий с суммой весов взаимодействий пользователей.
     * Оценка (score) = сумма максимальных весов = рейтинг мероприятия.
     */
    @Override
    public void getInteractionsCount(InteractionsCountRequestProto request,
                                     StreamObserver<RecommendedEventProto> responseObserver) {
        log.info("GetInteractionsCount: {} eventIds", request.getEventIdCount());

        try {
            Stream<RecommendedEventProto> stream =
                    recommendationService.getInteractionsCount(request.getEventIdList());
            streamToObserver(stream, responseObserver);
        } catch (Exception e) {
            log.error("Error in GetInteractionsCount: {}", e.getMessage(), e);
            responseObserver.onError(e);
        }
    }

    /**
     * Отправляет все элементы стрима в gRPC StreamObserver и завершает его.
     */
    private void streamToObserver(Stream<RecommendedEventProto> stream,
                                  StreamObserver<RecommendedEventProto> observer) {
        try (stream) {
            stream.forEach(observer::onNext);
            observer.onCompleted();
        }
    }
}
