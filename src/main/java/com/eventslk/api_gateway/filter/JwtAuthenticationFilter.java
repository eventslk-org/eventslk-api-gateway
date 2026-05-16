package com.eventslk.api_gateway.filter;

import com.eventslk.api_gateway.config.GatewayProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Gateway-level JWT validation filter.
 *
 * Note: the reactive Spring Cloud Gateway uses {@code AbstractGatewayFilterFactory},
 * but this project uses the servlet/MVC variant (spring-cloud-starter-gateway-server-webmvc),
 * so the filter is registered as a {@link OncePerRequestFilter} (the servlet equivalent).
 *
 * Behaviour:
 *   - Public paths (login/register/verify/actuator) are bypassed.
 *   - All other requests must carry a valid {@code Authorization: Bearer <jwt>} header.
 *   - On success the JWT's {@code sub} and {@code roles} claims are forwarded to the
 *     downstream service as {@code X-User-Name} and {@code X-User-Roles}.
 *   - On failure the request is short-circuited with HTTP 401.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final GatewayProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private SecretKey secretKey;

    @PostConstruct
    public void initSecretKey() {
        byte[] keyBytes = Base64.getDecoder().decode(properties.getJwt().getSecret());
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (isPublicPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorized(response, "Missing or malformed Authorization header");
            return;
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String username = claims.getSubject();
            String roles = extractRoles(claims);

            MutableHttpServletRequest mutable = new MutableHttpServletRequest(request);
            mutable.addHeader("X-User-Name",  username);
            mutable.addHeader("X-User-Roles", roles);

            log.debug("JWT valid — forwarding {} {} as user={} roles={}",
                    request.getMethod(), path, username, roles);

            chain.doFilter(mutable, response);

        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT for path {}: {}", path, e.getMessage());
            sendUnauthorized(response, "Token has expired");
        } catch (JwtException e) {
            log.warn("Invalid JWT for path {}: {}", path, e.getMessage());
            sendUnauthorized(response, "Invalid token");
        }
    }

    /**
     * Roles may be issued as a single string ("ADMIN"), a comma-separated string
     * ("ADMIN,USER"), or a JSON array (["ADMIN","USER"]). Normalise to a
     * comma-separated string so downstream services can split on ','.
     */
    @SuppressWarnings("unchecked")
    private String extractRoles(Claims claims) {
        Object raw = claims.get("roles");
        if (raw == null) {
            raw = claims.get("role");
        }
        if (raw == null) {
            return "";
        }
        if (raw instanceof Collection<?> collection) {
            return ((Collection<Object>) collection).stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(","));
        }
        return raw.toString();
    }

    private boolean isPublicPath(String path) {
        return properties.getGateway().getPublicPaths().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}"
        );
    }
}
