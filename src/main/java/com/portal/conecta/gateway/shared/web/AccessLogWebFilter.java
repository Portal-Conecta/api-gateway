package com.portal.conecta.gateway.shared.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Emits one structured access log entry after each exchange completes.
 */
@Component
public class AccessLogWebFilter implements WebFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccessLogWebFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startedAt = System.currentTimeMillis();

        return chain.filter(exchange)
                .doFinally(signalType -> logRequest(exchange, startedAt));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private void logRequest(ServerWebExchange exchange, long startedAt) {
        long durationMs = System.currentTimeMillis() - startedAt;
        HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = route != null ? route.getId() : "unmatched";
        String correlationId = exchange.getAttributeOrDefault(
                CorrelationIdWebFilter.CORRELATION_ID_ATTRIBUTE,
                "unknown"
        );

        LOGGER.info(
                "gateway_request correlationId={} method={} path={} route={} status={} durationMs={}",
                correlationId,
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().value(),
                routeId,
                statusCode != null ? statusCode.value() : 0,
                durationMs
        );
    }
}
