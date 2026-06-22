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
 * Converte falhas geradas pelo gateway para o contrato de erro do Portal
 * Conecta.
 */
@Component
public class GatewayErrorWebFilter implements WebFilter, Ordered {

    private final ApiErrorResponseWriter apiErrorResponseWriter;

    /**
     * Recebe o writer compartilhado para manter o formato `ApiError`.
     *
     * @param apiErrorResponseWriter componente responsavel por escrever erros do gateway
     */
    public GatewayErrorWebFilter(ApiErrorResponseWriter apiErrorResponseWriter) {
        this.apiErrorResponseWriter = apiErrorResponseWriter;
    }

    /**
     * Converte rota nao encontrada e excecoes da borda para respostas `ApiError`
     * sem interceptar corpos de erro gerados pelos servicos de destino.
     *
     * @param exchange contexto HTTP atual
     * @param chain proxima etapa da cadeia WebFlux
     * @return sinal reativo que trata erros gerados no proprio gateway
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange)
                .then(Mono.defer(() -> writeNotFoundWhenGatewayDidNotRoute(exchange)))
                .onErrorResume(throwable -> writeGatewayError(exchange, throwable));
    }

    /**
     * Executa perto do fim da cadeia para observar o status final definido pelo
     * roteamento do gateway.
     *
     * @return ordem imediatamente anterior ao menor nivel de precedencia
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }

    /**
     * Escreve `404 Not Found` quando nenhuma rota do gateway atendeu a chamada.
     *
     * @param exchange contexto HTTP com o status final da requisicao
     * @return sinal vazio quando nao ha erro a escrever ou sinal de escrita do `ApiError`
     */
    private Mono<Void> writeNotFoundWhenGatewayDidNotRoute(ServerWebExchange exchange) {
        if (exchange.getResponse().isCommitted() || !HttpStatus.NOT_FOUND.equals(exchange.getResponse().getStatusCode())) {
            return Mono.empty();
        }

        return apiErrorResponseWriter.write(exchange, HttpStatus.NOT_FOUND, "Route not found");
    }

    /**
     * Mapeia excecoes geradas na borda para status HTTP seguros e escreve o
     * contrato padronizado.
     *
     * @param exchange contexto HTTP que recebera a resposta de erro
     * @param throwable excecao capturada durante roteamento ou comunicacao
     * @return sinal de escrita do erro ou propagacao quando a resposta ja foi enviada
     */
    private Mono<Void> writeGatewayError(ServerWebExchange exchange, Throwable throwable) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(throwable);
        }

        HttpStatus status = resolveStatus(throwable);
        return apiErrorResponseWriter.write(exchange, status, status.getReasonPhrase());
    }

    /**
     * Traduz excecoes conhecidas do gateway para status HTTP de borda.
     *
     * @param throwable excecao capturada na cadeia reativa
     * @return status HTTP seguro para expor ao cliente
     */
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

    /**
     * Percorre a cadeia de causas para identificar excecoes encapsuladas pelo
     * stack reativo ou pelo cliente HTTP.
     *
     * @param throwable excecao raiz ou wrapper recebido pela cadeia
     * @param causeType tipo de causa procurado
     * @return `true` quando a causa aparece em qualquer nivel da cadeia
     */
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
