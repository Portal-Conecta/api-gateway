package com.portal.conecta.gateway.config.rate;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Vincula as politicas de rate limit usadas pela configuracao de rotas.
 */
@ConfigurationProperties(prefix = "portal.gateway.rate-limit")
public class GatewayRateLimitProperties {

    private boolean enabled = false;
    private Policy authentication = new Policy(5, 10, 1);
    private Policy user = new Policy(30, 60, 1);

    /**
     * Cria as propriedades com defaults seguros para desenvolvimento local.
     */
    public GatewayRateLimitProperties() {
    }

    /**
     * Indica se as rotas devem receber o filtro `RequestRateLimiter`.
     *
     * @return `true` quando o rate limit esta habilitado no gateway
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Atualiza a flag que liga ou desliga rate limit.
     *
     * @param enabled novo valor de `portal.gateway.rate-limit.enabled`
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Obtem a politica usada em endpoints publicos de autenticacao.
     *
     * @return politica aplicada em `/hub/auth/**`
     */
    public Policy getAuthentication() {
        return authentication;
    }

    /**
     * Atualiza a politica usada em endpoints publicos de autenticacao.
     *
     * @param authentication politica configurada para login e refresh
     */
    public void setAuthentication(Policy authentication) {
        this.authentication = authentication;
    }

    /**
     * Obtem a politica usada nas rotas autenticadas.
     *
     * @return politica aplicada nas rotas protegidas do gateway
     */
    public Policy getUser() {
        return user;
    }

    /**
     * Atualiza a politica usada nas rotas autenticadas.
     *
     * @param user politica configurada para chamadas com usuario autenticado
     */
    public void setUser(Policy user) {
        this.user = user;
    }

    /**
     * Representa uma politica de token bucket do Redis Rate Limiter.
     */
    public static class Policy {

        private int replenishRate;
        private long burstCapacity;
        private int requestedTokens;

        /**
         * Cria uma politica com os limites padrao para rotas autenticadas.
         */
        public Policy() {
            this(30, 60, 1);
        }

        /**
         * Cria uma politica com todos os parametros usados pelo Redis Rate Limiter.
         *
         * @param replenishRate quantidade de tokens repostos por segundo
         * @param burstCapacity capacidade maxima acumulada no bucket
         * @param requestedTokens tokens consumidos por requisicao
         */
        public Policy(int replenishRate, long burstCapacity, int requestedTokens) {
            this.replenishRate = replenishRate;
            this.burstCapacity = burstCapacity;
            this.requestedTokens = requestedTokens;
        }

        /**
         * Informa a taxa de reposicao de tokens por segundo.
         *
         * @return quantidade de tokens repostos por segundo
         */
        public int getReplenishRate() {
            return replenishRate;
        }

        /**
         * Atualiza a taxa de reposicao de tokens por segundo.
         *
         * @param replenishRate nova taxa de reposicao do bucket
         */
        public void setReplenishRate(int replenishRate) {
            this.replenishRate = replenishRate;
        }

        /**
         * Informa a capacidade maxima de rajada permitida.
         *
         * @return capacidade maxima acumulada no bucket
         */
        public long getBurstCapacity() {
            return burstCapacity;
        }

        /**
         * Atualiza a capacidade maxima de rajada permitida.
         *
         * @param burstCapacity novo limite maximo do bucket
         */
        public void setBurstCapacity(long burstCapacity) {
            this.burstCapacity = burstCapacity;
        }

        /**
         * Informa quantos tokens uma requisicao consome.
         *
         * @return quantidade de tokens exigida por requisicao
         */
        public int getRequestedTokens() {
            return requestedTokens;
        }

        /**
         * Atualiza quantos tokens uma requisicao consome.
         *
         * @param requestedTokens nova quantidade de tokens por requisicao
         */
        public void setRequestedTokens(int requestedTokens) {
            this.requestedTokens = requestedTokens;
        }
    }
}
