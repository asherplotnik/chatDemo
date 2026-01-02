package com.demoBank.chatDemo.orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for intent extraction API calls.
 * Parses JSON response from LLM intent extraction.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntentExtractionResponse {
    
    @JsonProperty("intents")
    private List<IntentData> intents;
    
    @JsonProperty("usedDefault")
    private Boolean usedDefault;
    
    @JsonProperty("defaultReason")
    private String defaultReason;
    
    @JsonProperty("confidence")
    private Double confidence;
    
    @JsonProperty("needsClarification")
    private Boolean needsClarification;
    
    @JsonProperty("clarificationNeeded")
    private String clarificationNeeded;
    
    /**
     * Individual intent data structure.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class IntentData {
        @JsonProperty("domain")
        private String domain;
        
        @JsonProperty("metric")
        private String metric;
        
        @JsonProperty("timeRangeHint")
        private String timeRangeHint;
        
        @JsonProperty("entityHints")
        private EntityHints entityHints;
        
        @JsonProperty("parameters")
        private Map<String, Object> parameters;
    }
    
    /**
     * Entity hints structure.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EntityHints {
        @JsonProperty("accountIds")
        private List<String> accountIds;
        
        @JsonProperty("cardIds")
        private List<String> cardIds;
        
        @JsonProperty("otherEntities")
        private Map<String, List<String>> otherEntities;
    }
}
