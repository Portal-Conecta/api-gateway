package com.portal.conecta.gateway.config.observability;

import com.portal.conecta.logging.CorrelationIdResolver;
import com.portal.conecta.logging.LoggingContextKeys;
import com.portal.conecta.logging.LoggingProperties;
import com.portal.conecta.logging.ReactiveCorrelationIdWebFilter;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Ajusta o filtro compartilhado para o gateway, que precisa sobrescrever o
 * header de correlation ID depois que os headers do downstream forem copiados.
 */
@Configuration
public class GatewayCorrelationIdConfig {

    @Bean
    public ReactiveCorrelationIdWebFilter gatewayReactiveCorrelationIdWebFilter(
            CorrelationIdResolver correlationIdResolver,
            LoggingProperties loggingProperties
    ) {
        return new GatewayEdgeCorrelationIdWebFilter(correlationIdResolver, loggingProperties);
    }

    private static final class GatewayEdgeCorrelationIdWebFilter extends ReactiveCorrelationIdWebFilter {

        private final CorrelationIdResolver correlationIdResolver;
        private final LoggingProperties loggingProperties;

        private GatewayEdgeCorrelationIdWebFilter(
                CorrelationIdResolver correlationIdResolver,
                LoggingProperties loggingProperties
        ) {
            super(correlationIdResolver, loggingProperties);
            this.correlationIdResolver = correlationIdResolver;
            this.loggingProperties = loggingProperties;
        }

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
            String header = loggingProperties.getCorrelationHeader();
            String correlationId = correlationIdResolver.resolve(
                    exchange.getRequest().getHeaders().getFirst(header),
                    header,
                    loggingProperties.getMaxCorrelationIdLength()
            );

            exchange.getAttributes().put(CORRELATION_ID_ATTRIBUTE, correlationId);
            exchange.getResponse().beforeCommit(() -> {
                exchange.getResponse().getHeaders().set(header, correlationId);
                return Mono.empty();
            });

            ServerHttpRequest request = exchange.getRequest()
                    .mutate()
                    .headers(headers -> headers.set(header, correlationId))
                    .build();

            return chain.filter(exchange.mutate().request(request).build())
                    .contextWrite(context -> context.put(CORRELATION_ID_ATTRIBUTE, correlationId))
                    .doFinally(signalType -> MDC.remove(LoggingContextKeys.CORRELATION_ID));
        }
    }
}
