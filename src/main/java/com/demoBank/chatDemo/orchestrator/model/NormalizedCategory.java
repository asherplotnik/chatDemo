package com.demoBank.chatDemo.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Normalized category information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NormalizedCategory {
    
    private String code;
    
    private String label;
}
