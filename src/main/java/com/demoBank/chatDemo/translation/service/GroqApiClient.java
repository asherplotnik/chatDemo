package com.demoBank.chatDemo.translation.service;

import com.demoBank.chatDemo.translation.dto.GroqApiRequest;
import com.demoBank.chatDemo.translation.dto.GroqApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Client for interacting with Groq API.
 * Handles HTTP communication with Groq's chat completions endpoint.
 */
@Slf4j
@Service
public class GroqApiClient {
    
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String DEFAULT_MODEL = "llama-3.3-70b-versatile";
    
    private final RestClient restClient;
    
    @Value("${groq.api.key}")
    private String apiKey;
    
    @Value("${groq.api.model:" + DEFAULT_MODEL + "}")
    private String defaultModel; // Kept for backward compatibility
    
    @Value("${groq.api.temperature:0.3}")
    private Double temperature;
    
    @Value("${groq.api.max-completion-tokens:1024}")
    private Integer maxCompletionTokens;
    
    public GroqApiClient() {
        this.restClient = RestClient.builder()
                .baseUrl(GROQ_API_URL)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
    
    /**
     * Generic method to call Groq API with any system prompt and user message.
     * Uses default model if not specified.
     * 
     * @param systemPrompt System prompt for the task
     * @param userMessage User message to process
     * @return GroqApiResponse with the result
     * @throws RuntimeException if API call fails
     */
    public GroqApiResponse callGroqApi(String systemPrompt, String userMessage) {
        return callGroqApi(systemPrompt, userMessage, defaultModel);
    }
    
    /**
     * Generic method to call Groq API with any system prompt, user message, and model.
     * 
     * @param systemPrompt System prompt for the task
     * @param userMessage User message to process
     * @param model Model to use for the API call
     * @return GroqApiResponse with the result
     * @throws RuntimeException if API call fails
     */
    public GroqApiResponse callGroqApi(String systemPrompt, String userMessage, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Groq API key is not configured. Set groq.api.key in application.yaml");
        }
        
        if (model == null || model.isBlank()) {
            model = defaultModel;
        }
        
        GroqApiRequest request = GroqApiRequest.builder()
                .messages(List.of(
                        GroqApiRequest.Message.builder()
                                .role("system")
                                .content(systemPrompt)
                                .build(),
                        GroqApiRequest.Message.builder()
                                .role("user")
                                .content(userMessage)
                                .build()
                ))
                .model(model)
                .temperature(temperature)
                .maxCompletionTokens(maxCompletionTokens)
                .topP(1.0)
                .stream(false) // Non-streaming for simplicity
                .stop(null)
                .build();
        
        try {
            log.debug("Calling Groq API - model: {}, message length: {}", model, userMessage.length());
            
            GroqApiResponse response = restClient.post()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(request)
                    .retrieve()
                    .body(GroqApiResponse.class);
            
            if (response == null) {
                throw new RuntimeException("Groq API returned null response");
            }
            
            log.debug("Groq API response received - model: {}, tokens used: {}", 
                    response.getModel(), 
                    response.getUsage() != null ? response.getUsage().getTotalTokens() : "unknown");
            
            return response;
            
        } catch (Exception e) {
            log.error("Error calling Groq API", e);
            throw new RuntimeException("Failed to call Groq API: " + e.getMessage(), e);
        }
    }
    
    /**
     * Calls Groq API with function calling support.
     * Uses default model if not specified.
     * 
     * @param systemPrompt System prompt for the task
     * @param userMessage User message to process
     * @param tools List of tools (functions) available to the model
     * @param toolChoice Tool choice strategy ("none", "auto", or specific tool)
     * @return GroqApiResponse with the result
     * @throws RuntimeException if API call fails
     */
    public GroqApiResponse callGroqApiWithTools(String systemPrompt, String userMessage, 
                                                 List<GroqApiRequest.Tool> tools, Object toolChoice) {
        return callGroqApiWithTools(systemPrompt, userMessage, tools, toolChoice, defaultModel);
    }
    
    /**
     * Calls Groq API with function calling support.
     * 
     * @param systemPrompt System prompt for the task
     * @param userMessage User message to process
     * @param tools List of tools (functions) available to the model
     * @param toolChoice Tool choice strategy ("none", "auto", or specific tool)
     * @param model Model to use for the API call
     * @return GroqApiResponse with the result
     * @throws RuntimeException if API call fails
     */
    public GroqApiResponse callGroqApiWithTools(String systemPrompt, String userMessage, 
                                                 List<GroqApiRequest.Tool> tools, Object toolChoice, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Groq API key is not configured. Set groq.api.key in application.yaml");
        }
        
        if (model == null || model.isBlank()) {
            model = defaultModel;
        }
        
        GroqApiRequest request = GroqApiRequest.builder()
                .messages(List.of(
                        GroqApiRequest.Message.builder()
                                .role("system")
                                .content(systemPrompt)
                                .build(),
                        GroqApiRequest.Message.builder()
                                .role("user")
                                .content(userMessage)
                                .build()
                ))
                .model(model)
                .temperature(temperature)
                .maxCompletionTokens(maxCompletionTokens)
                .topP(1.0)
                .stream(false)
                .stop(null)
                .tools(tools)
                .toolChoice(toolChoice != null ? toolChoice : "auto")
                .build();
        
        try {
            log.debug("Calling Groq API with tools - model: {}, message length: {}, tools: {}", 
                    model, userMessage.length(), tools != null ? tools.size() : 0);
            
            GroqApiResponse response = restClient.post()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(request)
                    .retrieve()
                    .body(GroqApiResponse.class);
            
            if (response == null) {
                throw new RuntimeException("Groq API returned null response");
            }
            
            log.debug("Groq API response received - model: {}, tokens used: {}, hasToolCalls: {}", 
                    response.getModel(), 
                    response.getUsage() != null ? response.getUsage().getTotalTokens() : "unknown",
                    response.hasToolCalls());
            
            return response;
            
        } catch (Exception e) {
            log.error("Error calling Groq API with tools", e);
            throw new RuntimeException("Failed to call Groq API: " + e.getMessage(), e);
        }
    }
    
    /**
     * Calls Groq API to translate Hebrew text to English.
     * 
     * @param systemPrompt System prompt for translation
     * @param hebrewText Hebrew text to translate
     * @return GroqApiResponse with translation
     * @throws RuntimeException if API call fails
     */
    public GroqApiResponse translate(String systemPrompt, String hebrewText) {
        return callGroqApi(systemPrompt, hebrewText);
    }
}
