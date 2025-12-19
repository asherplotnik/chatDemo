package com.demoBank.chatDemo.language.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of language detection.
 * Indicates whether the message is Hebrew or English.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LanguageDetectionResult {
    
    /**
     * Detected language code: "he" for Hebrew, "en" for English.
     */
    private String languageCode;
    
    /**
     * Confidence score (0.0 to 1.0) indicating detection confidence.
     */
    private double confidence;
    
    /**
     * Whether the message requires translation.
     * Hebrew messages need translation to English for internal processing.
     */
    public boolean requiresTranslation() {
        return "he".equals(languageCode);
    }
    
    /**
     * Convenience method to check if language is Hebrew.
     */
    public boolean isHebrew() {
        return "he".equals(languageCode);
    }
    
    /**
     * Convenience method to check if language is English.
     */
    public boolean isEnglish() {
        return "en".equals(languageCode);
    }
}
