package com.portal.conecta.gateway.config.rate;

import java.util.Objects;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Fornece chaves de rate limit compativeis com o comportamento do gateway
 * original.
 */
@Configuration
public class RateLimiterConfig {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";

    /**
     * Cria a configuracao de resolvedores de chave de rate limit.
     */
    public RateLimiterConfig() {
    }

    /**
     * Resolve a chave do limite por usuario autenticado e usa IP como fallback
     * quando a autenticacao ainda nao existe no contexto reativo.
     *
     * @return resolvedor usado pelas rotas protegidas do gateway
     */
    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .cast(JwtAuthenticationToken.class)
                .map(jwtAuth -> "user:" + resolveUserId(jwtAuth))
                .switchIfEmpty(Mono.defer(() -> Mono.just(ipKey(exchange))))
                .onErrorResume(exception -> Mono.just(ipKey(exchange)));
    }

    /**
     * Resolve a chave do limite por IP para rotas publicas, como login e refresh.
     *
     * @return resolvedor usado pelas rotas publicas de autenticacao
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(ipKey(exchange));
    }

    /**
     * Escolhe o identificador do usuario usado na chave de rate limit.
     *
     * @param jwtAuth autenticacao JWT presente no contexto reativo
     * @return `sub`, `user_id` legado ou nome do principal
     */
    private String resolveUserId(JwtAuthenticationToken jwtAuth) {
        String subject = jwtAuth.getToken().getSubject();
        if (subject != null && !subject.isBlank()) {
            return subject;
        }

        String legacyUserId = jwtAuth.getToken().getClaimAsString("user_id");
        return Objects.requireNonNullElseGet(legacyUserId, jwtAuth::getName);
    }

    /**
     * Monta a chave de rate limit baseada no IP resolvido da requisicao.
     *
     * @param exchange contexto HTTP usado para ler headers e endereco remoto
     * @return chave prefixada com `ip:`
     */
    private String ipKey(ServerWebExchange exchange) {
        return "ip:" + extractClientIp(exchange);
    }

    /**
     * Prioriza headers de proxy porque o trafego de producao chega ao gateway
     * depois da terminacao da conexao original do cliente.
     *
     * @param exchange contexto HTTP usado para ler `X-Forwarded-For`, `X-Real-IP` e endereco remoto
     * @return IP do cliente ou `unknown` quando nao houver endereco disponivel
     */
    private String extractClientIp(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst(X_FORWARDED_FOR);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = exchange.getRequest().getHeaders().getFirst(X_REAL_IP);
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        var remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }
}
