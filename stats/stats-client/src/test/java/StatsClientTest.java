import dto.EndpointHitDto;
import dto.ViewStatsDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import ru.practicum.StatsClient;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StatsClientTest {

    private MockWebServer mockWebServer;
    private StatsClient statsClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void beforeEach() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // Создаём StatsClient с RestTemplate,指向 mock сервер
        RestTemplate restTemplate = new RestTemplate();
        statsClient = new StatsClient(null, restTemplate); // DiscoveryClient не нужен для теста
    }

    @Test
    void testAddHit() throws IOException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(201));

        EndpointHitDto hit = EndpointHitDto.builder()
                .app("ewm-main-service")
                .uri("/events")
                .ip("127.0.0.1")
                .timestamp(LocalDateTime.now())
                .build();

        assertDoesNotThrow(() -> statsClient.addHit(hit));
    }

    @Test
    void testGetStats() throws IOException {
        List<ViewStatsDto> expected = List.of(
                ViewStatsDto.builder().app("ewm-main-service").uri("/events/1").hits(50L).build(),
                ViewStatsDto.builder().app("ewm-main-service").uri("/events/100").hits(200L).build()
        );

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(mapper.writeValueAsString(expected))
                .addHeader("Content-Type", "application/json"));

        List<ViewStatsDto> result = statsClient.getStats(
                "2024-01-01 00:00:00",
                "2024-12-31 23:59:59",
                List.of("/events/1", "/events/100"),
                true
        );

        assertEquals(2, result.size());
        assertEquals("ewm-main-service", result.get(0).getApp());
        assertEquals(50L, result.get(0).getHits());
    }

    @AfterEach
    void afterEach() throws IOException {
        mockWebServer.shutdown();
    }
}