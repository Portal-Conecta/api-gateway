package com.portal.conecta.gateway.config.rate;

import java.util.Objects;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Provides rate-limit keys compatible with the original gateway behavior.
 */
@Configuration
public class RateLimiterConfig {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";

    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .cast(JwtAuthenticationToken.class)
                .map(jwtAuth -> "user:" + resolveUserId(jwtAuth))
                .switchIfEmpty(Mono.defer(() -> Mono.just(ipKey(exchange))))
                .onErrorResume(exception -> Mono.just(ipKey(exchange)));
    }

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(ipKey(exchange));
    }

    private String resolveUserId(JwtAuthenticationToken jwtAuth) {
        String subject = jwtAuth.getToken().getSubject();
        if (subject != null && !subject.isBlank()) {
            return subject;
        }

        String legacyUserId = jwtAuth.getToken().getClaimAsString("user_id");
        return Objects.requireNonNullElseGet(legacyUserId, jwtAuth::getName);
    }

    private String ipKey(ServerWebExchange exchange) {
        return "ip:" + extractClientIp(exchange);
    }

    /**
     * Uses trusted proxy headers first because production traffic reaches the gateway
     * through infrastructure that terminates the original client connection.
     */
    private String extractClientIp(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst(X_FORWARDED_FOR);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = exchange.getRequest().getHeaders().getFirst(X_REAL_IP);
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        var remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }
}
