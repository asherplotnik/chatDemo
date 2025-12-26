package com.demoBank.chatDemo.orchestrator.service;

import com.demoBank.chatDemo.gateway.model.ChatSessionContext;
import com.demoBank.chatDemo.gateway.model.RequestContext;
import com.demoBank.chatDemo.orchestrator.model.NormalizedData;
import com.demoBank.chatDemo.orchestrator.model.NormalizedEntity;
import com.demoBank.chatDemo.orchestrator.model.OrchestrationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for creating and managing conversation summaries.
 * 
 * Handles:
 * - Creating summaries from normalized data
 * - Storing summaries in session context
 * - Limiting summary count to control prompt size
 */
@Slf4j
@Service
public class ConversationSummaryService {
    
    private static final int MAX_SUMMARIES = 10;
    
    /**
     * Creates a conversation summary from normalized data and stores it in session context.
     * Summary format: "domain=<domain>, timeRange=<fromDate> to <toDate>, entities=[<nicknames>], transactions=<included|excluded>"
     * 
     * @param state Current orchestration state with normalized data
     * @param requestContext Request context
     */
    public void createConversationSummary(OrchestrationState state, RequestContext requestContext) {
        String correlationId = requestContext.getCorrelationId();
        log.debug("Creating conversation summary - correlationId: {}", correlationId);
        
        ChatSessionContext sessionContext = state.getSessionContext();
        if (sessionContext == null) {
            log.debug("No session context available for summary - correlationId: {}", correlationId);
            return;
        }
        
        List<NormalizedData> normalizedData = state.getNormalizedData();
        if (normalizedData == null || normalizedData.isEmpty()) {
            log.debug("No normalized data to summarize - correlationId: {}", correlationId);
            return;
        }
        
        // Get user message
        String userMessage = requestContext.getTranslatedMessageText();
        if (userMessage == null || userMessage.isBlank()) {
            userMessage = requestContext.getOriginalMessageText();
        }
        
        // Generate summary for each domain in normalized data
        for (NormalizedData data : normalizedData) {
            String summary = generateResponseSummary(data, state, correlationId);
            if (summary != null) {
                // Initialize conversation summaries list if needed
                if (sessionContext.getConversationSummaries() == null) {
                    sessionContext.setConversationSummaries(new ArrayList<>());
                }
                
                // Create summary entry
                ChatSessionContext.ConversationSummary conversationSummary = 
                    ChatSessionContext.ConversationSummary.builder()
                        .userMessage(userMessage)
                        .responseSummary(summary)
                        .createdAt(Instant.now())
                        .build();
                
                sessionContext.getConversationSummaries().add(conversationSummary);
                
                // Limit to last N summaries to control prompt size
                if (sessionContext.getConversationSummaries().size() > MAX_SUMMARIES) {
                    sessionContext.getConversationSummaries().remove(0);
                }
                
                log.info("Created conversation summary - correlationId: {}, summary: {}", correlationId, summary);
            }
        }
    }
    
    /**
     * Generates a response summary string from normalized data.
     * Format: "domain=<domain>, timeRange=<fromDate> to <toDate>, entities=[<nicknames>], transactions=<included|excluded>"
     * 
     * @param normalizedData Normalized data for one domain
     * @param state Orchestration state (for time range)
     * @param correlationId Correlation ID for logging
     * @return Summary string or null if cannot generate
     */
    private String generateResponseSummary(
            NormalizedData normalizedData,
            OrchestrationState state,
            String correlationId) {
        
        try {
            String domain = normalizedData.getDomain();
            if (domain == null) {
                return null;
            }
            
            // Get time range
            String timeRangeStr = "unknown";
            if (state.getResolvedTimeRange() instanceof ChatSessionContext.TimeRange) {
                ChatSessionContext.TimeRange timeRange = (ChatSessionContext.TimeRange) state.getResolvedTimeRange();
                if (timeRange.getFromDate() != null && timeRange.getToDate() != null) {
                    timeRangeStr = timeRange.getFromDate() + " to " + timeRange.getToDate();
                }
            }
            
            // Extract entity nicknames/IDs
            List<String> entityIdentifiers = new ArrayList<>();
            if (normalizedData.getEntities() != null) {
                for (NormalizedEntity entity : normalizedData.getEntities()) {
                    // Prefer nickname, fallback to entityId
                    String identifier = entity.getNickname();
                    if (identifier == null || identifier.isBlank()) {
                        identifier = entity.getEntityId();
                    }
                    if (identifier != null && !identifier.isBlank()) {
                        entityIdentifiers.add(identifier);
                    }
                }
            }
            
            // Determine if transactions were included
            boolean transactionsIncluded = false;
            if (normalizedData.getEntities() != null) {
                for (NormalizedEntity entity : normalizedData.getEntities()) {
                    if (entity.getTransactions() != null && !entity.getTransactions().isEmpty()) {
                        transactionsIncluded = true;
                        break;
                    }
                }
            }
            
            // Build summary string
            StringBuilder summary = new StringBuilder();
            summary.append("domain=").append(domain);
            summary.append(", timeRange=").append(timeRangeStr);
            
            if (!entityIdentifiers.isEmpty()) {
                summary.append(", entities=[").append(String.join(",", entityIdentifiers)).append("]");
            } else {
                summary.append(", entities=[]");
            }
            
            summary.append(", transactions=").append(transactionsIncluded ? "included" : "excluded");
            
            return summary.toString();
            
        } catch (Exception e) {
            log.error("Error generating response summary - correlationId: {}", correlationId, e);
            return null;
        }
    }
}
