package com.demoBank.chatDemo.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

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
     * Last resolved intent from previous messages (domain + metric).
     * Null if no intent resolved yet.
     * Will be filled by the Orchestrator.
     */
    private ResolvedIntent lastResolvedIntent;
    
    /**
     * Resolved time range from previous messages (absolute dates).
     * Null if not resolved yet.
     * Will be filled by the Orchestrator.
     */
    private TimeRange lastResolvedTimeRange;
    
    /**
     * Last selected entities (accountId masked refs, cardId, etc.).
     * Null if not selected yet.
     * Will be filled by the Orchestrator.
     */
    private SelectedEntities lastSelectedEntities;
    
    /**
     * Clarification state - if awaiting an answer to a clarifying question.
     * Null if not in clarification mode.
     * Will be filled by the Orchestrator/Clarifier.
     */
    private ClarificationState clarificationState;
    
    /**
     * Default preferences for the session.
     * Will be filled by the Orchestrator.
     */
    private SessionDefaults defaults;
    
    /**
     * Conversation history summaries - stores summaries of previous Q&A pairs.
     * Limited to last N messages to control prompt size.
     * Will be filled by the Orchestrator after responses.
     */
    private List<ConversationSummary> conversationSummaries;
    
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
     * Resolved intent model (domain + metric).
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResolvedIntent {
        /**
         * Domain: current-accounts, foreign-current-accounts, credit-cards, loans, mortgages, deposits, securities
         */
        private String domain;
        
        /**
         * Metric: balance, count, sum, max, min, average, list, etc.
         */
        private String metric;
        
        /**
         * Additional intent parameters as JSON string or Map
         */
        private String parameters;
    }
    
    /**
     * Selected entities model (accountId masked refs, cardId, etc.).
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SelectedEntities {
        /**
         * List of masked account references (e.g., "****1234")
         */
        private java.util.List<String> accountIds;
        
        /**
         * List of masked card IDs (e.g., "****5678")
         */
        private java.util.List<String> cardIds;
        
        /**
         * Other selected entity IDs (loan IDs, deposit IDs, etc.)
         */
        private java.util.Map<String, java.util.List<String>> otherEntities;
    }
    
    /**
     * Session defaults model.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SessionDefaults {
        /**
         * Transaction status preference: "posted" or "pending"
         * Default: "posted"
         */
        private String transactionStatus; // "posted" or "pending"
        
        /**
         * Currency preference (e.g., "ILS", "USD", "EUR")
         * Null means no preference (show all currencies)
         */
        private String currencyPreference;
        
        /**
         * Paging cursor policy: "auto", "manual", "none"
         * Default: "auto"
         */
        private String pagingCursorPolicy; // "auto", "manual", "none"
        
        /**
         * Page size for paginated results
         * Default: 10
         */
        private Integer pageSize;
    }
    
    /**
     * Clarification state model.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ClarificationState {
        /**
         * The clarifying question asked to the user
         */
        private String question;
        
        /**
         * Expected answer type (e.g., "date", "account", "amount", "yes_no")
         */
        private String expectedAnswerType;
        
        /**
         * When the question was asked
         */
        private Instant askedAt;
        
        /**
         * Context about what we're clarifying (e.g., "time_range", "account_selection")
         */
        private String clarificationContext;
    }
    
    /**
     * Conversation summary model - stores summary of a Q&A pair.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConversationSummary {
        /**
         * User's question/message
         */
        private String userMessage;
        
        /**
         * Summary of the response provided.
         * Format: "domain=<domain>, timeRange=<fromDate> to <toDate>, entities=[<nicknames>], transactions=<included|excluded>"
         */
        private String responseSummary;
        
        /**
         * When this summary was created
         */
        private Instant createdAt;
    }
}
