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
 * Normaliza o correlation ID usado em logs, respostas e chamadas aos servicos.
 */
@Component
public class CorrelationIdWebFilter implements WebFilter, Ordered {

    /**
     * Nome do header HTTP usado para receber, propagar e devolver o correlation ID.
     */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    /**
     * Nome do atributo interno usado para compartilhar o correlation ID entre
     * filtros do gateway.
     */
    public static final String CORRELATION_ID_ATTRIBUTE = "correlationId";
    private static final int MAX_CORRELATION_ID_LENGTH = 128;
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("^[A-Za-z0-9._:-]+$");

    /**
     * Cria o filtro que normaliza o correlation ID em todas as requisicoes.
     */
    public CorrelationIdWebFilter() {
    }

    /**
     * Resolve o correlation ID da requisicao, registra o valor no contexto,
     * adiciona o header na resposta e encaminha o mesmo header ao servico de
     * destino.
     *
     * @param exchange contexto HTTP recebido pelo gateway
     * @param chain proxima etapa da cadeia WebFlux
     * @return sinal reativo da cadeia com a requisicao mutada
     */
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

    /**
     * Executa antes dos demais filtros para garantir que logs, erros e roteamento
     * enxerguem o mesmo correlation ID.
     *
     * @return maior precedencia da cadeia WebFlux
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * Valida o header recebido e gera um UUID quando o valor esta ausente,
     * vazio, muito longo ou contem caracteres inseguros para logs.
     *
     * @param exchange contexto HTTP usado para ler `X-Correlation-Id`
     * @return correlation ID seguro para propagacao e log
     */
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
