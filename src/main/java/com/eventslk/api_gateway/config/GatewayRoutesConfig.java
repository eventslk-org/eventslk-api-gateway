package com.eventslk.api_gateway.config;

import org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.web.servlet.function.RequestPredicates.path;

@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouterFunction<ServerResponse> eventRegistrationApiRoutes() {
        return GatewayRouterFunctions.route("event-registration-api")
                .route(path("/auth/**")
                        .or(path("/event/**"))
                        .or(path("/book/**"))
                        .or(path("/user/**")),
                        HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("event-registration-api"))
                .build();
    }
}
