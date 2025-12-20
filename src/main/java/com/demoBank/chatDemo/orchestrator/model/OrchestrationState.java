package com.demoBank.chatDemo.orchestrator.model;

import com.demoBank.chatDemo.gateway.dto.ChatResponse;
import com.demoBank.chatDemo.gateway.model.ChatSessionContext;
import com.demoBank.chatDemo.gateway.model.RequestContext;
import com.demoBank.chatDemo.orchestrator.dto.IntentExtractionResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Orchestration state - maintains state throughout the orchestration workflow.
 * 
 * Contains all intermediate data and results as the request flows through the pipeline.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrchestrationState {
    
    /**
     * Original request context.
     */
    private RequestContext requestContext;
    
    /**
     * Session context (loaded from session).
     */
    private ChatSessionContext sessionContext;
    
    /**
     * Extracted intents (list of domain + metric + parameters).
     * Can contain multiple intents for queries requiring multiple domains.
     */
    private List<IntentExtractionResponse.IntentData> extractedIntent;
    
    /**
     * Resolved time range (absolute dates).
     * TODO: Use ChatSessionContext.TimeRange or create specific model
     */
    private Object resolvedTimeRange;
    
    /**
     * Execution plan (which APIs to call).
     * TODO: Create Plan model
     */
    private Object executionPlan;
    
    /**
     * Raw API responses.
     * TODO: Create appropriate models for API responses
     */
    private Object fetchedData;
    
    /**
     * Normalized data (canonical internal models).
     * TODO: Create normalized data models
     */
    private Object normalizedData;
    
    /**
     * Computed results.
     * TODO: Create computed results model
     */
    private Object computedResults;
    
    /**
     * Drafted response text.
     */
    private String draftedAnswer;
    
    /**
     * Drafted explanation ("How I got this").
     */
    private String draftedExplanation;
    
    /**
     * Final response.
     */
    private ChatResponse response;
    
    /**
     * Whether orchestration should stop (e.g., blocked by guard, needs clarification).
     */
    @Builder.Default
    private boolean shouldStop = false;
    
    /**
     * Whether we're awaiting clarification from user.
     */
    @Builder.Default
    private boolean awaitingClarification = false;
    
    /**
     * Whether clarifier is needed (intent is ambiguous).
     */
    @Builder.Default
    private boolean needsClarifier = false;
    
    /**
     * What needs clarification (e.g., "time_range", "account_selection", "intent_domain", "metric").
     * Set by INTENT_EXTRACT, RESOLVE_TIME_RANGE, or PLAN when ambiguity is detected.
     * Used by ASK_CLARIFIER to generate the appropriate question.
     */
    private String clarificationNeeded;
    
    /**
     * Additional context about what needs clarification (optional).
     * Can contain details like possible options, detected ambiguity, etc.
     */
    private String clarificationContext;
    
    /**
     * User's answer to a clarifying question (set by APPLY_CLARIFICATION).
     * Used by INTENT_EXTRACT to understand the clarification context.
     */
    private String clarificationAnswer;
    
    /**
     * Expected answer type for clarification (e.g., "time_range", "account", "yes_no").
     * Set by APPLY_CLARIFICATION, used by INTENT_EXTRACT to apply defaults if answer is unsatisfactory.
     */
    private String expectedAnswerType;
    
    /**
     * Error message if orchestration failed.
     */
    private String errorMessage;
    
    /**
     * Checks if we're awaiting clarification.
     * 
     * @return true if clarification is pending
     */
    public boolean isAwaitingClarification() {
        return awaitingClarification || 
               (sessionContext != null && sessionContext.getClarificationState() != null);
    }
    
    /**
     * Checks if clarifier is needed.
     * 
     * @return true if clarifier should be invoked
     */
    public boolean needsClarifier() {
        return needsClarifier;
    }
    
    /**
     * Checks if orchestration should stop.
     * 
     * @return true if should stop
     */
    public boolean shouldStop() {
        return shouldStop;
    }
    
    /**
     * Gets the customer ID from request context (trusted source from HTTP header).
     * 
     * @return Customer ID, never null (validated in GatewayService)
     */
    public String getCustomerId() {
        if (requestContext == null) {
            throw new IllegalStateException("RequestContext is null - customerId not available");
        }
        String customerId = requestContext.getCustomerId();
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalStateException("CustomerId is missing from RequestContext - this should never happen");
        }
        return customerId;
    }
    
    /**
     * Gets the correlation ID from request context.
     * 
     * @return Correlation ID
     */
    public String getCorrelationId() {
        return requestContext != null ? requestContext.getCorrelationId() : null;
    }
    
    /**
     * Gets the session ID from request context.
     * 
     * @return Session ID
     */
    public String getSessionId() {
        return requestContext != null ? requestContext.getSessionId() : null;
    }
    
    /**
     * Gets the message text for processing (translated to English if needed).
     * 
     * @return Message text for processing
     */
    public String getMessageTextForProcessing() {
        return requestContext != null ? requestContext.getTranslatedMessageText() : null;
    }
}
