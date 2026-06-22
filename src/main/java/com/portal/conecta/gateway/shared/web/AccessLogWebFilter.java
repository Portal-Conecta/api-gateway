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
 * Emite um log de acesso estruturado ao final de cada troca HTTP.
 */
@Component
public class AccessLogWebFilter implements WebFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccessLogWebFilter.class);

    /**
     * Cria o filtro de log de acesso usado pela cadeia WebFlux.
     */
    public AccessLogWebFilter() {
    }

    /**
     * Mede a duracao da troca HTTP e agenda o log operacional ao final da
     * cadeia reativa, independentemente do resultado da requisicao.
     *
     * @param exchange contexto HTTP atual, usado para ler rota, status e headers
     * @param chain proxima etapa da cadeia WebFlux
     * @return sinal reativo que conclui depois da execucao da cadeia
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startedAt = System.currentTimeMillis();

        return chain.filter(exchange)
                .doFinally(signalType -> logRequest(exchange, startedAt));
    }

    /**
     * Define a ordem do filtro para executar o log depois dos filtros que
     * resolvem rota, status e correlation ID.
     *
     * @return menor prioridade padrao, mantendo o log no fim da cadeia
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /**
     * Escreve um registro de acesso sem expor token, corpo da requisicao ou
     * headers sensiveis.
     *
     * @param exchange contexto HTTP usado para montar os campos do log
     * @param startedAt timestamp em milissegundos capturado antes da cadeia
     */
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
