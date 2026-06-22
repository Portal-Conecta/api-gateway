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
 * Valida na borda os JWTs emitidos pelo Hub e preserva o header
 * {@code Authorization} para que os servicos apliquem autorizacao de dominio.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    private static final int MIN_HS256_KEY_BYTES = 32;

    private final GatewaySecurityProperties properties;
    private final ApiErrorResponseWriter apiErrorResponseWriter;

    /**
     * Recebe as propriedades de seguranca e o writer responsavel por respostas
     * de erro no formato `ApiError`.
     *
     * @param properties configuracoes externas de seguranca do gateway
     * @param apiErrorResponseWriter componente que escreve erros de autenticacao e autorizacao
     */
    public GatewaySecurityConfig(
            GatewaySecurityProperties properties,
            ApiErrorResponseWriter apiErrorResponseWriter
    ) {
        this.properties = properties;
        this.apiErrorResponseWriter = apiErrorResponseWriter;
    }

    /**
     * Monta a cadeia de seguranca WebFlux do gateway.
     *
     * <p>Quando a seguranca esta desabilitada por ambiente, todas as rotas sao
     * liberadas. Quando esta habilitada, apenas os caminhos publicos configurados
     * podem passar sem JWT; as demais requisicoes exigem Bearer Token valido.</p>
     *
     * @param http builder de seguranca reativa fornecido pelo Spring Security
     * @return cadeia de filtros de seguranca usada pelo WebFlux
     */
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

    /**
     * Cria o decodificador JWT HS256 usado para validar tokens emitidos pelo Hub.
     *
     * @return decoder reativo configurado com o segredo compartilhado
     */
    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        byte[] keyBytes = decodeSecret(properties.getJwtSecret());
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");

        return NimbusReactiveJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * Decodifica o segredo HS256 compartilhado com Hub e servicos. A falha
     * rapida evita que o gateway suba aceitando tokens assinados com chave
     * diferente.
     *
     * @param secret segredo Base64 recebido por configuracao
     * @return bytes decodificados do segredo HS256
     * @throws IllegalStateException quando o segredo nao for Base64 ou tiver menos de 32 bytes
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
