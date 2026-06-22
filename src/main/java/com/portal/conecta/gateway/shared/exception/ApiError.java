package com.portal.conecta.gateway.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Contrato de erro retornado apenas para falhas geradas pelo proprio gateway.
 *
 * @param timestamp instante UTC em que o erro foi montado
 * @param status codigo HTTP numerico retornado ao cliente
 * @param error descricao padrao do status HTTP
 * @param message mensagem objetiva para o consumidor da API
 * @param path caminho externo chamado pelo cliente
 * @param errors detalhes opcionais de validacao por campo
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldErrorDetail> errors
) {

    /**
     * Cria um erro padrao do gateway usando o status HTTP e o caminho externo
     * chamado pelo cliente.
     *
     * @param status status HTTP que sera aplicado na resposta
     * @param message mensagem segura para o consumidor
     * @param path caminho externo da requisicao
     * @return instancia de {@link ApiError} sem detalhes de validacao
     */
    public static ApiError of(HttpStatus status, String message, String path) {
        return new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                null
        );
    }

    /**
     * Detalhe opcional para erros de validacao por campo.
     *
     * @param field nome do campo invalido
     * @param message mensagem associada ao campo invalido
     */
    public record FieldErrorDetail(
            String field,
            String message
    ) {
    }
}
