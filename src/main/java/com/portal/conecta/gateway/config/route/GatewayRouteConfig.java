package com.portal.conecta.gateway.config.route;

import com.portal.conecta.gateway.config.rate.GatewayRateLimitProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

/**
 * Centralizes Portal Conecta edge routes so shared filters can be applied
 * consistently without duplicating YAML route blocks.
 */
@Configuration
public class GatewayRouteConfig {

    private final GatewayRateLimitProperties rateLimitProperties;

    public GatewayRouteConfig(GatewayRateLimitProperties rateLimitProperties) {
        this.rateLimitProperties = rateLimitProperties;
    }

    @Bean
    public RouteLocator portalConectaRoutes(
            RouteLocatorBuilder routes,
            @Qualifier("userKeyResolver") KeyResolver userKeyResolver,
            @Qualifier("ipKeyResolver") KeyResolver ipKeyResolver,
            @Value("${HUB_SERVICE_URL:http://localhost:8081}") String hubServiceUrl,
            @Value("${CHECKLIST_SERVICE_URL:http://localhost:8082}") String checklistServiceUrl,
            @Value("${MAPA_SERVICE_URL:http://localhost:8083}") String mapaServiceUrl,
            @Value("${COMUNICADOS_SERVICE_URL:http://localhost:8084}") String comunicadosServiceUrl
    ) {
        return routes.routes()
                .route("hub-auth", route -> route
                        .order(-20)
                        .path("/hub/auth/**")
                        .filters(filters -> stripPrefixAndRateLimit(
                                filters,
                                rateLimitProperties.getAuthentication(),
                                ipKeyResolver
                        ))
                        .uri(hubServiceUrl))
                .route("hub", route -> route
                        .path("/hub/**")
                        .filters(filters -> stripPrefixAndRateLimit(
                                filters,
                                rateLimitProperties.getUser(),
                                userKeyResolver
                        ))
                        .uri(hubServiceUrl))
                .route("checklist", route -> route
                        .path("/checklist/**")
                        .filters(filters -> stripPrefixAndRateLimit(
                                filters,
                                rateLimitProperties.getUser(),
                                userKeyResolver
                        ))
                        .uri(checklistServiceUrl))
                .route("mapa", route -> route
                        .path("/mapa/**")
                        .filters(filters -> stripPrefixAndRateLimit(
                                filters,
                                rateLimitProperties.getUser(),
                                userKeyResolver
                        ))
                        .uri(mapaServiceUrl))
                .route("comunicados", route -> route
                        .path("/comunicados/**")
                        .filters(filters -> stripPrefixAndRateLimit(
                                filters,
                                rateLimitProperties.getUser(),
                                userKeyResolver
                        ))
                        .uri(comunicadosServiceUrl))
                .build();
    }

    private GatewayFilterSpec stripPrefixAndRateLimit(
            GatewayFilterSpec filters,
            GatewayRateLimitProperties.Policy policy,
            KeyResolver keyResolver
    ) {
        GatewayFilterSpec filtered = filters.stripPrefix(1);

        if (!rateLimitProperties.isEnabled()) {
            return filtered;
        }

        // RedisRateLimiter keeps limits consistent across gateway instances.
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
}
