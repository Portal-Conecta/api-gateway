package com.portal.conecta.gateway.config.route;

import com.portal.conecta.gateway.config.rate.GatewayRateLimitProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Aplica os filtros compartilhados de rate limit nas rotas do gateway.
 *
 * <p>A classe centraliza a escolha entre chave por usuario e chave por IP para
 * manter a configuracao de rotas simples e evitar duplicacao de filtros.</p>
 */
@Component
class GatewayRateLimitFilterApplier {

    private final GatewayRateLimitProperties rateLimitProperties;
    private final KeyResolver userKeyResolver;
    private final KeyResolver ipKeyResolver;

    /**
     * Recebe propriedades e resolvedores necessarios para configurar o
     * `RequestRateLimiter` por tipo de rota.
     *
     * @param rateLimitProperties configuracoes externas das politicas de limite
     * @param userKeyResolver resolvedor usado em rotas autenticadas
     * @param ipKeyResolver resolvedor usado em rotas publicas
     */
    GatewayRateLimitFilterApplier(
            GatewayRateLimitProperties rateLimitProperties,
            @Qualifier("userKeyResolver") KeyResolver userKeyResolver,
            @Qualifier("ipKeyResolver") KeyResolver ipKeyResolver
    ) {
        this.rateLimitProperties = rateLimitProperties;
        this.userKeyResolver = userKeyResolver;
        this.ipKeyResolver = ipKeyResolver;
    }

    /**
     * Aplica a remocao de prefixo configurada na rota e, quando habilitado, o
     * filtro Redis de rate limit.
     *
     * @param filters builder de filtros da rota atual
     * @param rateLimitPolicy politica que define limites e chave da rota
     * @param stripPrefixParts quantidade de segmentos externos removidos antes do encaminhamento
     * @param forwardedPrefix prefixo publico informado ao servico de destino
     * @return builder de filtros com prefixo tratado e rate limit configurado
     */
    GatewayFilterSpec apply(
            GatewayFilterSpec filters,
            RateLimitPolicy rateLimitPolicy,
            int stripPrefixParts,
            String forwardedPrefix
    ) {
        GatewayFilterSpec filtered = stripPrefix(filters, stripPrefixParts);
        filtered = forwardedPrefix(filtered, forwardedPrefix);

        if (!rateLimitProperties.isEnabled()) {
            return filtered;
        }

        GatewayRateLimitProperties.Policy policy = policyFor(rateLimitPolicy);
        KeyResolver keyResolver = keyResolverFor(rateLimitPolicy);

        return filtered.requestRateLimiter()
                .rateLimiter(RedisRateLimiter.class, config -> config
                        .setReplenishRate(policy.getReplenishRate())
                        .setBurstCapacity(policy.getBurstCapacity())
                        .setRequestedTokens(policy.getRequestedTokens()))
                .configure(config -> config
                        .setKeyResolver(keyResolver)
                        .setStatusCode(HttpStatus.TOO_MANY_REQUESTS)
                .setDenyEmptyKey(true));
    }

    /**
     * Remove prefixos publicos apenas quando a rota declara essa necessidade.
     *
     * <p>A autenticacao e publicada em `/auth/**` e deve chegar ao Hub com o
     * mesmo caminho. Rotas de servico, como `/hub/**`, removem o primeiro
     * segmento para preservar o contrato interno do servico.</p>
     *
     * @param filters builder de filtros da rota atual
     * @param stripPrefixParts quantidade de segmentos removidos pelo filtro `StripPrefix`
     * @return builder original ou builder com `StripPrefix` aplicado
     */
    private GatewayFilterSpec stripPrefix(GatewayFilterSpec filters, int stripPrefixParts) {
        if (stripPrefixParts <= 0) {
            return filters;
        }
        return filters.stripPrefix(stripPrefixParts);
    }

    /**
     * Informa ao servico interno qual prefixo publico foi removido pelo gateway.
     *
     * @param filters builder de filtros da rota atual
     * @param forwardedPrefix valor do header `X-Forwarded-Prefix`
     * @return builder original ou builder com header configurado
     */
    private GatewayFilterSpec forwardedPrefix(GatewayFilterSpec filters, String forwardedPrefix) {
        if (forwardedPrefix == null || forwardedPrefix.isBlank()) {
            return filters;
        }
        return filters.setRequestHeader("X-Forwarded-Prefix", forwardedPrefix);
    }

    /**
     * Seleciona os valores de token bucket de acordo com o tipo de rota.
     *
     * @param rateLimitPolicy politica declarada na rota
     * @return configuracao numerica aplicada no Redis Rate Limiter
     */
    private GatewayRateLimitProperties.Policy policyFor(RateLimitPolicy rateLimitPolicy) {
        return switch (rateLimitPolicy) {
            case AUTHENTICATION -> rateLimitProperties.getAuthentication();
            case USER -> rateLimitProperties.getUser();
        };
    }

    /**
     * Seleciona a origem da chave do rate limit para evitar misturar limites de
     * login por IP com limites de uso autenticado por usuario.
     *
     * @param rateLimitPolicy politica declarada na rota
     * @return resolvedor de chave usado pelo filtro `RequestRateLimiter`
     */
    private KeyResolver keyResolverFor(RateLimitPolicy rateLimitPolicy) {
        return switch (rateLimitPolicy) {
            case AUTHENTICATION -> ipKeyResolver;
            case USER -> userKeyResolver;
        };
    }
}
