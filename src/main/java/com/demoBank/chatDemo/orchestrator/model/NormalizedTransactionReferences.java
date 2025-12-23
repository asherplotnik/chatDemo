package com.demoBank.chatDemo.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Normalized transaction references.
 * 
 * Different APIs use different reference fields, all unified here.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NormalizedTransactionReferences {
    
    /**
     * Bank reference number (null if not available)
     */
    private String bankReference;
    
    /**
     * End-to-end ID (null if not available)
     */
    private String endToEndId;
    
    /**
     * Authorization code (null if not available)
     */
    private String authorizationCode;
    
    /**
     * Retrieval reference number (RRN) (null if not available)
     */
    private String rrn;
    
    /**
     * Issuer reference (null if not available)
     */
    private String issuerReference;
}
