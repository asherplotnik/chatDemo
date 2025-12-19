package com.demoBank.chatDemo.translation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for Groq API chat completions.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroqApiResponse {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("object")
    private String object;
    
    @JsonProperty("created")
    private Long created;
    
    @JsonProperty("model")
    private String model;
    
    @JsonProperty("choices")
    private List<Choice> choices;
    
    @JsonProperty("usage")
    private Usage usage;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Choice {
        @JsonProperty("index")
        private Integer index;
        
        @JsonProperty("message")
        private Message message;
        
        @JsonProperty("finish_reason")
        private String finishReason;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Message {
        @JsonProperty("role")
        private String role;
        
        @JsonProperty("content")
        private String content;
        
        @JsonProperty("tool_calls")
        private List<ToolCall> toolCalls;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ToolCall {
        @JsonProperty("id")
        private String id;
        
        @JsonProperty("type")
        private String type; // "function"
        
        @JsonProperty("function")
        private FunctionCall function;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FunctionCall {
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("arguments")
        private String arguments; // JSON string
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;
        
        @JsonProperty("completion_tokens")
        private Integer completionTokens;
        
        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }
    
    /**
     * Extracts the content from the response (generic method for any type of response).
     * 
     * @return Response content or null if not available
     */
    public String getContent() {
        if (choices != null && !choices.isEmpty()) {
            Choice firstChoice = choices.get(0);
            if (firstChoice != null && firstChoice.getMessage() != null) {
                return firstChoice.getMessage().getContent();
            }
        }
        return null;
    }
    
    /**
     * Extracts tool calls from the response.
     * 
     * @return List of tool calls or empty list if not available
     */
    public List<ToolCall> getToolCalls() {
        if (choices != null && !choices.isEmpty()) {
            Choice firstChoice = choices.get(0);
            if (firstChoice != null && firstChoice.getMessage() != null) {
                return firstChoice.getMessage().getToolCalls();
            }
        }
        return List.of();
    }
    
    /**
     * Checks if the response contains tool calls.
     * 
     * @return true if tool calls are present
     */
    public boolean hasToolCalls() {
        List<ToolCall> toolCalls = getToolCalls();
        return toolCalls != null && !toolCalls.isEmpty();
    }
    
    /**
     * Extracts the translated text from the response.
     * 
     * @return Translated text or null if not available
     */
    public String getTranslatedText() {
        return getContent();
    }
}
