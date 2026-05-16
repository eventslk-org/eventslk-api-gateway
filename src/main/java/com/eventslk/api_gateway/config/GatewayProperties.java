package com.eventslk.api_gateway.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Strongly-typed config bound from application.yaml under the "app" prefix.
 *
 * Example YAML:
 *   app:
 *     jwt:
 *       secret: ${JWT_SECRET}
 *     gateway:
 *       public-paths:
 *         - /auth/signup
 *         - /auth/login
 *         - /actuator/**
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app")
public class GatewayProperties {

    @Valid
    @NotNull
    private Jwt jwt = new Jwt();

    @Valid
    @NotNull
    private Gateway gateway = new Gateway();

    @Getter
    @Setter
    public static class Jwt {
        /** Hex-encoded HS256 secret — must match the secret used in event-registration-api. */
        @NotBlank(message = "JWT_SECRET environment variable must be set")
        private String secret;
    }

    @Getter
    @Setter
    public static class Gateway {
        /**
         * Ant-style path patterns that bypass JWT validation.
         * Default covers signup, login and actuator probes.
         */
        @NotNull
        private List<String> publicPaths = List.of(
                // "/auth/signup",
                "/auth/*",
                "/actuator/**"
        );
    }
}
