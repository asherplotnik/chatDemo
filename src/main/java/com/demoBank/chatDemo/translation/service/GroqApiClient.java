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
    private String model;
    
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
     * Calls Groq API to translate Hebrew text to English.
     * 
     * @param systemPrompt System prompt for translation
     * @param hebrewText Hebrew text to translate
     * @return GroqApiResponse with translation
     * @throws RuntimeException if API call fails
     */
    public GroqApiResponse translate(String systemPrompt, String hebrewText) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Groq API key is not configured. Set groq.api.key in application.yaml");
        }
        
        GroqApiRequest request = GroqApiRequest.builder()
                .messages(List.of(
                        GroqApiRequest.Message.builder()
                                .role("system")
                                .content(systemPrompt)
                                .build(),
                        GroqApiRequest.Message.builder()
                                .role("user")
                                .content(hebrewText)
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
            log.debug("Calling Groq API - model: {}, text length: {}", model, hebrewText.length());
            
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
}
