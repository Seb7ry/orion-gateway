package com.unibague.gradework.oriongateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Global exception handler for the API Gateway
 * Provides centralized error handling and consistent error responses
 */
@Slf4j
@Component
@Order(-1)
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    /**
     * Constructor initializing the ObjectMapper with time module
     */
    public GlobalExceptionHandler() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Main exception handling method
     * @param exchange the web exchange
     * @param ex the exception that occurred
     * @return Mono<Void> representing the completion of error handling
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {

        HttpStatus status = determineHttpStatus(ex);
        String path = exchange.getRequest().getPath().value();
        String requestId = exchange.getAttribute("requestId");

        logError(ex, path, requestId, status);

        ErrorResponse errorResponse = createErrorResponse(ex, status, path, requestId);

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        exchange.getResponse().getHeaders().add("X-Error-Handler", "Gateway-Global");
        exchange.getResponse().getHeaders().add("X-Request-ID", requestId != null ? requestId : "unknown");

        String jsonResponse;
        try {
            jsonResponse = objectMapper.writeValueAsString(errorResponse);
        } catch (JsonProcessingException jsonEx) {
            log.error("Error serializing error response: {}", jsonEx.getMessage());
            jsonResponse = createFallbackErrorJson(ex, status, path);
        }

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(jsonResponse.getBytes());
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * Determines appropriate HTTP status based on exception type
     * @param ex the exception
     * @return appropriate HttpStatus
     */
    private HttpStatus determineHttpStatus(Throwable ex) {
        if (ex instanceof IllegalArgumentException) {
            return HttpStatus.BAD_REQUEST;
        } else if (ex instanceof java.net.ConnectException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        } else if (ex instanceof java.util.concurrent.TimeoutException) {
            return HttpStatus.GATEWAY_TIMEOUT;
        } else if (ex instanceof java.net.UnknownHostException) {
            return HttpStatus.BAD_GATEWAY;
        } else if (ex instanceof RuntimeException &&
                ex.getMessage() != null &&
                ex.getMessage().toLowerCase().contains("not found")) {
            return HttpStatus.NOT_FOUND;
        } else if (ex instanceof RuntimeException) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        } else {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    /**
     * Logs errors with appropriate level based on status
     * @param ex the exception
     * @param path request path
     * @param requestId unique request identifier
     * @param status HTTP status
     */
    private void logError(Throwable ex, String path, String requestId, HttpStatus status) {
        if (status == HttpStatus.BAD_REQUEST) {
            log.warn("Validation error: {}", ex.getMessage());
        } else if (status == HttpStatus.NOT_FOUND) {
            log.warn("Resource not found: {}", ex.getMessage());
        } else if (status.is5xxServerError()) {
            log.error("Internal gateway error: {}", ex.getMessage(), ex);
        } else {
            log.error("Unexpected gateway error: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Creates standardized error response
     * @param ex the exception
     * @param status HTTP status
     * @param path request path
     * @param requestId unique request identifier
     * @return ErrorResponse object
     */
    private ErrorResponse createErrorResponse(Throwable ex, HttpStatus status, String path, String requestId) {
        String errorType = determineErrorType(ex, status);
        String message = determineErrorMessage(ex, status);

        return ErrorResponse.builder()
                .error(errorType)
                .message(message)
                .timestamp(LocalDateTime.now())
                .path(path)
                .status(status.value())
                .requestId(requestId != null ? requestId : "unknown")
                .gateway("orion-gateway")
                .build();
    }

    /**
     * Determines error type based on exception and status
     * @param ex the exception
     * @param status HTTP status
     * @return error type string
     */
    private String determineErrorType(Throwable ex, HttpStatus status) {
        if (ex instanceof IllegalArgumentException) {
            return "VALIDATION_ERROR";
        } else if (status == HttpStatus.NOT_FOUND) {
            return "NOT_FOUND";
        } else if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            return "SERVICE_UNAVAILABLE";
        } else if (status == HttpStatus.GATEWAY_TIMEOUT) {
            return "GATEWAY_TIMEOUT";
        } else if (status == HttpStatus.BAD_GATEWAY) {
            return "BAD_GATEWAY";
        } else if (ex instanceof RuntimeException) {
            return "INTERNAL_ERROR";
        } else {
            return "UNEXPECTED_ERROR";
        }
    }

    /**
     * Determines user-friendly error message
     * @param ex the exception
     * @param status HTTP status
     * @return user-friendly error message
     */
    private String determineErrorMessage(Throwable ex, HttpStatus status) {
        if (ex instanceof IllegalArgumentException) {
            return ex.getMessage();
        } else if (status == HttpStatus.NOT_FOUND) {
            return "The requested resource was not found";
        } else if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            return "Service temporarily unavailable. Please try again later.";
        } else if (status == HttpStatus.GATEWAY_TIMEOUT) {
            return "Gateway timeout. The service is taking too long to respond.";
        } else if (status == HttpStatus.BAD_GATEWAY) {
            return "Bad gateway. Unable to connect to the downstream service.";
        } else if (ex instanceof RuntimeException &&
                ex.getMessage() != null &&
                ex.getMessage().toLowerCase().contains("not found")) {
            return ex.getMessage();
        } else if (ex instanceof RuntimeException) {
            return "An unexpected error occurred: " + ex.getMessage();
        } else {
            return "Something went wrong. Please try again later.";
        }
    }

    /**
     * Creates fallback JSON error response when serialization fails
     * @param ex the exception
     * @param status HTTP status
     * @param path request path
     * @return JSON string for error response
     */
    private String createFallbackErrorJson(Throwable ex, HttpStatus status, String path) {
        return String.format(
                "{\"error\":\"SERIALIZATION_ERROR\",\"message\":\"Error processing gateway response\",\"timestamp\":\"%s\",\"path\":\"%s\",\"status\":%d,\"gateway\":\"orion-gateway\"}",
                LocalDateTime.now(),
                path,
                status.value()
        );
    }
}