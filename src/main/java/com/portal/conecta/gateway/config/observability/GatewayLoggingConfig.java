package com.portal.conecta.gateway.config.observability;

import com.portal.conecta.logging.ReactiveRouteResolver;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Integra o access log compartilhado com metadados específicos do Spring Cloud Gateway.
 */
@Configuration
public class GatewayLoggingConfig {

    /**
     * Resolve o ID da rota selecionada pelo Gateway para enriquecer o access log.
     *
     * @return resolver de rota usado pelo portal-logging reativo
     */
    @Bean
    public ReactiveRouteResolver gatewayReactiveRouteResolver() {
        return exchange -> {
            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            return route != null ? route.getId() : "unmatched";
        };
    }
}
