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

/**
 * Gateway-level JWT validation filter.
 *
 * Runs on every inbound request before it is proxied to a downstream service.
 * Public paths (configured in application.yaml) are allowed through without a token.
 *
 * On a valid token the filter injects two trusted headers that downstream services
 * can read instead of re-validating the token themselves:
 *   X-User-Email  — the JWT subject (user's email)
 *   X-User-Role   — the "role" claim (e.g. USER, ADMIN)
 *
 * On an invalid/expired token the filter short-circuits with HTTP 401.
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

        // ── 1. Allow public paths through without any token ───────────────────
        if (isPublicPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        // ── 2. Require Authorization: Bearer <token> header ───────────────────
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorized(response, "Missing or malformed Authorization header");
            return;
        }

        String token = authHeader.substring(7);

        // ── 3. Validate the token ─────────────────────────────────────────────
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // ── 4. Inject trusted user identity headers for downstream services ─
            MutableHttpServletRequest mutable = new MutableHttpServletRequest(request);
            mutable.addHeader("X-User-Email", claims.getSubject());
            mutable.addHeader("X-User-Role",  claims.get("role", String.class));

            log.debug("JWT valid — forwarding {} {} as user={} role={}",
                    request.getMethod(), path,
                    claims.getSubject(), claims.get("role"));

            chain.doFilter(mutable, response);

        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT for path {}: {}", path, e.getMessage());
            sendUnauthorized(response, "Token has expired");
        } catch (JwtException e) {
            log.warn("Invalid JWT for path {}: {}", path, e.getMessage());
            sendUnauthorized(response, "Invalid token");
        }
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
