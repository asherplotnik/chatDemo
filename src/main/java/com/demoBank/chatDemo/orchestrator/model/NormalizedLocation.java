package com.demoBank.chatDemo.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Normalized location information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NormalizedLocation {
    
    private String city;
    
    private String country;
}
