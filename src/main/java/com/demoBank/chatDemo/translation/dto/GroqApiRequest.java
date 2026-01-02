package com.demoBank.chatDemo.translation.dto;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
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
    
    @JsonProperty("tools")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<Tool> tools;
    
    @JsonProperty("tool_choice")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Object toolChoice; // Can be "none", "auto", "required" (string values only for Groq API)
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Message {
        @JsonProperty("role")
        private String role;
        
        @JsonProperty("content")
        private String content;
        
        // tool_calls should only be present in assistant messages (responses), never in system/user messages (requests)
        // Ignore the field by default, use custom getter to conditionally include it
        @JsonIgnore
        private List<ToolCall> toolCalls;
        
        // Custom getter to exclude tool_calls for system and user roles
        @JsonGetter("tool_calls")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public List<ToolCall> getToolCalls() {
            // Only include tool_calls for assistant role (responses), never for system/user (requests)
            if ("assistant".equals(role) && toolCalls != null) {
                return toolCalls;
            }
            return null;
        }
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Tool {
        @JsonProperty("type")
        private String type; // "function"
        
        @JsonProperty("function")
        private Function function;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Function {
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("description")
        private String description;
        
        @JsonProperty("parameters")
        private Object parameters; // JSON Schema object
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
}
