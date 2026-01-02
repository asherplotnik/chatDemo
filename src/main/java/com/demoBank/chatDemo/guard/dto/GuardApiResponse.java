package com.demoBank.chatDemo.guard.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for guard API calls.
 * Parses JSON response from LLM security guard checks.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuardApiResponse {
    
    @JsonProperty("isSafe")
    private Boolean isSafe;
    
    @JsonProperty("promptInjectionDetected")
    private Boolean promptInjectionDetected;
    
    @JsonProperty("maliciousIntentDetected")
    private Boolean maliciousIntentDetected;
    
    @JsonProperty("unpermittedActionDetected")
    private Boolean unpermittedActionDetected;
    
    @JsonProperty("riskScore")
    private Double riskScore;
    
    @JsonProperty("confidence")
    private Double confidence;
    
    @JsonProperty("rejectionReason")
    private String rejectionReason;
}
