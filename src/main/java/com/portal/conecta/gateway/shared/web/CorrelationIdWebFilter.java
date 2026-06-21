package com.portal.conecta.gateway.shared.web;

import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Normalizes the correlation ID used by logs, responses, and downstream calls.
 */
@Component
public class CorrelationIdWebFilter implements WebFilter, Ordered {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_ATTRIBUTE = "correlationId";
    private static final int MAX_CORRELATION_ID_LENGTH = 128;
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("^[A-Za-z0-9._:-]+$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = resolveCorrelationId(exchange);
        exchange.getAttributes().put(CORRELATION_ID_ATTRIBUTE, correlationId);
        exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);

        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> headers.set(CORRELATION_ID_HEADER, correlationId))
                .build();

        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private String resolveCorrelationId(ServerWebExchange exchange) {
        String candidate = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);

        if (candidate == null || candidate.isBlank()) {
            return UUID.randomUUID().toString();
        }

        String trimmed = candidate.trim();
        if (trimmed.length() > MAX_CORRELATION_ID_LENGTH || !SAFE_CORRELATION_ID.matcher(trimmed).matches()) {
            return UUID.randomUUID().toString();
        }

        return trimmed;
    }
}
