package com.demoBank.chatDemo.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Normalized metadata from API response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NormalizedMetadata {
    
    private String schemaVersion;
    
    /**
     * Currency decimals map (e.g., {"ILS": 2, "USD": 2})
     */
    private Map<String, Integer> currencyDecimals;
    
    /**
     * Disclaimers list
     */
    private List<String> disclaimers;
}
