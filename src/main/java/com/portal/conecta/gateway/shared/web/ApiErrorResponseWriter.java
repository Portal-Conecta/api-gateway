package com.portal.conecta.gateway.shared.web;

import com.portal.conecta.gateway.shared.exception.ApiError;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes gateway-owned errors with the same contract used by the services.
 */
@Component
public class ApiErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ApiErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String message) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String path = exchange.getRequest().getPath().value();
        ApiError error = ApiError.of(status, message, path);
        byte[] bytes = serialize(error);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);

        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private byte[] serialize(ApiError error) {
        try {
            return objectMapper.writeValueAsBytes(error);
        } catch (Exception exception) {
            return "{}".getBytes(StandardCharsets.UTF_8);
        }
    }
}
