package com.portal.conecta.gateway.config.route;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder.Builder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra as rotas externas do Portal Conecta mantendo o padrao dos endpoints
 * internos dos servicos.
 *
 * <p>O gateway remove apenas o primeiro prefixo publico, como {@code /hub} ou
 * {@code /checklist}, e encaminha o caminho restante sem reescrever contratos
 * dos servicos.</p>
 */
@Configuration
public class GatewayRouteConfig {

    private static final int DEFAULT_ORDER = 0;
    private static final int AUTH_ROUTE_ORDER = -20;

    private final GatewayRateLimitFilterApplier rateLimitFilterApplier;

    /**
     * Recebe o componente que aplica filtros compartilhados nas rotas.
     *
     * @param rateLimitFilterApplier aplicador de `StripPrefix` e rate limit por politica
     */
    public GatewayRouteConfig(GatewayRateLimitFilterApplier rateLimitFilterApplier) {
        this.rateLimitFilterApplier = rateLimitFilterApplier;
    }

    /**
     * Cria as rotas programaticamente para que filtros compartilhados sejam
     * aplicados de forma uniforme e dependam das variaveis de ambiente.
     *
     * @param routes builder de rotas do Spring Cloud Gateway
     * @param hubServiceUrl URL interna do Hub Core
     * @param checklistServiceUrl URL interna do servico de Checklist
     * @param mapaServiceUrl URL interna do servico de Mapa de Sala
     * @param comunicadosServiceUrl URL interna do servico de Comunicados
     * @return localizador de rotas usado pelo gateway em tempo de execucao
     */
    @Bean
    public RouteLocator portalConectaRoutes(
            RouteLocatorBuilder routes,
            @Value("${HUB_SERVICE_URL:http://localhost:8081}") String hubServiceUrl,
            @Value("${CHECKLIST_SERVICE_URL:http://localhost:8082}") String checklistServiceUrl,
            @Value("${MAPA_SERVICE_URL:http://localhost:8083}") String mapaServiceUrl,
            @Value("${COMUNICADOS_SERVICE_URL:http://localhost:8084}") String comunicadosServiceUrl
    ) {
        Builder builder = routes.routes();
        for (GatewayRouteDefinition routeDefinition : routeDefinitions(
                hubServiceUrl,
                checklistServiceUrl,
                mapaServiceUrl,
                comunicadosServiceUrl
        )) {
            builder.route(routeDefinition.id(), route -> route
                    .order(routeDefinition.order())
                    .path(routeDefinition.path())
                    .filters(filters -> rateLimitFilterApplier.apply(filters, routeDefinition.rateLimitPolicy()))
                    .uri(routeDefinition.uri()));
        }
        return builder.build();
    }

    /**
     * Define o catalogo estatico de rotas publicadas pela borda HTTP.
     *
     * @param hubServiceUrl destino configurado para as rotas do Hub
     * @param checklistServiceUrl destino configurado para as rotas de Checklist
     * @param mapaServiceUrl destino configurado para as rotas de Mapa de Sala
     * @param comunicadosServiceUrl destino configurado para as rotas de Comunicados
     * @return lista imutavel de definicoes de rota do gateway
     */
    private List<GatewayRouteDefinition> routeDefinitions(
            String hubServiceUrl,
            String checklistServiceUrl,
            String mapaServiceUrl,
            String comunicadosServiceUrl
    ) {
        return List.of(
                new GatewayRouteDefinition(
                        "hub-auth",
                        "/hub/auth/**",
                        hubServiceUrl,
                        AUTH_ROUTE_ORDER,
                        RateLimitPolicy.AUTHENTICATION
                ),
                new GatewayRouteDefinition("hub", "/hub/**", hubServiceUrl, DEFAULT_ORDER, RateLimitPolicy.USER),
                new GatewayRouteDefinition(
                        "checklist",
                        "/checklist/**",
                        checklistServiceUrl,
                        DEFAULT_ORDER,
                        RateLimitPolicy.USER
                ),
                new GatewayRouteDefinition("mapa", "/mapa/**", mapaServiceUrl, DEFAULT_ORDER, RateLimitPolicy.USER),
                new GatewayRouteDefinition(
                        "comunicados",
                        "/comunicados/**",
                        comunicadosServiceUrl,
                        DEFAULT_ORDER,
                        RateLimitPolicy.USER
                )
        );
    }
}
