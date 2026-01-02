package com.demoBank.chatDemo.orchestrator.service;

import com.demoBank.chatDemo.gateway.dto.ChatResponse;
import com.demoBank.chatDemo.gateway.model.ChatSessionContext;
import com.demoBank.chatDemo.gateway.model.RequestContext;
import com.demoBank.chatDemo.orchestrator.dto.DraftResponseDTO;
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
 * - Creating summaries from drafted responses (what was actually shown to the user)
 * - Storing summaries in session context
 * - Limiting summary count to control prompt size
 */
@Slf4j
@Service
public class ConversationSummaryService {
    
    private static final int MAX_SUMMARIES = 10;
    
    /**
     * Creates a conversation summary from the drafted response and stores it in session context.
     * Summary format: "introduction=<intro>, tableType=<type>, dataSource=<source>"
     * 
     * @param state Current orchestration state with drafted response
     * @param requestContext Request context
     */
    public void createConversationSummary(OrchestrationState state, RequestContext requestContext) {
        String correlationId = requestContext.getCorrelationId();
        log.debug("Creating conversation summary from drafted response - correlationId: {}", correlationId);
        
        ChatSessionContext sessionContext = state.getSessionContext();
        if (sessionContext == null) {
            log.debug("No session context available for summary - correlationId: {}", correlationId);
            return;
        }
        
        ChatResponse response = state.getResponse();
        if (response == null) {
            log.debug("No drafted response available for summary - correlationId: {}", correlationId);
            return;
        }
        
        // Get user message
        String userMessage = requestContext.getTranslatedMessageText();
        if (userMessage == null || userMessage.isBlank()) {
            userMessage = requestContext.getOriginalMessageText();
        }
        
        // Generate summary from drafted response
        String summary = generateResponseSummary(response, correlationId);
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
            
            log.info("Created conversation summary from drafted response - correlationId: {}, summary: {}", 
                    correlationId, summary);
        }
    }
    
    /**
     * Generates a response summary string from the drafted ChatResponse.
     * Format: "introduction=<intro>, tableType=<type>, hasTable=<true|false>, dataSource=<source>"
     * 
     * @param response Drafted ChatResponse
     * @param correlationId Correlation ID for logging
     * @return Summary string or null if cannot generate
     */
    private String generateResponseSummary(ChatResponse response, String correlationId) {
        
        try {
            StringBuilder summary = new StringBuilder();
            
            // Add introduction (truncated if too long)
            String introduction = response.getAnswer();
            if (introduction != null && !introduction.isBlank()) {
                // Truncate to first 100 chars if longer
                String introSummary = introduction.length() > 100 
                    ? introduction.substring(0, 100) + "..." 
                    : introduction;
                summary.append("introduction=").append(introSummary.replace(",", ";"));
            } else {
                summary.append("introduction=none");
            }
            
            // Add tables information
            if (response.getTables() != null && !response.getTables().isEmpty()) {
                summary.append(", tableCount=").append(response.getTables().size());
                summary.append(", hasTables=true");
                // Add account names
                List<String> accountNames = new ArrayList<>();
                int totalRows = 0;
                for (DraftResponseDTO.TableData table : response.getTables()) {
                    if (table.getAccountName() != null) {
                        accountNames.add(table.getAccountName());
                    }
                    if (table.getRows() != null) {
                        totalRows += table.getRows().size();
                    }
                }
                if (!accountNames.isEmpty()) {
                    summary.append(", accounts=[").append(String.join(",", accountNames)).append("]");
                }
                summary.append(", totalRows=").append(totalRows);
            } else {
                summary.append(", hasTables=false");
            }
            
            // Add data source
            String dataSource = response.getExplanation();
            if (dataSource != null && !dataSource.isBlank()) {
                // Truncate if too long
                String sourceSummary = dataSource.length() > 80 
                    ? dataSource.substring(0, 80) + "..." 
                    : dataSource;
                summary.append(", dataSource=").append(sourceSummary.replace(",", ";"));
            } else {
                summary.append(", dataSource=unknown");
            }
            
            return summary.toString();
            
        } catch (Exception e) {
            log.error("Error generating response summary from drafted response - correlationId: {}", 
                    correlationId, e);
            return null;
        }
    }
}
