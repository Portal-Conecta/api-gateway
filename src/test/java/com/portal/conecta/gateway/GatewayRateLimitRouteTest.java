package com.portal.conecta.gateway;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "portal.gateway.security.enabled=false",
                "portal.gateway.rate-limit.enabled=true"
        }
)
class GatewayRateLimitRouteTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void appliesRequestRateLimiterFilterWhenRateLimitIsEnabled() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();

        assertThat(routes)
                .extracting(Route::getId)
                .contains("hub-auth", "hub", "checklist", "mapa", "comunicados");

        assertThat(routes)
                .allSatisfy(route -> assertThat(route.getFilters())
                        .anySatisfy(filter -> assertThat(filter.toString()).contains("RequestRateLimiter")));
    }
}
