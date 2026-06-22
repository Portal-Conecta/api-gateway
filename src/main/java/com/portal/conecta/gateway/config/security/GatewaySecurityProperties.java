package com.portal.conecta.gateway.config.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Vincula flags de seguranca de borda e configuracoes JWT usadas pela cadeia
 * WebFlux.
 */
@ConfigurationProperties(prefix = "portal.gateway.security")
public class GatewaySecurityProperties {

    private boolean enabled = true;
    private String jwtSecret = "ZGV2LXNlY3JldC1rZXktMzItYnl0ZXMtbWluaW11bS1mb3ItaHMyNTY=";
    private List<String> publicPaths = List.of(
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus",
            "/auth/**"
    );

    /**
     * Cria as propriedades de seguranca com defaults locais.
     */
    public GatewaySecurityProperties() {
    }

    /**
     * Indica se o gateway deve exigir JWT nas rotas protegidas.
     *
     * @return `true` quando a seguranca de borda esta habilitada
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Atualiza a flag que liga ou desliga a seguranca de borda.
     *
     * @param enabled novo valor da flag `portal.gateway.security.enabled`
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Obtem o segredo Base64 usado para validar tokens HS256 emitidos pelo Hub.
     *
     * @return segredo configurado para validacao JWT
     */
    public String getJwtSecret() {
        return jwtSecret;
    }

    /**
     * Atualiza o segredo usado pelo decoder JWT.
     *
     * @param jwtSecret segredo Base64 vindo de `JWT_SECRET` ou propriedade equivalente
     */
    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    /**
     * Lista os caminhos que podem ser acessados sem JWT.
     *
     * @return padroes publicos usados pela cadeia de seguranca
     */
    public List<String> getPublicPaths() {
        return publicPaths;
    }

    /**
     * Atualiza os caminhos publicos do gateway.
     *
     * @param publicPaths lista de padroes liberados sem autenticacao
     */
    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }
}
