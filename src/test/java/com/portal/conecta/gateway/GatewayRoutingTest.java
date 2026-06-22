package com.portal.conecta.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000",
                "portal.gateway.security.enabled=false",
                "portal.gateway.rate-limit.enabled=false",
                "spring.cloud.gateway.server.webflux.httpclient.connect-timeout=2000",
                "spring.cloud.gateway.server.webflux.httpclient.response-timeout=2s"
        }
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GatewayRoutingTest {

    private static final String AUTHORIZATION = "Bearer sample-token";
    private static final String CORRELATION_ID = "portal-test-correlation-id";
    private static final StubDownstreamServer DOWNSTREAM = StubDownstreamServer.start();

    @Value("${local.server.port}")
    private int localServerPort;

    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("HUB_SERVICE_URL", DOWNSTREAM::baseUrl);
        registry.add("CHECKLIST_SERVICE_URL", DOWNSTREAM::baseUrl);
        registry.add("MAPA_SERVICE_URL", DOWNSTREAM::baseUrl);
        registry.add("COMUNICADOS_SERVICE_URL", DOWNSTREAM::baseUrl);
    }

    @AfterAll
    void stopDownstream() {
        DOWNSTREAM.stop();
    }

    @BeforeEach
    void setUpClient() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + localServerPort)
                .build();
    }

    @ParameterizedTest
    @MethodSource("routes")
    void routesRequestsToConfiguredServicesAndStripsExternalPrefix(String externalPath, String expectedPath) {
        webTestClient.get()
                .uri(externalPath)
                .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                .header("X-Correlation-Id", CORRELATION_ID)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", CORRELATION_ID)
                .expectBody()
                .jsonPath("$.path").isEqualTo(expectedPath)
                .jsonPath("$.authorization").isEqualTo(AUTHORIZATION)
                .jsonPath("$.correlationId").isEqualTo(CORRELATION_ID);

        RecordedRequest request = DOWNSTREAM.takeRequest();

        assertThat(request.path()).isEqualTo(expectedPath);
        assertThat(request.authorization()).isEqualTo(AUTHORIZATION);
        assertThat(request.correlationId()).isEqualTo(CORRELATION_ID);
    }

    @Test
    void createsCorrelationIdWhenHeaderIsMissing() {
        webTestClient.get()
                .uri("/hub/users")
                .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Correlation-Id")
                .expectBody()
                .jsonPath("$.correlationId").isNotEmpty();

        RecordedRequest request = DOWNSTREAM.takeRequest();

        assertThat(request.correlationId()).isNotBlank();
    }

    @Test
    void appliesCorsFromAllowedOriginsEnvironmentVariable() {
        webTestClient.options()
                .uri("/hub/users")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,X-Correlation-Id")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173")
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, value -> {
                    assertThat(value).contains("Authorization");
                    assertThat(value).contains("X-Correlation-Id");
                });
    }

    @Test
    void returnsApiErrorWhenRouteDoesNotExist() {
        webTestClient.get()
                .uri("/unknown")
                .header("X-Correlation-Id", CORRELATION_ID)
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().valueEquals("X-Correlation-Id", CORRELATION_ID)
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.error").isEqualTo("Not Found")
                .jsonPath("$.path").isEqualTo("/unknown");
    }

    private static Stream<Arguments> routes() {
        return Stream.of(
                Arguments.of("/auth/login", "/auth/login"),
                Arguments.of("/auth/refresh", "/auth/refresh"),
                Arguments.of("/hub/users", "/users"),
                Arguments.of("/hub/me/courses", "/me/courses"),
                Arguments.of("/hub/api/v1/notifications", "/api/v1/notifications"),
                Arguments.of("/checklist/api/checklist-templates", "/api/checklist-templates"),
                Arguments.of("/mapa/api/mapas", "/api/mapas"),
                Arguments.of("/comunicados/api/posts", "/api/posts")
        );
    }

    private record RecordedRequest(String path, String authorization, String correlationId) {
    }

    private static final class StubDownstreamServer {

        private final HttpServer server;
        private final BlockingQueue<RecordedRequest> requests = new LinkedBlockingQueue<>();

        private StubDownstreamServer(HttpServer server) {
            this.server = server;
        }

        static StubDownstreamServer start() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
                StubDownstreamServer stub = new StubDownstreamServer(server);
                server.createContext("/", stub::handle);
                server.start();
                return stub;
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        String baseUrl() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        RecordedRequest takeRequest() {
            try {
                RecordedRequest request = requests.poll(5, TimeUnit.SECONDS);
                assertThat(request).as("downstream request").isNotNull();
                return request;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for downstream request", exception);
            }
        }

        void stop() {
            server.stop(0);
        }

        private void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getRawPath();
            String authorization = exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            String correlationId = exchange.getRequestHeaders().getFirst("X-Correlation-Id");

            requests.add(new RecordedRequest(path, authorization, correlationId));

            byte[] body = """
                    {"path":"%s","authorization":"%s","correlationId":"%s"}
                    """.formatted(path, authorization, correlationId).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }
    }
}
