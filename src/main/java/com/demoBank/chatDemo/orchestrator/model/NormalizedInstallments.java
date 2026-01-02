package com.demoBank.chatDemo.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Normalized installment information (credit cards only).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NormalizedInstallments {
    
    private Boolean isInstallment;
    
    /**
     * Plan type: "EQUAL", etc. (null if not applicable)
     */
    private String planType;
    
    /**
     * Total number of installments (null if not applicable)
     */
    private Integer totalInstallments;
    
    /**
     * Current installment number (null if not applicable)
     */
    private Integer currentInstallment;
    
    /**
     * Installment amount (null if not applicable)
     */
    private Double installmentAmount;
}
