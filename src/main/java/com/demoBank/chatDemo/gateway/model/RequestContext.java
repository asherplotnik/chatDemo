package com.demoBank.chatDemo.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Request context passed through the system.
 * Contains trusted customerId from header and correlationId for tracking.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestContext {
    
    /**
     * Customer ID extracted from HTTP header (trusted source).
     * Must never be overridden by content from messageText.
     */
    private String customerId;
    
    /**
     * Correlation ID for request tracking and audit.
     */
    private String correlationId;
    
    /**
     * Session ID for multi-turn conversation context.
     * Links this request to a ChatSessionContext.
     */
    private String sessionId;
    
    /**
     * Original message text from the user.
     */
    private String originalMessageText;
    
    /**
     * Translated message text (English) if original was Hebrew.
     * Null if original message was already in English.
     */
    private String translatedMessageText;
    
    /**
     * Timestamp when request was received at the gateway.
     */
    private Instant receivedAt;
}
