package com.unibague.gradework.oriongateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Global JWT Authentication Filter for API Gateway
 * Validates JWT tokens and enriches requests with user context
 * Bypasses authentication for internal service requests
 */
@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    @Autowired
    private AuthServiceClient authServiceClient;

    // Public routes que no requieren autenticación
    private static final List<String> PUBLIC_ROUTES = List.of(
            "/api/auth/login",
            "/api/auth/oauth2",
            "/api/auth/jwks",
            "/api/auth/validate",  // El Gateway necesita poder validar tokens
            "/api/auth/utils",     // Utilities para desarrollo
            "/actuator/health",
            "/health"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String requestId = exchange.getAttribute("requestId");

        // IMPORTANTE: Skip JWT si es request de servicio interno
        Boolean skipJwtFilter = exchange.getAttribute("SKIP_JWT_FILTER");
        if (Boolean.TRUE.equals(skipJwtFilter)) {
            log.debug("🔧 [{}] Skipping JWT filter for internal service request", requestId);
            return chain.filter(exchange);
        }

        // Skip authentication para rutas públicas
        if (isPublicRoute(path)) {
            log.debug("🔓 [{}] Public route accessed: {}", requestId, path);
            return chain.filter(exchange);
        }

        // Extraer JWT token del header Authorization
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("🚫 [{}] Missing or invalid Authorization header for: {}", requestId, path);
            return unauthorizedResponse(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7); // Remover "Bearer "

        // Validar token con auth service
        return authServiceClient.validateToken(token)
                .flatMap(validationResult -> {
                    if (isTokenValid(validationResult)) {
                        // Enriquecer request con contexto de usuario
                        ServerWebExchange enrichedExchange = enrichRequestHeaders(exchange, validationResult);
                        log.info("🔐 [{}] Authentication successful for user: {}",
                                requestId, validationResult.get("userId"));
                        return chain.filter(enrichedExchange);
                    } else {
                        log.warn("🚫 [{}] Token validation failed: {}", requestId, validationResult.get("error"));
                        return unauthorizedResponse(exchange, "Invalid or expired token");
                    }
                })
                .onErrorResume(error -> {
                    log.error("💥 [{}] Auth service communication failed: {}", requestId, error.getMessage());
                    return serviceUnavailableResponse(exchange, "Authentication service unavailable");
                });
    }

    /**
     * Verificar si la ruta es pública (no requiere autenticación)
     */
    private boolean isPublicRoute(String path) {
        return PUBLIC_ROUTES.stream().anyMatch(path::startsWith);
    }

    /**
     * Verificar si el resultado de validación del token indica un token válido
     */
    private boolean isTokenValid(Map<String, Object> validationResult) {
        return Boolean.TRUE.equals(validationResult.get("valid"));
    }

    /**
     * Enriquecer headers de request con contexto de usuario desde token validado
     */
    private ServerWebExchange enrichRequestHeaders(ServerWebExchange exchange, Map<String, Object> userContext) {
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();

        // Agregar headers de contexto de usuario
        if (userContext.get("userId") != null) {
            requestBuilder.header("X-User-ID", userContext.get("userId").toString());
        }

        if (userContext.get("email") != null) {
            requestBuilder.header("X-User-Email", userContext.get("email").toString());
        }

        if (userContext.get("role") != null) {
            requestBuilder.header("X-User-Role", userContext.get("role").toString());
        }

        if (userContext.get("programs") != null) {
            requestBuilder.header("X-User-Programs", userContext.get("programs").toString());
        }

        // Marcar request como validado por gateway
        requestBuilder.header("X-Gateway-Validated", "true");
        requestBuilder.header("X-Gateway-Service", "orion-gateway");

        return exchange.mutate().request(requestBuilder.build()).build();
    }

    /**
     * Retornar respuesta 401 Unauthorized
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json");
        response.getHeaders().add("X-Auth-Error", message);

        String body = String.format(
                "{\"error\":\"UNAUTHORIZED\",\"message\":\"%s\",\"timestamp\":\"%s\",\"path\":\"%s\"}",
                message,
                java.time.LocalDateTime.now(),
                exchange.getRequest().getPath().value()
        );

        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }

    /**
     * Retornar respuesta 503 Service Unavailable
     */
    private Mono<Void> serviceUnavailableResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        response.getHeaders().add("Content-Type", "application/json");
        response.getHeaders().add("Retry-After", "30");

        String body = String.format(
                "{\"error\":\"SERVICE_UNAVAILABLE\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                message,
                java.time.LocalDateTime.now()
        );

        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }

    /**
     * Set filter order para ejecutar después del filtro de servicios internos
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}