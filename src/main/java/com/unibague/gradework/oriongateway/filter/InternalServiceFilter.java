package com.unibague.gradework.oriongateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Filter para manejar comunicación interna service-to-service
 * Bypasa autenticación JWT para servicios internos confiables
 */
@Slf4j
@Component
public class InternalServiceFilter implements GlobalFilter, Ordered {

    @Value("${gateway.service.token:auth-service-token-secure-2025}")
    private String validServiceToken;

    // Servicios internos confiables
    private static final List<String> TRUSTED_SERVICES = List.of(
            "orion-auth",
            "orion-user",
            "orion-program",
            "orion-document"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String requestId = exchange.getAttribute("requestId");

        // Si no hay requestId, generar uno temporal para debug
        if (requestId == null) {
            requestId = "temp-" + System.currentTimeMillis();
            exchange.getAttributes().put("requestId", requestId);
        }

        // LOGGING DETALLADO PARA DEBUG
        log.info("=== INTERNAL SERVICE FILTER DEBUG ===");
        log.info("Path: {}", path);
        log.info("Request ID: {}", requestId);

        // Verificar headers
        String serviceRequest = exchange.getRequest().getHeaders().getFirst("X-Service-Request");
        String serviceName = exchange.getRequest().getHeaders().getFirst("X-Service-Name");
        String serviceToken = exchange.getRequest().getHeaders().getFirst("X-Service-Token");

        log.info("X-Service-Request: {}", serviceRequest);
        log.info("X-Service-Name: {}", serviceName);
        log.info("X-Service-Token: {}", serviceToken != null ? serviceToken.substring(0, 10) + "..." : "null");

        if ("true".equals(serviceRequest) && isValidInternalService(serviceName, serviceToken)) {
            log.info("🔧 [{}] INTERNAL SERVICE DETECTED: {} → {}", requestId, serviceName, path);

            // Enriquecer headers para servicios downstream
            ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();
            requestBuilder.header("X-Gateway-Validated", "true");
            requestBuilder.header("X-Internal-Request", "true");
            requestBuilder.header("X-Source-Service", serviceName);
            requestBuilder.header("X-Gateway-Service", "orion-gateway");

            // IMPORTANTE: No pasar por el filtro JWT
            exchange.getAttributes().put("SKIP_JWT_FILTER", true);

            log.info("🔧 [{}] SKIP_JWT_FILTER set to true", requestId);

            ServerWebExchange modifiedExchange = exchange.mutate()
                    .request(requestBuilder.build())
                    .build();

            return chain.filter(modifiedExchange);
        } else {
            log.info("⚠️ [{}] NOT an internal service request, continuing with normal flow", requestId);
        }

        // Continuar con flujo normal (pasará por filtro JWT)
        return chain.filter(exchange);
    }

    /**
     * Validar credenciales de servicio interno
     */
    private boolean isValidInternalService(String serviceName, String serviceToken) {
        if (serviceName == null || serviceToken == null) {
            log.debug("Missing service credentials");
            return false;
        }

        // Verificar si el servicio está en la lista de confianza
        if (!TRUSTED_SERVICES.contains(serviceName)) {
            log.warn("❌ Unknown service attempting internal communication: {}", serviceName);
            return false;
        }

        // Validar token del servicio
        if (!validServiceToken.equals(serviceToken)) {
            log.warn("❌ Invalid service token from: {}", serviceName);
            return false;
        }

        log.debug("✅ Valid internal service: {}", serviceName);
        return true;
    }

    /**
     * Ejecutar ANTES del filtro JWT (orden más alto)
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 50;
    }
}