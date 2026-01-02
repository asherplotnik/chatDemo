package com.demoBank.chatDemo.gateway.service;

import com.demoBank.chatDemo.gateway.model.ChatSessionContext;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Session service - manages chat session context using Caffeine cache.
 * 
 * Responsibilities:
 * - Get or create session for a customer
 * - Update session data
 * - Handle session expiration (30 minutes idle TTL)
 * - Store conversation context (language, timezone, intent, etc.)
 * 
 * Future: Can also store API call results here for caching.
 */
@Slf4j
@Service
public class SessionService {
    
    /**
     * Session TTL: 30 minutes idle time (as per instructions.txt section 8).
     */
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);
    
    /**
     * Caffeine cache for storing sessions.
     * Key: customerId (String)
     * Value: ChatSessionContext
     * 
     * Expires entries after 30 minutes of inactivity.
     */
    private final Cache<String, ChatSessionContext> sessionCache = Caffeine.newBuilder()
            .expireAfterAccess(SESSION_TTL)
            .maximumSize(10_000) // Max 10k concurrent sessions
            .removalListener((key, value, cause) -> {
                if (value != null) {
                    ChatSessionContext session = (ChatSessionContext) value;
                    log.debug("Session expired - customerId: {}, sessionId: {}, cause: {}", 
                            maskCustomerId((String) key), session.getSessionId(), cause);
                }
            })
            .build();
    
    /**
     * Gets an existing session for a customer, or creates a new one if none exists.
     * 
     * @param customerId Customer ID
     * @return ChatSessionContext (existing or newly created)
     */
    public ChatSessionContext getOrCreateSession(String customerId) {
        ChatSessionContext session = sessionCache.getIfPresent(customerId);
        
        if (session == null) {
            // Create new session
            session = ChatSessionContext.builder()
                    .sessionId(UUID.randomUUID().toString())
                    .customerId(customerId)
                    .createdAt(Instant.now())
                    .lastAccessedAt(Instant.now())
                    .build();
            
            sessionCache.put(customerId, session);
            log.info("Created new session - customerId: {}, sessionId: {}", 
                    maskCustomerId(customerId), session.getSessionId());
        } else {
            // Update last accessed time
            session.touch();
            log.debug("Retrieved existing session - customerId: {}, sessionId: {}", 
                    maskCustomerId(customerId), session.getSessionId());
        }
        
        return session;
    }
    
    /**
     * Gets an existing session for a customer.
     * Returns null if session doesn't exist or has expired.
     * 
     * @param customerId Customer ID
     * @return ChatSessionContext or null if not found
     */
    public ChatSessionContext getSession(String customerId) {
        ChatSessionContext session = sessionCache.getIfPresent(customerId);
        if (session != null) {
            session.touch(); // Update last accessed time
        }
        return session;
    }
    
    /**
     * Updates a session in the cache.
     * 
     * @param session Session to update
     */
    public void updateSession(ChatSessionContext session) {
        if (session == null || session.getCustomerId() == null) {
            log.warn("Attempted to update null session or session without customerId");
            return;
        }
        
        session.touch(); // Update last accessed time
        sessionCache.put(session.getCustomerId(), session);
        log.debug("Updated session - customerId: {}, sessionId: {}", 
                maskCustomerId(session.getCustomerId()), session.getSessionId());
    }
    
    /**
     * Removes a session from the cache (explicit invalidation).
     * 
     * @param customerId Customer ID
     */
    public void invalidateSession(String customerId) {
        sessionCache.invalidate(customerId);
        log.info("Invalidated session - customerId: {}", maskCustomerId(customerId));
    }
    
    /**
     * Gets the current number of active sessions.
     * Useful for monitoring.
     * 
     * @return Number of active sessions
     */
    public long getActiveSessionCount() {
        return sessionCache.estimatedSize();
    }
    
    /**
     * Masks customer ID for logging (privacy compliance).
     */
    private String maskCustomerId(String customerId) {
        if (customerId == null || customerId.length() <= 4) {
            return "****";
        }
        return customerId.substring(0, 2) + "****" + customerId.substring(customerId.length() - 2);
    }
}
