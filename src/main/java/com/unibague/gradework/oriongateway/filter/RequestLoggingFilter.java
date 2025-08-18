package com.unibague.gradework.oriongateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final String REQUEST_START_TIME = "requestStartTime";
    private static final String REQUEST_ID = "requestId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String requestId = UUID.randomUUID().toString().substring(0, 8);
        LocalDateTime startTime = LocalDateTime.now();

        exchange.getAttributes().put(REQUEST_ID, requestId);
        exchange.getAttributes().put(REQUEST_START_TIME, startTime);

        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().name();
        String path = request.getPath().value();
        String clientIp = getClientIp(request);

        log.info("🔍 [{}] {} {} - Client: {}", requestId, method, path, clientIp);

        exchange.getResponse().getHeaders().add("X-Request-ID", requestId);
        exchange.getResponse().getHeaders().add("X-Gateway-Timestamp", startTime.toString());

        return chain.filter(exchange)
                .doOnSuccess(aVoid -> logSuccessfulResponse(exchange))
                .doOnError(throwable -> logErrorResponse(exchange, throwable));
    }

    private void logSuccessfulResponse(ServerWebExchange exchange) {
        String requestId = exchange.getAttribute(REQUEST_ID);
        LocalDateTime startTime = exchange.getAttribute(REQUEST_START_TIME);
        long duration = ChronoUnit.MILLIS.between(startTime, LocalDateTime.now());

        int statusCode = exchange.getResponse().getStatusCode() != null ?
                exchange.getResponse().getStatusCode().value() : 0;

        log.info("✅ [{}] Response: {} - Duration: {}ms", requestId, statusCode, duration);

        if (duration > 5000) {
            log.warn("⚠️  [{}] SLOW REQUEST - Duration: {}ms", requestId, duration);
        }
    }

    private void logErrorResponse(ServerWebExchange exchange, Throwable throwable) {
        String requestId = exchange.getAttribute(REQUEST_ID);
        LocalDateTime startTime = exchange.getAttribute(REQUEST_START_TIME);
        long duration = ChronoUnit.MILLIS.between(startTime, LocalDateTime.now());

        String path = exchange.getRequest().getPath().value();

        log.error("❌ [{}] ERROR: {} - Path: {} - Duration: {}ms",
                requestId, throwable.getMessage(), path, duration);
    }

    private String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddress() != null ?
                request.getRemoteAddress().getAddress().getHostAddress() : "Unknown";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}