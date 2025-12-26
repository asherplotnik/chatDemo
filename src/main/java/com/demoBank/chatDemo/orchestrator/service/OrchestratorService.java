package com.demoBank.chatDemo.orchestrator.service;

import com.demoBank.chatDemo.gateway.dto.ChatResponse;
import com.demoBank.chatDemo.gateway.model.ChatSessionContext;
import com.demoBank.chatDemo.gateway.model.RequestContext;
import com.demoBank.chatDemo.orchestrator.dto.IntentExtractionResponse;
import com.demoBank.chatDemo.orchestrator.model.OrchestrationState;
import com.demoBank.chatDemo.translation.dto.GroqApiRequest;
import com.demoBank.chatDemo.translation.dto.GroqApiResponse;
import com.demoBank.chatDemo.translation.service.GroqApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * Orchestrator service - workflow owner and coordinator.
 * 
 * Responsibilities:
 * - Manage the complete workflow from request to response
 * - Coordinate between different agents/services
 * - Handle state transitions and error recovery
 * - Maintain conversation context
 * 
 * Workflow steps:
 * LOAD_CONTEXT -> (IF AWAITING_CLARIFICATION ? APPLY_CLARIFICATION : INTENT_EXTRACT)
 * -> RESOLVE_TIME_RANGE -> PLAN -> (IF NEEDS_CLARIFIER -> ASK_CLARIFIER -> SAVE_CONTEXT -> END)
 * -> FETCH -> NORMALIZE -> COMPUTE -> DRAFT -> SAVE_CONTEXT -> RESPOND
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorService {
    
    private final GroqApiClient groqApiClient;
    private final ObjectMapper objectMapper;
    private final ContextLoaderService contextLoaderService;
    private final ClarificationService clarificationService;
    private final IntentService intentService;
    private final TimeRangeResolutionService timeRangeResolutionService;
    private final FetchService fetchService;
    private final NormalizationService normalizationService;
    private final ConversationSummaryService conversationSummaryService;
    
    /**
     * Main orchestration method - processes a chat request through the complete workflow.
     * 
     * @param requestContext Request context with customerId, correlationId, session, and message
     * @return ChatResponse with answer and explanation
     */
    public ChatResponse orchestrate(RequestContext requestContext) {
        String correlationId = requestContext.getCorrelationId();
        log.info("Starting orchestration - correlationId: {}", correlationId);
        
        try {
            // Initialize state
            OrchestrationState state = OrchestrationState.builder()
                    .requestContext(requestContext)
                    .build();
            
            // Step 1: LOAD_CONTEXT - Load conversation context first to understand the request
            state = contextLoaderService.loadContext(state, requestContext);
            
            // Step 2: IF AWAITING_CLARIFICATION ? APPLY_CLARIFICATION then INTENT_EXTRACT : INTENT_EXTRACT
            if (state.isAwaitingClarification()) {
                state = clarificationService.applyClarification(state, requestContext);
                // After applying clarification, still need to extract intent with clarification context
            }
            // Always extract intent (with clarification context if clarification was applied)
            state = intentService.extractIntent(state, requestContext);
            
            // Check if all intents are UNKNOWN (conversational, non-banking messages)
            if (isAllIntentsUnknown(state)) {
                log.info("All intents are UNKNOWN - taking conversational response path - correlationId: {}", correlationId);
                // Handle UNKNOWN intent with conversational response (skip data-fetching steps)
                state = intentService.handleUnknownIntent(state, requestContext);
                // Ensure response is created before returning
                if (state.getResponse() == null) {
                    log.warn("handleUnknownIntent did not create response - correlationId: {}, creating fallback response", correlationId);
                    state.setResponse(intentService.createUnknownIntentFallbackResponse(requestContext.getCorrelationId()));
                }
                return state.getResponse();
            }
            
            // Check if clarification is needed (before fetching data to avoid unnecessary API calls)
            if (state.needsClarifier()) {
                log.info("Clarification needed - asking clarifier before fetching data - correlationId: {}", correlationId);
                state = clarificationService.askClarifier(state, requestContext);
                // Ensure response is created before returning
                if (state.getResponse() == null) {
                    log.warn("askClarifier did not create response - correlationId: {}, creating fallback response", correlationId);
                    state.setResponse(clarificationService.createClarificationFallbackResponse(requestContext.getCorrelationId(), state.getClarificationNeeded()));
                }
                return state.getResponse();
            }
            
            // Step 3: RESOLVE_TIME_RANGE
            state = timeRangeResolutionService.resolveTimeRange(state, requestContext);
            
            // Step 4: FETCH
            state = fetchService.fetchData(state, requestContext);
            
            
            // Step 5: NORMALIZE
            state = normalizationService.normalize(state, requestContext);
            
            // Step 5.5: CREATE_CONVERSATION_SUMMARY - Create summary of this Q&A for future context
            conversationSummaryService.createConversationSummary(state, requestContext);
            
            // Step 6: COMPUTE
            state = compute(state, requestContext);
            
            // Step 7: DRAFT
            state = draft(state, requestContext);
            
            // Step 8: SAVE_CONTEXT
            saveContext(state, requestContext);
            
            // Step 9: RESPOND
            return respond(state, requestContext);
            
        } catch (Exception e) {
            log.error("Error in orchestration - correlationId: {}", correlationId, e);
            return createErrorResponse(correlationId, e);
        }
    }
    
    
    
    
    /**
     * Step 6: COMPUTE - Perform deterministic calculations.
     * 
     * @param state Current orchestration state
     * @param requestContext Request context
     * @return Updated orchestration state with computed results
     */
    private OrchestrationState compute(OrchestrationState state, RequestContext requestContext) {
        log.debug("Step COMPUTE - correlationId: {}", requestContext.getCorrelationId());
        // TODO: Implement computation logic
        // - Perform calculations (sum, average, max, min, count, etc.)
        // - Use deterministic code only (no LLM math)
        // - Store computed results in state
        return state;
    }
    
    /**
     * Step 7: DRAFT - Generate answer text and explanation.
     * 
     * @param state Current orchestration state
     * @param requestContext Request context
     * @return Updated orchestration state with drafted response
     */
    private OrchestrationState draft(OrchestrationState state, RequestContext requestContext) {
        log.debug("Step DRAFT - correlationId: {}", requestContext.getCorrelationId());
        // TODO: Implement drafting logic
        // - Generate concise answer (2-4 lines)
        // - Include "How I got this" explanation
        // - Format amounts, dates, descriptions
        // - Store drafted response in state
        return state;
    }

    /**
     * Step 8: SAVE_CONTEXT - Save conversation context to session.
     *
     * @param state Current orchestration state
     * @param requestContext Request context
     */
    private void saveContext(OrchestrationState state, RequestContext requestContext) {
        log.debug("Step SAVE_CONTEXT - correlationId: {}", requestContext.getCorrelationId());
        // TODO: Implement context saving logic
        // - Update session with resolved intent
        // - Update session with resolved time range
        // - Update session with selected entities
        // - Update session defaults if changed
        // - Clear clarification state if resolved
    }
    
    /**
     * Step 9: RESPOND - Return final response to user.
     * 
     * @param state Current orchestration state
     * @param requestContext Request context
     * @return Final ChatResponse
     */
    private ChatResponse respond(OrchestrationState state, RequestContext requestContext) {
        log.debug("Step RESPOND - correlationId: {}", requestContext.getCorrelationId());
        
        // If response already exists in state, return it
        if (state.getResponse() != null) {
            return state.getResponse();
        }
        
        // Temporary: Return normalized data as JSON until drafting is implemented
        String answer;
        String explanation;
        
        List<com.demoBank.chatDemo.orchestrator.model.NormalizedData> normalizedData = state.getNormalizedData();
        if (normalizedData != null && !normalizedData.isEmpty()) {
            try {
                // Convert normalized data to JSON
                String jsonData = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(normalizedData);
                answer = jsonData;
                explanation = "Normalized data returned as JSON (temporary - will be refined later)";
                log.info("Returning normalized data as JSON - correlationId: {}, dataCount: {}", 
                        requestContext.getCorrelationId(), normalizedData.size());
            } catch (Exception e) {
                log.error("Error serializing normalized data to JSON - correlationId: {}", 
                        requestContext.getCorrelationId(), e);
                answer = "Error serializing normalized data: " + e.getMessage();
                explanation = "Failed to convert normalized data to JSON";
            }
        } else {
            answer = "No normalized data available";
            explanation = "Normalization step did not produce any data";
            log.warn("No normalized data found in state - correlationId: {}", requestContext.getCorrelationId());
        }
        
        ChatResponse response = ChatResponse.builder()
                .answer(answer)
                .correlationId(requestContext.getCorrelationId())
                .explanation(explanation)
                .build();
        
        state.setResponse(response);
        return response;
    }
    
    /**
     * Checks if all extracted intents have domain "UNKNOWN".
     * 
     * @param state Orchestration state
     * @return true if all intents are UNKNOWN, false otherwise
     */
    private boolean isAllIntentsUnknown(OrchestrationState state) {
        List<IntentExtractionResponse.IntentData> intents = state.getExtractedIntent();
        if (intents == null || intents.isEmpty()) {
            return false;
        }
        
        // Check if all intents have domain "UNKNOWN"
        return intents.stream()
                .allMatch(intent -> intent != null && "UNKNOWN".equals(intent.getDomain()));
    }
    
    /**
     * Creates an error response when orchestration fails.
     * 
     * @param correlationId Correlation ID
     * @param exception Exception that occurred
     * @return Error ChatResponse
     */
    private ChatResponse createErrorResponse(String correlationId, Exception exception) {
        log.error("Creating error response - correlationId: {}", correlationId, exception);
        return ChatResponse.builder()
                .answer("I encountered an error processing your request. Please try again.")
                .correlationId(correlationId)
                .explanation("Orchestration error: " + exception.getMessage())
                .build();
    }
    
    /**
     * Masks customer ID for logging (privacy compliance).
     * 
     * @param customerId Customer ID to mask
     * @return Masked customer ID
     */
    private String maskCustomerId(String customerId) {
        if (customerId == null || customerId.length() <= 4) {
            return "****";
        }
        return customerId.substring(0, 2) + "****" + customerId.substring(customerId.length() - 2);
    }
}