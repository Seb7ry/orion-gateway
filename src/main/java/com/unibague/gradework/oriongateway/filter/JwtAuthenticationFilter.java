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
 */
@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    @Autowired
    private AuthServiceClient authServiceClient;

    // Public routes that don't require authentication
    private static final List<String> PUBLIC_ROUTES = List.of(
        "/api/auth/login",
        "/api/auth/oauth2",
        "/api/auth/jwks",
        "/actuator/health",
        "/health"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String requestId = exchange.getAttribute("requestId");

        // Skip authentication for public routes
        if (isPublicRoute(path)) {
            log.debug("[{}] Public route accessed: {}", requestId, path);
            return chain.filter(exchange);
        }

        // Extract JWT token from Authorization header
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("[{}] Missing or invalid Authorization header for protected route: {}", requestId, path);
            return unauthorizedResponse(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7); // Remove "Bearer " prefix

        // Validate token with auth service
        return authServiceClient.validateToken(token)
                .flatMap(validationResult -> {
                    if (isTokenValid(validationResult)) {
                        // Enrich request with user context
                        ServerWebExchange enrichedExchange = enrichRequestHeaders(exchange, validationResult);
                        log.info("[{}] Authentication successful for user: {}", 
                                requestId, validationResult.get("userId"));
                        return chain.filter(enrichedExchange);
                    } else {
                        log.warn("[{}] Token validation failed: {}", requestId, validationResult.get("error"));
                        return unauthorizedResponse(exchange, "Invalid or expired token");
                    }
                })
                .onErrorResume(error -> {
                    log.error("[{}] Auth service communication failed: {}", requestId, error.getMessage());
                    return serviceUnavailableResponse(exchange, "Authentication service unavailable");
                });
    }

    /**
     * Check if the route is public (doesn't require authentication)
     */
    private boolean isPublicRoute(String path) {
        return PUBLIC_ROUTES.stream().anyMatch(path::startsWith);
    }

    /**
     * Check if token validation result indicates a valid token
     */
    private boolean isTokenValid(Map<String, Object> validationResult) {
        return Boolean.TRUE.equals(validationResult.get("valid"));
    }

    /**
     * Enrich request headers with user context from validated token
     */
    private ServerWebExchange enrichRequestHeaders(ServerWebExchange exchange, Map<String, Object> userContext) {
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();

        // Add user context headers
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

        // Mark request as validated by gateway
        requestBuilder.header("X-Gateway-Validated", "true");
        requestBuilder.header("X-Gateway-Service", "orion-gateway");

        return exchange.mutate().request(requestBuilder.build()).build();
    }

    /**
     * Return 401 Unauthorized response
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
     * Return 503 Service Unavailable response
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
     * Set filter order to run after request logging but before routing
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}