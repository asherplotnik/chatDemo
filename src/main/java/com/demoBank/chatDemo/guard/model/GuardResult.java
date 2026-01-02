package com.demoBank.chatDemo.guard.model;

import lombok.Builder;
import lombok.Data;

/**
 * Result of security guard checks for prompt injections and malicious intent.
 */
@Data
@Builder
public class GuardResult {
    
    /**
     * Whether the message passed all security checks.
     */
    private boolean isSafe;
    
    /**
     * Whether prompt injection patterns were detected.
     */
    private boolean promptInjectionDetected;
    
    /**
     * Whether malicious intent patterns were detected.
     */
    private boolean maliciousIntentDetected;
    
    /**
     * Whether unpermitted actions (beyond data fetching) were detected.
     * Examples: creating accounts, modifying permissions, producing checkbooks, etc.
     */
    private boolean unpermittedActionDetected;
    
    /**
     * Risk score (0.0 = safe, 1.0 = highly dangerous).
     */
    private double riskScore;
    
    /**
     * Reason for rejection if not safe.
     */
    private String rejectionReason;
    
    /**
     * Confidence level of the detection (0.0 to 1.0).
     */
    private double confidence;
}
