package com.demoBank.chatDemo.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Normalized counterparty information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NormalizedCounterparty {
    
    private String name;
    
    /**
     * Bank name (null if not applicable)
     */
    private String bankName;
    
    /**
     * Masked account number (null if not applicable)
     */
    private String accountMasked;
}
