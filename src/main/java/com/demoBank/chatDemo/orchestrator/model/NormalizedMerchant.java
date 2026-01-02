package com.demoBank.chatDemo.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Normalized merchant information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NormalizedMerchant {
    
    private String name;
    
    /**
     * Merchant category code (MCC)
     * Null if not available
     */
    private String mcc;
    
    /**
     * Merchant location
     * Null if not available
     */
    private NormalizedLocation location;
}
