package com.demoBank.chatDemo.translation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for Groq API chat completions.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroqApiRequest {
    
    @JsonProperty("messages")
    private List<Message> messages;
    
    @JsonProperty("model")
    private String model;
    
    @JsonProperty("temperature")
    private Double temperature;
    
    @JsonProperty("max_completion_tokens")
    private Integer maxCompletionTokens;
    
    @JsonProperty("top_p")
    private Double topP;
    
    @JsonProperty("stream")
    private Boolean stream;
    
    @JsonProperty("stop")
    private List<String> stop;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Message {
        @JsonProperty("role")
        private String role;
        
        @JsonProperty("content")
        private String content;
    }
}
