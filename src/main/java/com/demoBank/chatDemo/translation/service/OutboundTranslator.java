package com.demoBank.chatDemo.translation.service;

import com.demoBank.chatDemo.translation.dto.GroqApiResponse;
import com.demoBank.chatDemo.translation.model.TranslationResult;
import com.demoBank.chatDemo.translation.prompt.TranslationSystemPrompt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Outbound Translator service - translates English to Hebrew.
 * 
 * Responsibilities:
 * - Translate English responses back to Hebrew for customer display
 * - Semantic translation (not literal)
 * - Preserve numbers, dates, currencies, and meanings
 * 
 * Translation is semantic, not literal. Numbers, dates, currencies, and meanings must not change.
 * 
 * Uses TranslationSystemPrompt for semantic translation guidelines.
 * Uses Groq API for actual translation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboundTranslator {
    
    private final GroqApiClient groqApiClient;
    
    @Value("${translation.use-groq-api:false}")
    private boolean useGroqApi;
    
    @Value("${groq.api.model:llama-3.3-70b-versatile}")
    private String translationModel;
    
    /**
     * Gets the system prompt for semantic translation from English to Hebrew.
     * 
     * @return System prompt for translation
     */
    public String getSystemPrompt() {
        return TranslationSystemPrompt.getSystemPromptToHebrew();
    }
    
    /**
     * Gets the condensed version of the system prompt (for API calls with token limits).
     * 
     * @return Condensed system prompt
     */
    public String getCondensedPrompt() {
        return TranslationSystemPrompt.getCondensedSystemPromptToHebrew();
    }
    
    /**
     * Translates English JSON with multiple values to Hebrew JSON.
     * Makes a single API call to translate all values at once.
     * 
     * @param jsonInput JSON string with key-value pairs to translate
     * @param correlationId Correlation ID for logging
     * @return Translated JSON string with same structure
     */
    public String translateJsonToHebrew(String jsonInput, String correlationId) {
        if (jsonInput == null || jsonInput.isBlank()) {
            log.warn("Empty JSON provided for translation - correlationId: {}", correlationId);
            return jsonInput;
        }
        
        log.info("Translating JSON to Hebrew - correlationId: {}, input length: {}, useGroqApi: {}", 
                correlationId, jsonInput.length(), useGroqApi);
        
        if (useGroqApi) {
            return translateJsonWithGroqApi(jsonInput, correlationId);
        } else {
            return createStubJsonTranslation(jsonInput, correlationId);
        }
    }
    
    /**
     * Translates English text to Hebrew (legacy method, kept for compatibility).
     * 
     * @param englishText Original text in English
     * @param correlationId Correlation ID for logging and tracking
     * @return TranslationResult with translated Hebrew text
     */
    public TranslationResult translateToHebrew(String englishText, String correlationId) {
        if (englishText == null || englishText.isBlank()) {
            log.warn("Empty English text provided for translation - correlationId: {}", correlationId);
            return TranslationResult.builder()
                    .originalText(englishText)
                    .translatedText(englishText)
                    .sourceLanguage("en")
                    .targetLanguage("he")
                    .confidence(0.0)
                    .success(false)
                    .errorMessage("Empty text provided")
                    .build();
        }
        
        log.info("Translating English to Hebrew - correlationId: {}, text length: {}, useGroqApi: {}", 
                correlationId, englishText.length(), useGroqApi);
        
        if (useGroqApi) {
            return translateWithGroqApi(englishText, correlationId);
        } else {
            return createStubTranslation(englishText, correlationId);
        }
    }
    
    /**
     * Translates JSON using Groq API.
     * 
     * @param jsonInput JSON string to translate
     * @param correlationId Correlation ID for logging
     * @return Translated JSON string
     */
    private String translateJsonWithGroqApi(String jsonInput, String correlationId) {
        try {
            String systemPrompt = getJsonTranslationPrompt();
            
            GroqApiResponse response = groqApiClient.callGroqApi(systemPrompt, jsonInput, translationModel);
            
            String translatedJson = response.getTranslatedText();
            
            if (translatedJson == null || translatedJson.isBlank()) {
                log.warn("Groq API returned empty JSON translation - correlationId: {}", correlationId);
                return jsonInput; // Return original on error
            }
            
            translatedJson = translatedJson.trim();
            
            log.info("JSON translation completed via Groq API - correlationId: {}, tokens: {}", 
                    correlationId,
                    response.getUsage() != null ? response.getUsage().getTotalTokens() : "unknown");
            
            return translatedJson;
                    
        } catch (Exception e) {
            log.error("Error translating JSON with Groq API - correlationId: {}, error: {}", 
                    correlationId, e.getMessage(), e);
            return jsonInput; // Return original on error
        }
    }
    
    /**
     * Gets system prompt for JSON translation.
     */
    private String getJsonTranslationPrompt() {
        return """
            You are a semantic translator from English to Hebrew for banking customer service.
            
            You will receive a JSON object with key-value pairs. Translate ONLY the VALUES (the text after the colons).
            Keep the KEYS unchanged. Keep the JSON structure intact.
            
            IMPORTANT: Translate ALL values including fields named "EXPLANATION", "ANSWER", and any other text fields.
            The field name "EXPLANATION" is just a key name - you MUST translate its value.
            
            Preserve:
            - Exact numbers, dates, currencies, account numbers in values
            - JSON structure and keys (including "EXPLANATION", "ANSWER", etc.)
            - Natural Hebrew phrasing and professional tone
            - Banking terminology accuracy
            
            Return ONLY the translated JSON object with the same structure. Do not add any text outside the JSON.
            Example:
            Input: {"ANSWER": "Your balance is ₪1,234.56", "EXPLANATION": "I retrieved this from your account", "HEADER": "Date"}
            Output: {"ANSWER": "היתרה שלך היא ₪1,234.56", "EXPLANATION": "אחזרתי את זה מחשבון שלך", "HEADER": "תאריך"}
            """;
    }
    
    /**
     * Creates stub JSON translation for fallback.
     */
    private String createStubJsonTranslation(String jsonInput, String correlationId) {
        log.info("Using stub JSON translation - correlationId: {}", correlationId);
        return "[TRANSLATED JSON] " + jsonInput;
    }
    
    /**
     * Translates English text to Hebrew using Groq API.
     * 
     * @param englishText Original English text
     * @param correlationId Correlation ID for logging
     * @return TranslationResult with translated text
     */
    private TranslationResult translateWithGroqApi(String englishText, String correlationId) {
        try {
            String systemPrompt = getCondensedPrompt();
            
            GroqApiResponse response = groqApiClient.callGroqApi(systemPrompt, englishText, translationModel);
            
            String translatedText = response.getTranslatedText();
            
            if (translatedText == null) {
                log.warn("Groq API returned null translation - correlationId: {}", correlationId);
                return createStubTranslation("TRANSLATION_ERROR", correlationId);
            }
            
            // Clean up the translation (remove any extra whitespace and handle literal quotes)
            translatedText = translatedText.trim();
            
            // Handle case where LLM returns literal "" (double quotes) or '' (single quotes) instead of empty string
            if (translatedText.equals("\"\"") || translatedText.equals("''")) {
                log.info("Groq API returned literal empty string quotes - treating as empty - correlationId: {}", correlationId);
                translatedText = "";
            }
            
            if (translatedText.isEmpty()) {
                log.info("Groq API returned empty translation - correlationId: {}", correlationId);
                return TranslationResult.builder()
                        .originalText(englishText)
                        .translatedText(englishText)
                        .sourceLanguage("en")
                        .targetLanguage("he")
                        .confidence(0.0)
                        .success(false)
                        .errorMessage("Empty translation returned")
                        .build();
            }
            
            log.info("Translation completed via Groq API - correlationId: {}, original length: {}, translated length: {}, tokens: {}", 
                    correlationId, 
                    englishText.length(), 
                    translatedText.length(),
                    response.getUsage() != null ? response.getUsage().getTotalTokens() : "unknown");
            
            return TranslationResult.builder()
                    .originalText(englishText)
                    .translatedText(translatedText)
                    .sourceLanguage("en")
                    .targetLanguage("he")
                    .confidence(0.95) // High confidence for LLM translation
                    .success(true)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error translating with Groq API - correlationId: {}, error: {}", correlationId, e.getMessage(), e);
            
            // Fallback to stub translation on error
            return TranslationResult.builder()
                    .originalText(englishText)
                    .translatedText(createStubTranslationText(englishText))
                    .sourceLanguage("en")
                    .targetLanguage("he")
                    .confidence(0.0)
                    .success(false)
                    .errorMessage("Groq API error: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * Creates a stub translation for POC purposes or fallback.
     * 
     * @param englishText Original English text
     * @param correlationId Correlation ID for logging
     * @return TranslationResult with stub translation
     */
    private TranslationResult createStubTranslation(String englishText, String correlationId) {
        String stubTranslation = createStubTranslationText(englishText);
        
        log.info("Using stub translation - correlationId: {}, original length: {}, translated length: {}", 
                correlationId, englishText.length(), stubTranslation.length());
        
        return TranslationResult.builder()
                .originalText(englishText)
                .translatedText(stubTranslation)
                .sourceLanguage("en")
                .targetLanguage("he")
                .confidence(0.5) // Low confidence for stub
                .success(true)
                .build();
    }
    
    /**
     * Creates stub translation text.
     * 
     * @param englishText Original English text
     * @return Stub Hebrew translation text
     */
    private String createStubTranslationText(String englishText) {
        return "[TRANSLATED TO HEBREW] " + englishText + " [Translation service will be implemented]";
    }
}
