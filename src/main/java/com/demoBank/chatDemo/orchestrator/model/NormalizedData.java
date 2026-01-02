package com.demoBank.chatDemo.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Normalized data structure - unified canonical model for all banking domains.
 * 
 * Converts different API response formats into a consistent structure
 * for downstream processing (computation, drafting).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NormalizedData {
    
    /**
     * Domain type (e.g., "current-accounts", "credit-cards", "loans", etc.)
     */
    private String domain;
    
    /**
     * List of normalized entities (accounts, cards, loans, etc.)
     */
    private List<NormalizedEntity> entities;
    
    /**
     * Metadata from original API response
     */
    private NormalizedMetadata metadata;
}
