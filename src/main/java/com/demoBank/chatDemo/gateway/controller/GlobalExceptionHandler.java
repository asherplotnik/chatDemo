package com.demoBank.chatDemo.gateway.controller;

import com.demoBank.chatDemo.gateway.dto.ChatResponse;
import com.demoBank.chatDemo.guard.exception.MaliciousContentException;
import com.demoBank.chatDemo.gateway.exception.MissingCustomerIdException;
import com.demoBank.chatDemo.gateway.exception.RateLimitExceededException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for the Gateway.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        
        log.warn("Validation error: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", message));
    }
    
    @ExceptionHandler(MissingCustomerIdException.class)
    public ResponseEntity<ErrorResponse> handleMissingCustomerId(MissingCustomerIdException ex) {
        log.error("Missing customer ID: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("MISSING_CUSTOMER_ID", ex.getMessage()));
    }
    
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(RateLimitExceededException ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ErrorResponse("RATE_LIMIT_EXCEEDED", ex.getMessage()));
    }
    
    @ExceptionHandler(MaliciousContentException.class)
    public ResponseEntity<ChatResponse> handleMaliciousContent(MaliciousContentException ex) {
        log.warn("Malicious content detected: {}", ex.getMessage());
        
        // Return default answer in the detected language
        String defaultAnswer;
        if (ex.isHebrew()) {
            defaultAnswer = ".מצטער אני לא יכול למלא את בקשה זו";
        } else {
            defaultAnswer = "Sorry. I can't comply with this request.";
        }
        
        // Create ChatResponse with default answer
        ChatResponse response = ChatResponse.builder()
                .answer(defaultAnswer)
                .correlationId(null) // Correlation ID not available in exception handler context
                .explanation("Security check detected potentially malicious content.")
                .build();
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(response);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
    }
    
    private record ErrorResponse(String code, String message) {}
}
