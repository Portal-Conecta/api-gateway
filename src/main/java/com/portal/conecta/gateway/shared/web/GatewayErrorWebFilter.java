package com.portal.conecta.gateway.shared.web;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Converts gateway-originated failures to the Portal Conecta API error shape.
 */
@Component
public class GatewayErrorWebFilter implements WebFilter, Ordered {

    private final ApiErrorResponseWriter apiErrorResponseWriter;

    public GatewayErrorWebFilter(ApiErrorResponseWriter apiErrorResponseWriter) {
        this.apiErrorResponseWriter = apiErrorResponseWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange)
                .then(Mono.defer(() -> writeNotFoundWhenGatewayDidNotRoute(exchange)))
                .onErrorResume(throwable -> writeGatewayError(exchange, throwable));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }

    private Mono<Void> writeNotFoundWhenGatewayDidNotRoute(ServerWebExchange exchange) {
        if (exchange.getResponse().isCommitted() || !HttpStatus.NOT_FOUND.equals(exchange.getResponse().getStatusCode())) {
            return Mono.empty();
        }

        return apiErrorResponseWriter.write(exchange, HttpStatus.NOT_FOUND, "Route not found");
    }

    private Mono<Void> writeGatewayError(ServerWebExchange exchange, Throwable throwable) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(throwable);
        }

        HttpStatus status = resolveStatus(throwable);
        return apiErrorResponseWriter.write(exchange, status, status.getReasonPhrase());
    }

    private HttpStatus resolveStatus(Throwable throwable) {
        if (throwable instanceof ResponseStatusException responseStatusException
                && responseStatusException.getStatusCode() instanceof HttpStatus httpStatus) {
            return httpStatus;
        }

        if (hasCause(throwable, TimeoutException.class)) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }

        if (hasCause(throwable, ConnectException.class)) {
            return HttpStatus.BAD_GATEWAY;
        }

        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

}
