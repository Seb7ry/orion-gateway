package com.unibague.gradework.oriongateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
 * Bypassa correctamente OAuth2, endpoints públicos y llamadas internas S2S.
 */
@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    @Autowired
    private AuthServiceClient authServiceClient;

    @Value("${gateway.service.token:${GATEWAY_SERVICE_TOKEN:dev-token-unibague-orion}}")
    private String validServiceToken;

    // Misma lista de servicios confiables que usas en InternalServiceFilter
    private static final List<String> TRUSTED_SERVICES = List.of(
            "orion-auth",
            "orion-user",
            "orion-program",
            "orion-document"
    );

    // Rutas públicas que NO requieren autenticación JWT
    private static final List<String> PUBLIC_ROUTES = List.of(
            // OAuth2
            "/oauth2/authorization",
            "/login/oauth2/code",

            // Auth públicos mínimos
            "/api/auth/login",
            "/api/auth/validate",
            "/api/auth/jwks",

            // Health & fallback
            "/actuator/health",
            "/health",
            "/fallback"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String requestId = exchange.getAttribute("requestId");

        // 1) Bypass por atributo interno puesto por InternalServiceFilter
        Boolean skipJwtFilter = exchange.getAttribute("SKIP_JWT_FILTER");
        if (Boolean.TRUE.equals(skipJwtFilter)) {
            log.debug("[{}] Skipping JWT (attribute SKIP_JWT_FILTER) for {}", requestId, path);
            return chain.filter(exchange);
        }

        // 2) Bypass explícito por header X-Skip-Auth (definido en rutas del gateway)
        String skipAuth = header(exchange, "X-Skip-Auth");
        if (isTrue(skipAuth)) {
            log.debug("[{}] Skipping JWT due to X-Skip-Auth for {}", requestId, path);
            return chain.filter(exchange);
        }

        // 3) Bypass por headers internos (service-to-service)
        String serviceRequest = header(exchange, "X-Service-Request");
        String internalRequest = header(exchange, "X-Internal-Request");
        if (isTrue(serviceRequest) || isTrue(internalRequest)) {
            log.debug("[{}] Skipping JWT due to internal headers (X-Service-Request/X-Internal-Request) for {}", requestId, path);
            return chain.filter(exchange);
        }

        // 4) Bypass por credenciales de servicio válidas (defensa extra)
        String serviceName = header(exchange, "X-Service-Name");
        String serviceToken = header(exchange, "X-Service-Token");
        if (isValidInternalService(serviceName, serviceToken)) {
            log.debug("[{}] Skipping JWT due to valid service credentials: {} -> {}", requestId, serviceName, path);
            return chain.filter(exchange);
        }

        // 5) Rutas públicas
        if (isPublicRoute(path)) {
            log.debug("[{}] Public route accessed: {}", requestId, path);
            return chain.filter(exchange);
        }

        // 6) Extraer JWT token del header Authorization
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("[{}] Missing or invalid Authorization header for: {}", requestId, path);
            return unauthorizedResponse(exchange, "User not authenticated");
        }

        String token = authHeader.substring(7); // Remover "Bearer "

        // 7) Validar token con auth service
        return authServiceClient.validateToken(token)
                .flatMap(validationResult -> {
                    if (isTokenValid(validationResult)) {
                        ServerWebExchange enrichedExchange = enrichRequestHeaders(exchange, validationResult);
                        log.info("[{}] Authentication successful for user: {}", requestId, validationResult.get("userId"));
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

    /* --------------------------------- helpers --------------------------------- */

    private static String header(ServerWebExchange exchange, String name) {
        return exchange.getRequest().getHeaders().getFirst(name);
    }

    private static boolean isTrue(String v) {
        return v != null && "true".equalsIgnoreCase(v.trim());
    }

    /** Igual que en InternalServiceFilter */
    private boolean isValidInternalService(String serviceName, String serviceToken) {
        if (serviceName == null || serviceToken == null) return false;
        if (!TRUSTED_SERVICES.contains(serviceName)) return false;
        return serviceToken.equals(validServiceToken);
    }

    /** Verificar si la ruta es pública (por prefijo) */
    private boolean isPublicRoute(String path) {
        return PUBLIC_ROUTES.stream().anyMatch(path::startsWith);
    }

    /** Verificar si el resultado de validación del token indica un token válido */
    private boolean isTokenValid(Map<String, Object> validationResult) {
        return Boolean.TRUE.equals(validationResult.get("valid"));
    }

    /** Enriquecer headers de request con contexto de usuario desde token validado */
    private ServerWebExchange enrichRequestHeaders(ServerWebExchange exchange, Map<String, Object> userContext) {
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();

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

        requestBuilder.header("X-Gateway-Validated", "true");
        requestBuilder.header("X-Gateway-Service", "orion-gateway");

        return exchange.mutate().request(requestBuilder.build()).build();
    }

    /** 401 */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json");
        response.getHeaders().add("X-Auth-Error", message);

        String body = String.format(
                "{\"error\":\"AUTHENTICATION_REQUIRED\",\"message\":\"%s\",\"timestamp\":\"%s\",\"path\":\"%s\"}",
                message,
                java.time.LocalDateTime.now(),
                exchange.getRequest().getPath().value()
        );

        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }

    /** 503 */
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

    /** Ejecuta después del filtro de servicios internos */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
