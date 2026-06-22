package com.portal.conecta.gateway.config.route;

/**
 * Descreve uma rota publicada pelo gateway sem acoplar a regra de negocio ao
 * builder do Spring Cloud Gateway.
 *
 * @param id identificador usado em logs, metricas e diagnostico da rota
 * @param path padrao externo recebido pelo gateway
 * @param uri destino interno configurado por variavel de ambiente
 * @param order prioridade da rota quando dois padroes podem atender o mesmo caminho
 * @param rateLimitPolicy politica de rate limit aplicada depois da remocao do prefixo
 */
record GatewayRouteDefinition(
        String id,
        String path,
        String uri,
        int order,
        RateLimitPolicy rateLimitPolicy
) {
}
