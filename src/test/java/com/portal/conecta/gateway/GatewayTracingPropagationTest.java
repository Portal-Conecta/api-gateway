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
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre a propagacao de headers de tracing W3C (traceparent/tracestate) do
 * gateway para os servicos roteados, conforme exigido pela issue #5.
 *
 * <p>O sampling e forcado para 100% para garantir que o gateway sempre
 * produza um trace amostrado durante o teste, evitando flakiness por
 * decisao de sampling probabilistico.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000",
                "portal.gateway.security.enabled=false",
                "portal.gateway.rate-limit.enabled=false",
                "management.tracing.sampling.probability=1.0",
                "spring.cloud.gateway.server.webflux.httpclient.connect-timeout=2000",
                "spring.cloud.gateway.server.webflux.httpclient.response-timeout=2s"
        }
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GatewayTracingPropagationTest {

    private static final Pattern TRACEPARENT_PATTERN =
            Pattern.compile("^00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]$");
    private static final String CORRELATION_ID = "gateway-trace-check";

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


    @Test
    void forwardsIncomingTracestateToRoutedService() {
        String incomingTraceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        String incomingTracestate = "vendor1=value1";

        webTestClient.get()
                .uri("/hub/courses")
                .header("traceparent", incomingTraceparent)
                .header("tracestate", incomingTracestate)
                .header("X-Correlation-Id", CORRELATION_ID)
                .exchange()
                .expectStatus().isOk();

        RecordedRequest request = DOWNSTREAM.takeRequest();

        assertThat(request.traceparent())
                .as("traceparent continuado a partir do trace recebido do cliente")
                .isNotBlank()
                .matches(TRACEPARENT_PATTERN);
        assertThat(request.traceparent())
                .as("trace-id original e mantido ao continuar o trace")
                .contains("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(request.tracestate())
                .as("tracestate recebido na entrada e encaminhado ao servico roteado")
                .isEqualTo(incomingTracestate);
    }

    @Test
    void preservesCorrelationIdContractWhileTracingIsActive() {
        webTestClient.get()
                .uri("/hub/me")
                .header("X-Correlation-Id", CORRELATION_ID)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", CORRELATION_ID);

        RecordedRequest request = DOWNSTREAM.takeRequest();

        assertThat(request.correlationId()).isEqualTo(CORRELATION_ID);
    }

    private record RecordedRequest(String traceparent, String tracestate, String correlationId) {
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
            String traceparent = exchange.getRequestHeaders().getFirst("traceparent");
            String tracestate = exchange.getRequestHeaders().getFirst("tracestate");
            String correlationId = exchange.getRequestHeaders().getFirst("X-Correlation-Id");

            requests.add(new RecordedRequest(traceparent, tracestate, correlationId));

            byte[] body = """
                    {"status":"ok"}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }
    }
}