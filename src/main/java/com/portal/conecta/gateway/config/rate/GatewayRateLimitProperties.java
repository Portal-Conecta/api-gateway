package com.portal.conecta.gateway.config.rate;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the gateway rate-limit policy used by route configuration.
 */
@ConfigurationProperties(prefix = "portal.gateway.rate-limit")
public class GatewayRateLimitProperties {

    private boolean enabled = false;
    private Policy authentication = new Policy(5, 10, 1);
    private Policy user = new Policy(30, 60, 1);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Policy getAuthentication() {
        return authentication;
    }

    public void setAuthentication(Policy authentication) {
        this.authentication = authentication;
    }

    public Policy getUser() {
        return user;
    }

    public void setUser(Policy user) {
        this.user = user;
    }

    public static class Policy {

        private int replenishRate;
        private long burstCapacity;
        private int requestedTokens;

        public Policy() {
            this(30, 60, 1);
        }

        public Policy(int replenishRate, long burstCapacity, int requestedTokens) {
            this.replenishRate = replenishRate;
            this.burstCapacity = burstCapacity;
            this.requestedTokens = requestedTokens;
        }

        public int getReplenishRate() {
            return replenishRate;
        }

        public void setReplenishRate(int replenishRate) {
            this.replenishRate = replenishRate;
        }

        public long getBurstCapacity() {
            return burstCapacity;
        }

        public void setBurstCapacity(long burstCapacity) {
            this.burstCapacity = burstCapacity;
        }

        public int getRequestedTokens() {
            return requestedTokens;
        }

        public void setRequestedTokens(int requestedTokens) {
            this.requestedTokens = requestedTokens;
        }
    }
}
