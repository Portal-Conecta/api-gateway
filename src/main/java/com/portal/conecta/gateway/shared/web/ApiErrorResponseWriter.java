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
 * Escreve erros gerados pelo gateway no mesmo contrato usado pelos servicos.
 */
@Component
public class ApiErrorResponseWriter {

    private final ObjectMapper objectMapper;

    /**
     * Recebe o mapper usado para serializar o contrato `ApiError`.
     *
     * @param objectMapper mapper JSON configurado pela aplicacao
     */
    public ApiErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Escreve uma resposta JSON de erro quando a resposta ainda nao foi enviada.
     *
     * @param exchange contexto HTTP que recebera status, content type e corpo
     * @param status status HTTP do erro gerado pelo gateway
     * @param message mensagem segura para o consumidor
     * @return sinal reativo de escrita da resposta ou vazio se ela ja foi enviada
     */
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

    /**
     * Serializa o erro para JSON e evita propagar detalhes internos se a
     * serializacao falhar.
     *
     * @param error contrato de erro que sera enviado ao cliente
     * @return bytes JSON da resposta ou `{}` em falha inesperada de serializacao
     */
    private byte[] serialize(ApiError error) {
        try {
            return objectMapper.writeValueAsBytes(error);
        } catch (Exception exception) {
            return "{}".getBytes(StandardCharsets.UTF_8);
        }
    }
}
