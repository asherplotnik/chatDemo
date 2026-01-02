package com.demoBank.chatDemo.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Normalized enrichment data.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NormalizedEnrichment {
    
    /**
     * Normalized description
     */
    private String normalizedDescription;
    
    /**
     * Tags (null if not available)
     */
    private List<String> tags;
}
