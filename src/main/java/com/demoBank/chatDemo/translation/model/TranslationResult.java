package com.demoBank.chatDemo.translation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of translation operation.
 * Contains original text, translated text, and metadata.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TranslationResult {
    
    /**
     * Original text in source language (Hebrew).
     */
    private String originalText;
    
    /**
     * Translated text in target language (English).
     */
    private String translatedText;
    
    /**
     * Source language code (e.g., "he" for Hebrew).
     */
    private String sourceLanguage;
    
    /**
     * Target language code (e.g., "en" for English).
     */
    private String targetLanguage;
    
    /**
     * Confidence score (0.0 to 1.0) indicating translation quality.
     */
    private double confidence;
    
    /**
     * Whether translation was successful.
     */
    private boolean success;
    
    /**
     * Error message if translation failed.
     */
    private String errorMessage;
}
