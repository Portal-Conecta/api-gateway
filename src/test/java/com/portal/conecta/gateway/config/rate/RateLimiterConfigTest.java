package com.portal.conecta.gateway;

import java.security.Principal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterConfigTest {

    private final Object config = newConfig();

    @Test
    void resolvesJwtSubjectAsUserRateLimitKey() {
        KeyResolver resolver = userKeyResolver();
        ServerWebExchange exchange = authenticatedExchange(
                exchange(request("/checklist/api/checklist-templates").build()),
                jwtAuthentication("11111111-1111-1111-1111-111111111111")
        );

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("user:11111111-1111-1111-1111-111111111111");
    }

    @Test
    void fallsBackToForwardedIpWhenPrincipalIsMissing() {
        KeyResolver resolver = userKeyResolver();
        ServerWebExchange exchange = exchange(request("/hub/auth/login")
                .header("X-Forwarded-For", "203.0.113.10, 198.51.100.20")
                .build());

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("ip:203.0.113.10");
    }

    @Test
    void resolvesIpRateLimitKeyFromForwardedHeader() {
        KeyResolver resolver = ipKeyResolver();
        ServerWebExchange exchange = exchange(request("/hub/auth/login")
                .header("X-Forwarded-For", "203.0.113.20")
                .build());

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("ip:203.0.113.20");
    }

    private static Object newConfig() {
        try {
            return Class.forName("com.portal.conecta.gateway.config.rate.RateLimiterConfig")
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("RateLimiterConfig must be available", exception);
        }
    }

    private KeyResolver userKeyResolver() {
        return resolver("userKeyResolver");
    }

    private KeyResolver ipKeyResolver() {
        return resolver("ipKeyResolver");
    }

    private KeyResolver resolver(String methodName) {
        try {
            return (KeyResolver) config.getClass().getMethod(methodName).invoke(config);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(methodName + " must return a KeyResolver", exception);
        }
    }

    private static MockServerHttpRequest.BaseBuilder<?> request(String path) {
        return MockServerHttpRequest.get(path);
    }

    private static ServerWebExchange exchange(MockServerHttpRequest request) {
        return MockServerWebExchange.from(request);
    }

    private static Principal jwtAuthentication(String subject) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("sub", subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        return new JwtAuthenticationToken(jwt);
    }

    private static ServerWebExchange authenticatedExchange(ServerWebExchange exchange, Principal principal) {
        return new ServerWebExchangeDecorator(exchange) {
            @Override
            @SuppressWarnings("unchecked")
            public <T extends Principal> Mono<T> getPrincipal() {
                return Mono.just((T) principal);
            }
        };
    }
}
