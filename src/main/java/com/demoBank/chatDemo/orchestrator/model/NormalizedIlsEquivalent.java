package com.demoBank.chatDemo.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ILS equivalent amounts (foreign accounts only).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NormalizedIlsEquivalent {
    
    private Double totalDebitsIls;
    
    private Double totalCreditsIls;
    
    private String fxMethod;
}
