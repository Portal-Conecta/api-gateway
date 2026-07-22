package com.portal.conecta.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
                "portal.gateway.security.enabled=true",
                "portal.gateway.security.jwt-secret=" + GatewaySecurityTest.JWT_SECRET,
                "portal.gateway.rate-limit.enabled=false",
                "spring.cloud.gateway.server.webflux.httpclient.connect-timeout=2000",
                "spring.cloud.gateway.server.webflux.httpclient.response-timeout=2s"
        }
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GatewaySecurityTest {

    static final String JWT_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVm";

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
        DOWNSTREAM.clear();
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + localServerPort)
                .build();
    }

    @Test
    void allowsAuthenticationRoutesWithoutJwt() {
        webTestClient.post()
                .uri("/auth/login")
                .header("X-Correlation-Id", "portal-auth-public")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", "portal-auth-public")
                .expectBody()
                .jsonPath("$.path").isEqualTo("/auth/login");

        assertThat(DOWNSTREAM.takeRequest().path()).isEqualTo("/auth/login");
    }

    @Test
    void allowsRefreshRouteWithoutJwt() {
        webTestClient.post()
                .uri("/auth/refresh")
                .header("X-Correlation-Id", "portal-auth-refresh")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", "portal-auth-refresh")
                .expectBody()
                .jsonPath("$.path").isEqualTo("/auth/refresh");

        assertThat(DOWNSTREAM.takeRequest().path()).isEqualTo("/auth/refresh");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/hub/v3/api-docs",
            "/hub/v3/api-docs/swagger-config",
            "/hub/swagger-ui.html",
            "/hub/swagger-ui/index.html"
    })
    void allowsHubDocumentationRoutesWithoutJwt(String path) {
        webTestClient.get()
                .uri(path)
                .exchange()
                .expectStatus().isOk();

        RecordedRequest request = DOWNSTREAM.takeRequest();

        assertThat(request.path()).isEqualTo(path.replaceFirst("^/hub", ""));
    }

    @Test
    void rejectsLegacyHubAuthenticationPathWhenJwtIsMissing() {
        webTestClient.post()
                .uri("/hub/auth/login")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("Unauthorized")
                .jsonPath("$.path").isEqualTo("/hub/auth/login");

        assertThat(DOWNSTREAM.pollRequest()).isNull();
    }

    @Test
    void rejectsProtectedRoutesWhenJwtIsMissing() {
        webTestClient.get()
                .uri("/checklist/api/checklist-templates")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("Unauthorized")
                .jsonPath("$.path").isEqualTo("/checklist/api/checklist-templates");

        assertThat(DOWNSTREAM.pollRequest()).isNull();
    }

    @Test
    void rejectsHubRoutesWhenJwtIsMissing() {
        webTestClient.get()
                .uri("/hub/users")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("Unauthorized")
                .jsonPath("$.path").isEqualTo("/hub/users");

        assertThat(DOWNSTREAM.pollRequest()).isNull();
    }

    @Test
    void forwardsProtectedRoutesWhenJwtIsValid() {
        String token = createToken("11111111-1111-1111-1111-111111111111");

        webTestClient.get()
                .uri("/checklist/api/checklist-templates")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/checklist-templates")
                .jsonPath("$.authorization").isEqualTo("Bearer " + token);

        RecordedRequest request = DOWNSTREAM.takeRequest();

        assertThat(request.path()).isEqualTo("/api/checklist-templates");
        assertThat(request.authorization()).isEqualTo("Bearer " + token);
    }

    @Test
    void forwardsHubRoutesWhenJwtIsValid() {
        String token = createToken("22222222-2222-2222-2222-222222222222");

        webTestClient.get()
                .uri("/hub/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/users")
                .jsonPath("$.authorization").isEqualTo("Bearer " + token);

        RecordedRequest request = DOWNSTREAM.takeRequest();

        assertThat(request.path()).isEqualTo("/users");
        assertThat(request.authorization()).isEqualTo("Bearer " + token);
    }

    @Test
    void rejectsProtectedRoutesWhenJwtIsInvalid() {
        webTestClient.get()
                .uri("/mapa/api/mapas")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("Unauthorized")
                .jsonPath("$.path").isEqualTo("/mapa/api/mapas");

        assertThat(DOWNSTREAM.pollRequest()).isNull();
    }

    private static String createToken(String subject) {
        try {
            long now = Instant.now().getEpochSecond();
            String header = "{\"alg\":\"HS384\",\"typ\":\"JWT\"}";
            String payload = """
                    {"sub":"%s","iat":%d,"exp":%d}
                    """.formatted(subject, now, now + 900);
            String unsignedToken = base64Url(header) + "." + base64Url(payload);

            Mac mac = Mac.getInstance("HmacSHA384");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(JWT_SECRET), "HmacSHA384"));
            String signature = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8)));

            return unsignedToken + "." + signature;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create test JWT", exception);
        }
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private record RecordedRequest(String path, String authorization) {
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

        RecordedRequest pollRequest() {
            try {
                return requests.poll(300, TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while checking downstream request", exception);
            }
        }

        void clear() {
            requests.clear();
        }

        void stop() {
            server.stop(0);
        }

        private void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getRawPath();
            String authorization = exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            requests.add(new RecordedRequest(path, authorization));

            byte[] body = """
                    {"path":"%s","authorization":"%s"}
                    """.formatted(path, authorization).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }
    }
}
