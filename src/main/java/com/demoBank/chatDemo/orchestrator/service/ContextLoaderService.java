package com.demoBank.chatDemo.orchestrator.service;

import com.demoBank.chatDemo.gateway.model.ChatSessionContext;
import com.demoBank.chatDemo.gateway.model.RequestContext;
import com.demoBank.chatDemo.orchestrator.dto.IntentExtractionResponse;
import com.demoBank.chatDemo.orchestrator.model.OrchestrationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Service for loading conversation context from session.
 * 
 * Handles:
 * - Loading session context into orchestration state
 * - Checking for context expiration (30 minutes TTL)
 * - Loading previous intent, time range, and selected entities
 * - Initializing session defaults
 */
@Slf4j
@Service
public class ContextLoaderService {
    
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);
    
    /**
     * Loads conversation context from session into orchestration state.
     * 
     * @param state Current orchestration state
     * @param requestContext Request context containing session context
     * @return Updated orchestration state with loaded context
     */
    public OrchestrationState loadContext(OrchestrationState state, RequestContext requestContext) {
        String correlationId = requestContext.getCorrelationId();
        log.debug("Step LOAD_CONTEXT - correlationId: {}", correlationId);
        
        ChatSessionContext sessionContext = requestContext.getSessionContext();
        if (sessionContext == null) {
            log.warn("No session context found - correlationId: {}", correlationId);
            return state;
        }
        
        // Set session context in state
        state.setSessionContext(sessionContext);
        
        // Check for context expiration (30 minutes idle TTL)
        checkAndHandleExpiration(sessionContext, correlationId);
        
        // Check if awaiting clarification
        checkAwaitingClarification(state, sessionContext, correlationId);
        
        // Load previous intent for follow-up questions
        loadPreviousIntent(state, sessionContext, correlationId);
        
        // Load previous time range for follow-up questions
        loadPreviousTimeRange(state, sessionContext, correlationId);
        
        // Load previous selected entities for follow-up questions
        logSelectedEntities(sessionContext, correlationId);
        
        // Initialize defaults if not present
        initializeDefaults(sessionContext, correlationId);
        
        // Log loaded context summary
        logContextSummary(sessionContext, correlationId);
        
        return state;
    }
    
    /**
     * Checks for context expiration and clears expired context.
     */
    private void checkAndHandleExpiration(ChatSessionContext sessionContext, String correlationId) {
        if (sessionContext.getLastAccessedAt() != null) {
            Duration timeSinceLastAccess = Duration.between(sessionContext.getLastAccessedAt(), Instant.now());
            
            if (timeSinceLastAccess.compareTo(SESSION_TTL) > 0) {
                log.info("Session context expired - correlationId: {}, sessionId: {}, timeSinceLastAccess: {} minutes",
                        correlationId, sessionContext.getSessionId(), timeSinceLastAccess.toMinutes());
                // Context expired - clear it but keep session for new conversation
                sessionContext.setLastResolvedIntent(null);
                sessionContext.setLastResolvedTimeRange(null);
                sessionContext.setLastSelectedEntities(null);
                sessionContext.setClarificationState(null);
                // Keep language and timezone as they're still valid
            }
        }
    }
    
    /**
     * Checks if awaiting clarification and sets flag in state.
     */
    private void checkAwaitingClarification(OrchestrationState state, ChatSessionContext sessionContext, String correlationId) {
        if (sessionContext.getClarificationState() != null) {
            state.setAwaitingClarification(true);
            log.debug("Awaiting clarification - correlationId: {}, question: {}, expectedType: {}",
                    correlationId,
                    sessionContext.getClarificationState().getQuestion(),
                    sessionContext.getClarificationState().getExpectedAnswerType());
        }
    }
    
    /**
     * Loads previous intent from session into state.
     */
    private void loadPreviousIntent(OrchestrationState state, ChatSessionContext sessionContext, String correlationId) {
        if (sessionContext.getLastResolvedIntent() != null) {
            ChatSessionContext.ResolvedIntent resolvedIntent = sessionContext.getLastResolvedIntent();
            log.debug("Previous intent found in session - correlationId: {}, domain: {}, metric: {}",
                    correlationId,
                    resolvedIntent.getDomain(),
                    resolvedIntent.getMetric());
            
            // Convert ResolvedIntent to IntentExtractionResponse.IntentData
            IntentExtractionResponse.IntentData intentData = IntentExtractionResponse.IntentData.builder()
                    .domain(resolvedIntent.getDomain())
                    .metric(resolvedIntent.getMetric())
                    .timeRangeHint(null) // Not stored in ResolvedIntent
                    .entityHints(null) // Not stored in ResolvedIntent
                    .parameters(null) // Parameters stored as string, would need parsing
                    .build();
            
            state.setExtractedIntent(List.of(intentData));
        }
    }
    
    /**
     * Loads previous time range from session into state.
     */
    private void loadPreviousTimeRange(OrchestrationState state, ChatSessionContext sessionContext, String correlationId) {
        if (sessionContext.getLastResolvedTimeRange() != null) {
            state.setResolvedTimeRange(sessionContext.getLastResolvedTimeRange());
            log.debug("Loaded previous time range - correlationId: {}, fromDate: {}, toDate: {}",
                    correlationId,
                    sessionContext.getLastResolvedTimeRange().getFromDate(),
                    sessionContext.getLastResolvedTimeRange().getToDate());
        }
    }
    
    /**
     * Logs selected entities (for debugging).
     */
    private void logSelectedEntities(ChatSessionContext sessionContext, String correlationId) {
        if (sessionContext.getLastSelectedEntities() != null) {
            log.debug("Loaded previous selected entities - correlationId: {}, accountIds: {}, cardIds: {}",
                    correlationId,
                    sessionContext.getLastSelectedEntities().getAccountIds() != null 
                            ? sessionContext.getLastSelectedEntities().getAccountIds().size() : 0,
                    sessionContext.getLastSelectedEntities().getCardIds() != null 
                            ? sessionContext.getLastSelectedEntities().getCardIds().size() : 0);
        }
    }
    
    /**
     * Initializes session defaults if not present.
     */
    private void initializeDefaults(ChatSessionContext sessionContext, String correlationId) {
        if (sessionContext.getDefaults() == null) {
            ChatSessionContext.SessionDefaults defaults = ChatSessionContext.SessionDefaults.builder()
                    .transactionStatus("posted") // Default to posted transactions
                    .pagingCursorPolicy("auto") // Default to auto paging
                    .pageSize(10) // Default page size
                    .build();
            sessionContext.setDefaults(defaults);
            log.debug("Initialized default preferences - correlationId: {}", correlationId);
        }
    }
    
    /**
     * Logs context summary (for debugging).
     */
    private void logContextSummary(ChatSessionContext sessionContext, String correlationId) {
        // Apply context defaults to state
        ChatSessionContext.SessionDefaults defaults = sessionContext.getDefaults();
        if (defaults != null) {
            log.debug("Applied context defaults - correlationId: {}, transactionStatus: {}, currency: {}, pagingPolicy: {}, pageSize: {}",
                    correlationId,
                    defaults.getTransactionStatus(),
                    defaults.getCurrencyPreference(),
                    defaults.getPagingCursorPolicy(),
                    defaults.getPageSize());
        }
        
        // Log loaded context summary
        log.info("Loaded context - correlationId: {}, sessionId: {}, language: {}, timezone: {}, " +
                "hasLastIntent: {}, hasTimeRange: {}, hasSelectedEntities: {}, hasClarification: {}, hasDefaults: {}",
                correlationId,
                sessionContext.getSessionId(),
                sessionContext.getLanguageCode(),
                sessionContext.getTimezone(),
                sessionContext.getLastResolvedIntent() != null,
                sessionContext.getLastResolvedTimeRange() != null,
                sessionContext.getLastSelectedEntities() != null,
                sessionContext.getClarificationState() != null,
                sessionContext.getDefaults() != null);
    }
}
