package com.unibague.gradework.oriongateway.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * Standard error response model for API Gateway
 * Used for consistent error formatting across all endpoints
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    /** Error type or code */
    private String error;
    
    /** Human-readable error message */
    private String message;

    /** Timestamp when the error occurred */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    /** Request path where the error occurred */
    private String path;
    
    /** HTTP status code */
    private int status;

    /** Unique request identifier for tracing */
    private String requestId;
    
    /** Gateway identifier */
    private String gateway;
}