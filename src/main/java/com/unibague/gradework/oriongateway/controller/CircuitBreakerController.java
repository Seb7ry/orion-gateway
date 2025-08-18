package com.unibague.gradework.oriongateway.controller;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Controlador para gestionar Circuit Breakers
 * Útil para debugging y administración
 */
@Slf4j
@RestController
@RequestMapping("/admin/circuit-breaker")
public class CircuitBreakerController {

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * Ver estado de todos los circuit breakers
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getCircuitBreakerStatus() {
        Map<String, Object> status = new HashMap<>();

        circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb -> {
            Map<String, Object> cbInfo = new HashMap<>();
            cbInfo.put("state", cb.getState().toString());
            cbInfo.put("failureRate", cb.getMetrics().getFailureRate() + "%");
            cbInfo.put("bufferedCalls", cb.getMetrics().getNumberOfBufferedCalls());
            cbInfo.put("failedCalls", cb.getMetrics().getNumberOfFailedCalls());
            cbInfo.put("successfulCalls", cb.getMetrics().getNumberOfSuccessfulCalls());
            cbInfo.put("lastUpdated", LocalDateTime.now());

            status.put(cb.getName(), cbInfo);
        });

        return ResponseEntity.ok(status);
    }

    /**
     * Resetear un circuit breaker específico
     */
    @PostMapping("/reset/{circuitBreakerName}")
    public ResponseEntity<Map<String, String>> resetCircuitBreaker(@PathVariable String circuitBreakerName) {
        try {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName);
            circuitBreaker.reset();

            log.info("Circuit breaker '{}' has been reset", circuitBreakerName);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Circuit breaker '" + circuitBreakerName + "' reset successfully");
            response.put("newState", circuitBreaker.getState().toString());
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to reset circuit breaker '{}': {}", circuitBreakerName, e.getMessage());

            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to reset circuit breaker");
            error.put("circuitBreaker", circuitBreakerName);
            error.put("reason", e.getMessage());

            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Forzar apertura de un circuit breaker (para testing)
     */
    @PostMapping("/open/{circuitBreakerName}")
    public ResponseEntity<Map<String, String>> openCircuitBreaker(@PathVariable String circuitBreakerName) {
        try {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName);
            circuitBreaker.transitionToOpenState();

            log.warn("Circuit breaker '{}' has been manually opened", circuitBreakerName);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Circuit breaker '" + circuitBreakerName + "' opened manually");
            response.put("newState", circuitBreaker.getState().toString());
            response.put("timestamp", LocalDateTime.now().toString());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to open circuit breaker");
            error.put("circuitBreaker", circuitBreakerName);
            error.put("reason", e.getMessage());

            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Resetear TODOS los circuit breakers
     */
    @PostMapping("/reset-all")
    public ResponseEntity<Map<String, Object>> resetAllCircuitBreakers() {
        Map<String, Object> results = new HashMap<>();
        int resetCount = 0;

        for (CircuitBreaker cb : circuitBreakerRegistry.getAllCircuitBreakers()) {
            try {
                cb.reset();
                results.put(cb.getName(), "RESET_SUCCESS");
                resetCount++;
            } catch (Exception e) {
                results.put(cb.getName(), "RESET_FAILED: " + e.getMessage());
            }
        }

        log.info("Reset {} circuit breakers", resetCount);

        Map<String, Object> response = new HashMap<>();
        response.put("totalReset", resetCount);
        response.put("details", results);
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }
}