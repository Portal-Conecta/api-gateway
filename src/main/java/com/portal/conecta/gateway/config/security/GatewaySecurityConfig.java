package com.portal.conecta.gateway.config.security;

import com.portal.conecta.gateway.shared.web.ApiErrorResponseWriter;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Validates Hub-issued JWTs at the edge while preserving the Authorization
 * header for downstream services to enforce domain authorization.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    private static final int MIN_HS256_KEY_BYTES = 32;

    private final GatewaySecurityProperties properties;
    private final ApiErrorResponseWriter apiErrorResponseWriter;

    public GatewaySecurityConfig(
            GatewaySecurityProperties properties,
            ApiErrorResponseWriter apiErrorResponseWriter
    ) {
        this.properties = properties;
        this.apiErrorResponseWriter = apiErrorResponseWriter;
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        ServerHttpSecurity serverHttpSecurity = http
                .csrf(ServerHttpSecurity.CsrfSpec::disable);

        if (!properties.isEnabled()) {
            return serverHttpSecurity
                    .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                    .build();
        }

        return serverHttpSecurity
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(properties.getPublicPaths().toArray(String[]::new)).permitAll()
                        .anyExchange().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((exchange, exception) -> apiErrorResponseWriter.write(
                                exchange,
                                HttpStatus.UNAUTHORIZED,
                                "Authentication is required"
                        ))
                        .accessDeniedHandler((exchange, exception) -> apiErrorResponseWriter.write(
                                exchange,
                                HttpStatus.FORBIDDEN,
                                "Access is denied"
                        )))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint((exchange, exception) -> apiErrorResponseWriter.write(
                                exchange,
                                HttpStatus.UNAUTHORIZED,
                                "Authentication is required"
                        ))
                        .accessDeniedHandler((exchange, exception) -> apiErrorResponseWriter.write(
                                exchange,
                                HttpStatus.FORBIDDEN,
                                "Access is denied"
                        ))
                        .jwt(jwt -> jwt.jwtDecoder(reactiveJwtDecoder())))
                .build();
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        byte[] keyBytes = decodeSecret(properties.getJwtSecret());
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");

        return NimbusReactiveJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * The Hub and downstream services share a Base64 encoded HS256 secret. Failing
     * fast here prevents the gateway from accepting tokens with a different key.
     */
    private byte[] decodeSecret(String secret) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("portal.gateway.security.jwt-secret must be Base64 encoded", exception);
        }

        if (keyBytes.length < MIN_HS256_KEY_BYTES) {
            throw new IllegalStateException("portal.gateway.security.jwt-secret must decode to at least 32 bytes");
        }

        return keyBytes;
    }
}
