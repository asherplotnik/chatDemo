package com.demoBank.chatDemo.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Chat session context - maintains conversation state per customer.
 * 
 * Scoped to customerId, maintains context across multiple messages in a conversation.
 * TTL: 30 minutes idle time (as per instructions.txt section 8).
 * 
 * Stores derived parameters, not raw chat history.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSessionContext {
    
    /**
     * Unique session ID (UUID).
     */
    private String sessionId;
    
    /**
     * Customer ID this session belongs to.
     */
    private String customerId;
    
    /**
     * Detected language code: "he" for Hebrew, "en" for English.
     * Null if not yet detected.
     */
    private String languageCode;
    
    /**
     * Confidence score (0.0 to 1.0) for language detection.
     * Null if not yet detected.
     */
    private Double languageConfidence;
    
    /**
     * Customer timezone (e.g., "Asia/Jerusalem").
     * Null if not yet determined.
     */
    private String timezone;
    
    /**
     * Last resolved intent from previous messages.
     * Null if no intent resolved yet.
     */
    private String lastIntent;
    
    /**
     * Resolved time range from previous messages (absolute dates).
     * Null if not resolved yet.
     */
    private TimeRange resolvedTimeRange;
    
    /**
     * Scope from previous messages (products, currencies, posted/pending).
     * Null if not resolved yet.
     */
    private String scope;
    
    /**
     * Clarification state - if awaiting an answer to a clarifying question.
     * Null if not in clarification mode.
     */
    private ClarificationState clarificationState;
    
    /**
     * Timestamp when session was created.
     */
    private Instant createdAt;
    
    /**
     * Timestamp when session was last accessed (for TTL calculation).
     */
    private Instant lastAccessedAt;
    
    /**
     * Checks if language has been established for this session.
     * 
     * @return true if language is already detected and stored
     */
    public boolean isLanguageEstablished() {
        return languageCode != null && !languageCode.isBlank();
    }
    
    /**
     * Updates the last accessed timestamp to now.
     */
    public void touch() {
        this.lastAccessedAt = Instant.now();
    }
    
    /**
     * Time range model for resolved dates.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TimeRange {
        private String fromDate; // YYYY-MM-DD
        private String toDate;   // YYYY-MM-DD
    }
    
    /**
     * Clarification state model.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ClarificationState {
        private String question;
        private String expectedAnswerType;
        private Instant askedAt;
    }
}
