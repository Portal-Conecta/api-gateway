package com.portal.conecta.gateway.config.route;

/**
 * Politicas de rate limit suportadas pelas rotas do gateway.
 */
enum RateLimitPolicy {

    /**
     * Usada em endpoints publicos de autenticacao, onde a melhor chave disponivel
     * antes do login e o IP do cliente.
     */
    AUTHENTICATION,

    /**
     * Usada em endpoints protegidos, priorizando o usuario autenticado e usando
     * IP apenas como fallback.
     */
    USER
}
