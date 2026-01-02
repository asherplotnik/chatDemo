package com.demoBank.chatDemo.language.service;

import com.demoBank.chatDemo.language.model.LanguageDetectionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Language Detector service.
 * 
 * Responsibilities:
 * - Detect if incoming message is Hebrew or English
 * - Primary customer language: Hebrew
 * - Internal processing language: English
 * 
 * Detection strategy:
 * - Uses Unicode range detection for Hebrew characters (U+0590 to U+05FF)
 * - If message contains Hebrew characters, classify as Hebrew
 * - Otherwise, default to English
 */
@Slf4j
@Service
public class LanguageDetector {
    
    /**
     * Unicode range for Hebrew characters: U+0590 to U+05FF
     * Includes Hebrew letters, vowels, and punctuation
     */
    private static final Pattern HEBREW_PATTERN = Pattern.compile(
        "[\\u0590-\\u05FF]+"
    );
    
    /**
     * Detects the language of the given message text.
     * 
     * @param messageText The message text to analyze
     * @return LanguageDetectionResult indicating Hebrew or English
     */
    public LanguageDetectionResult detectLanguage(String messageText) {
        if (messageText == null || messageText.isBlank()) {
            log.warn("Empty message text provided for language detection");
            return LanguageDetectionResult.builder()
                    .languageCode("en")
                    .confidence(0.0)
                    .build();
        }
        
        // Check if message contains Hebrew characters
        boolean containsHebrew = HEBREW_PATTERN.matcher(messageText).find();
        
        // Calculate confidence based on ratio of Hebrew characters
        double confidence = calculateConfidence(messageText, containsHebrew);
        
        String detectedLanguage = containsHebrew ? "he" : "en";
        
        log.debug("Language detection - detected: {}, confidence: {:.2f}", 
                detectedLanguage, confidence);
        
        return LanguageDetectionResult.builder()
                .languageCode(detectedLanguage)
                .confidence(confidence)
                .build();
    }
    
    /**
     * Calculates confidence score based on the ratio of Hebrew characters.
     * 
     * @param messageText The message text
     * @param containsHebrew Whether Hebrew characters were found
     * @return Confidence score between 0.0 and 1.0
     */
    private double calculateConfidence(String messageText, boolean containsHebrew) {
        if (!containsHebrew) {
            // For English, check if it contains mostly ASCII/Latin characters
            long totalChars = messageText.chars().filter(Character::isLetter).count();
            if (totalChars == 0) {
                return 0.5; // Neutral confidence for non-text content
            }
            long latinChars = messageText.chars()
                    .filter(c -> Character.isLetter(c) && c < 128)
                    .count();
            return Math.min(1.0, (double) latinChars / totalChars);
        }
        
        // For Hebrew, calculate ratio of Hebrew characters
        long totalChars = messageText.chars().filter(Character::isLetter).count();
        if (totalChars == 0) {
            return 0.5;
        }
        
        long hebrewChars = messageText.chars()
                .filter(c -> c >= 0x0590 && c <= 0x05FF)
                .count();
        
        return Math.min(1.0, (double) hebrewChars / totalChars);
    }
}
