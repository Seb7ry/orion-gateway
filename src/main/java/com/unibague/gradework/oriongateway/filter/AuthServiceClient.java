package com.unibague.gradework.oriongateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;

/**
 * Client for communicating with the Authentication Service
 * Handles token validation and user context retrieval
 */
@Slf4j
@Component
public class AuthServiceClient {

    private final WebClient webClient;

    @Value("${auth.service.validation.url:http://orion-auth:8091/auth/validate}")
    private String authValidationUrl;

    public AuthServiceClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
    }

    /**
     * Validate JWT token with authentication service
     *
     * @param token JWT token to validate
     * @return Mono containing validation result with user context
     */
    @SuppressWarnings("unchecked")
    public Mono<Map> validateToken(String token) {
        log.debug("Validating token with auth service: {}", authValidationUrl);

        return webClient.post()
                .uri(authValidationUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(5))
                .retryWhen(Retry.fixedDelay(2, Duration.ofMillis(500))
                        .filter(throwable -> !(throwable instanceof WebClientResponseException.Unauthorized)))
                .doOnSuccess(result -> log.debug("Token validation successful: {}", result.get("userId")))
                .doOnError(error -> log.warn("Token validation failed: {}", error.getMessage()))
                .onErrorReturn(createErrorResponse("Token validation failed"));
    }

    /**
     * Create standardized error response for failed validations
     */
    private Map<String, Object> createErrorResponse(String message) {
        return Map.of(
                "valid", false,
                "error", message,
                "timestamp", java.time.LocalDateTime.now()
        );
    }
}