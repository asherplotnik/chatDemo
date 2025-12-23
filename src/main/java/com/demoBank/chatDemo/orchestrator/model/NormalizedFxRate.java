package com.demoBank.chatDemo.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Normalized FX rate information (foreign accounts only).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NormalizedFxRate {
    
    private String baseCurrency;
    
    private String quoteCurrency;
    
    private Double rate;
    
    private String rateTimestamp;
    
    private String rateType;
    
    private String source;
    
    private String appliedTo;
    
    private Double convertedAmountIls;
    
    /**
     * Whether rate is final (null if not applicable)
     */
    private Boolean isFinal;
}
