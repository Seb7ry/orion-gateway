package com.unibague.gradework.oriongateway.controller;

import com.unibague.gradework.oriongateway.exception.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Fallback controller for handling service unavailability
 * Provides graceful degradation when microservices are down
 */
@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    /**
     * Fallback endpoint for user service when unavailable
     * @param exchange the web exchange context
     * @return Mono with error response indicating service unavailability
     */
    @GetMapping("/user/**")
    @PostMapping("/user/**")
    public Mono<ResponseEntity<ErrorResponse>> userServiceFallback(ServerWebExchange exchange) {
        return createFallbackResponse(
                "SERVICE_UNAVAILABLE",
                "User service is temporarily unavailable",
                exchange
        );
    }

    /**
     * Fallback endpoint for authentication service when unavailable
     * @param exchange the web exchange context
     * @return Mono with error response indicating service unavailability
     */
    @GetMapping("/auth/**")
    @PostMapping("/auth/**")
    public Mono<ResponseEntity<ErrorResponse>> authServiceFallback(ServerWebExchange exchange) {
        return createFallbackResponse(
                "SERVICE_UNAVAILABLE",
                "Authentication service is temporarily unavailable",
                exchange
        );
    }

    /**
     * Fallback endpoint for program service when unavailable
     * @param exchange the web exchange context
     * @return Mono with error response indicating service unavailability
     */
    @GetMapping("/program/**")
    @PostMapping("/program/**")
    public Mono<ResponseEntity<ErrorResponse>> programServiceFallback(ServerWebExchange exchange) {
        return createFallbackResponse(
                "SERVICE_UNAVAILABLE",
                "Program service is temporarily unavailable",
                exchange
        );
    }

    /**
     * Fallback endpoint for document service when unavailable
     * @param exchange the web exchange context
     * @return Mono with error response indicating service unavailability
     */
    @GetMapping("/document/**")
    @PostMapping("/document/**")
    public Mono<ResponseEntity<ErrorResponse>> documentServiceFallback(ServerWebExchange exchange) {
        return createFallbackResponse(
                "SERVICE_UNAVAILABLE",
                "Document service is temporarily unavailable",
                exchange
        );
    }

    /**
     * Fallback endpoint for drive service when unavailable
     * @param exchange the web exchange context
     * @return Mono with error response indicating service unavailability
     */
    @GetMapping("/drive/**")
    @PostMapping("/drive/**")
    public Mono<ResponseEntity<ErrorResponse>> driveServiceFallback(ServerWebExchange exchange) {
        return createFallbackResponse(
                "SERVICE_UNAVAILABLE",
                "Drive service is temporarily unavailable",
                exchange
        );
    }

    /**
     * Creates a standardized fallback response
     * @param errorType the type of error
     * @param message descriptive error message
     * @param exchange the web exchange context
     * @return Mono with formatted error response
     */
    private Mono<ResponseEntity<ErrorResponse>> createFallbackResponse(
            String errorType,
            String message,
            ServerWebExchange exchange) {

        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();
        String requestId = exchange.getAttribute("requestId");

        log.warn("Fallback triggered for {} {} - RequestID: {} - Reason: {}",
                method, path, requestId, message);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .error(errorType)
                .message(message)
                .timestamp(LocalDateTime.now())
                .path(path)
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .requestId(requestId != null ? requestId : "unknown")
                .gateway("orion-gateway")
                .build();

        return Mono.just(
                ResponseEntity
                        .status(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("X-Fallback-Triggered", "true")
                        .header("X-Request-ID", requestId != null ? requestId : "unknown")
                        .header("Retry-After", "300")
                        .body(errorResponse)
        );
    }
}