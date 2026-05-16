package com.eventslk.api_gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Centralised CORS policy for the entire gateway.
 *
 * Because all client traffic flows through the gateway, CORS only needs to be
 * configured here. You should remove any @CrossOrigin annotations from the
 * downstream services (event-registration-api, etc.) once they are fully behind
 * this gateway and are not directly reachable from the browser.
 *
 * Dev:  allowedOrigins is a wildcard "*" — fine for local development.
 * Prod: replace "*" with your actual frontend URLs via an environment variable
 *       (see the TODO comment below) to prevent unwanted cross-origin access.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    // TODO (prod): inject allowed origins from application.yaml
    //   app.cors.allowed-origins: ${CORS_ALLOWED_ORIGINS:*}
    private static final String[] ALLOWED_ORIGINS = {"*"};

    private static final String[] ALLOWED_METHODS = {
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
    };

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(ALLOWED_ORIGINS)
                .allowedMethods(ALLOWED_METHODS)
                .allowedHeaders("*")
                // exposedHeaders lets the browser read custom response headers
                .exposedHeaders("Authorization", "X-User-Name", "X-User-Roles")
                .maxAge(3600);        // preflight cache duration in seconds
    }
}
