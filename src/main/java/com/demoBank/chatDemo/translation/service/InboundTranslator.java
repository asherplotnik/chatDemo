package com.demoBank.chatDemo.translation.service;

import com.demoBank.chatDemo.translation.dto.GroqApiResponse;
import com.demoBank.chatDemo.translation.model.TranslationResult;
import com.demoBank.chatDemo.translation.prompt.TranslationSystemPrompt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Inbound Translator service - translates Hebrew to English.
 * 
 * Responsibilities:
 * - Translate Hebrew messages to English for internal processing
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
public class InboundTranslator {
    
    private final GroqApiClient groqApiClient;
    
    @Value("${translation.use-groq-api:false}")
    private boolean useGroqApi;
    
    /**
     * Gets the system prompt for semantic translation.
     * This prompt will be used when integrating with an LLM.
     * 
     * @return System prompt for translation
     */
    public String getSystemPrompt() {
        return TranslationSystemPrompt.getSystemPromptToEnglish();
    }
    
    /**
     * Gets the condensed version of the system prompt (for API calls with token limits).
     * 
     * @return Condensed system prompt
     */
    public String getCondensedPrompt() {
        return TranslationSystemPrompt.getCondensedSystemPromptToEnglish();
    }
    
    /**
     * Translates Hebrew text to English.
     * 
     * @param hebrewText Original text in Hebrew
     * @param correlationId Correlation ID for logging and tracking
     * @return TranslationResult with translated English text
     */
    public TranslationResult translateToEnglish(String hebrewText, String correlationId) {
        if (hebrewText == null || hebrewText.isBlank()) {
            log.warn("Empty Hebrew text provided for translation - correlationId: {}", correlationId);
            return TranslationResult.builder()
                    .originalText(hebrewText)
                    .translatedText(hebrewText)
                    .sourceLanguage("he")
                    .targetLanguage("en")
                    .confidence(0.0)
                    .success(false)
                    .errorMessage("Empty text provided")
                    .build();
        }
        
        log.info("Translating Hebrew to English - correlationId: {}, text length: {}, useGroqApi: {}", 
                correlationId, hebrewText.length(), useGroqApi);
        
        if (useGroqApi) {
            return translateWithGroqApi(hebrewText, correlationId);
        } else {
            return createStubTranslation(hebrewText, correlationId);
        }
    }
    
    /**
     * Translates Hebrew text to English using Groq API.
     * 
     * @param hebrewText Original Hebrew text
     * @param correlationId Correlation ID for logging
     * @return TranslationResult with translated text
     */
    private TranslationResult translateWithGroqApi(String hebrewText, String correlationId) {
        try {
            String systemPrompt = getCondensedPrompt();
            
            GroqApiResponse response = groqApiClient.translate(systemPrompt, hebrewText);
            
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
                log.info("Groq API returned empty translation (text already in English) - correlationId: {}", correlationId);
                return TranslationResult.builder()
                        .originalText(hebrewText)
                        .translatedText(hebrewText)
                        .sourceLanguage("he")
                        .targetLanguage("en")
                        .confidence(0.95) // High confidence - LLM detected English
                        .success(true)
                        .build();
            }
            
            log.info("Translation completed via Groq API - correlationId: {}, original length: {}, translated length: {}, tokens: {}", 
                    correlationId, 
                    hebrewText.length(), 
                    translatedText.length(),
                    response.getUsage() != null ? response.getUsage().getTotalTokens() : "unknown");
            
            return TranslationResult.builder()
                    .originalText(hebrewText)
                    .translatedText(translatedText)
                    .sourceLanguage("he")
                    .targetLanguage("en")
                    .confidence(0.95) // High confidence for LLM translation
                    .success(true)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error translating with Groq API - correlationId: {}, error: {}", correlationId, e.getMessage(), e);
            
            // Fallback to stub translation on error
            return TranslationResult.builder()
                    .originalText(hebrewText)
                    .translatedText(createStubTranslationText(hebrewText))
                    .sourceLanguage("he")
                    .targetLanguage("en")
                    .confidence(0.0)
                    .success(false)
                    .errorMessage("Groq API error: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * Creates a stub translation for POC purposes or fallback.
     * 
     * @param hebrewText Original Hebrew text
     * @param correlationId Correlation ID for logging
     * @return TranslationResult with stub translation
     */
    private TranslationResult createStubTranslation(String hebrewText, String correlationId) {
        String stubTranslation = createStubTranslationText(hebrewText);
        
        log.info("Using stub translation - correlationId: {}, original length: {}, translated length: {}", 
                correlationId, hebrewText.length(), stubTranslation.length());
        
        return TranslationResult.builder()
                .originalText(hebrewText)
                .translatedText(stubTranslation)
                .sourceLanguage("he")
                .targetLanguage("en")
                .confidence(0.5) // Low confidence for stub
                .success(true)
                .build();
    }
    
    /**
     * Creates stub translation text.
     * 
     * @param hebrewText Original Hebrew text
     * @return Stub English translation text
     */
    private String createStubTranslationText(String hebrewText) {
        return "[TRANSLATED FROM HEBREW] " + hebrewText + " [Translation service will be implemented]";
    }
}
