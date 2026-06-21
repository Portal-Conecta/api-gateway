package com.portal.conecta.gateway.config.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds edge security flags and JWT settings used by the WebFlux security chain.
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
            "/hub/auth/**"
    );

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }
}
