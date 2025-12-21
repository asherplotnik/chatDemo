package com.demoBank.chatDemo.orchestrator.service;

import com.demoBank.chatDemo.gateway.dto.ChatResponse;
import com.demoBank.chatDemo.gateway.model.ChatSessionContext;
import com.demoBank.chatDemo.gateway.model.RequestContext;
import com.demoBank.chatDemo.orchestrator.model.OrchestrationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Service for handling clarification flow.
 * 
 * Handles:
 * - Asking clarifying questions when intent is ambiguous
 * - Extracting clarification answers from user messages
 * - Storing clarification context in orchestration state
 * - Preparing state for intent extraction with clarification context
 */
@Slf4j
@Service
public class ClarificationService {
    
    /**
     * Extracts answer to previously asked clarifying question.
     * Stores the answer and clarification context in state for intent extraction to use.
     * 
     * @param state Current orchestration state
     * @param requestContext Request context
     * @return Updated orchestration state with clarification answer stored
     */
    public OrchestrationState applyClarification(OrchestrationState state, RequestContext requestContext) {
        String correlationId = requestContext.getCorrelationId();
        log.debug("Step APPLY_CLARIFICATION - correlationId: {}", correlationId);
        
        ChatSessionContext.ClarificationState clarificationState = 
            state.getSessionContext().getClarificationState();
        
        if (clarificationState == null) {
            log.warn("No clarification state found but awaiting clarification - correlationId: {}", correlationId);
            state.setAwaitingClarification(false);
            return state;
        }
        
        String userAnswer = requestContext.getTranslatedMessageText();
        String clarificationContext = clarificationState.getClarificationContext();
        String expectedAnswerType = clarificationState.getExpectedAnswerType();
        String question = clarificationState.getQuestion();
        
        log.info("Processing clarification answer - correlationId: {}, context: {}, expectedType: {}, question: {}, answer: {}", 
                correlationId, clarificationContext, expectedAnswerType, question, userAnswer);
        
        // Store clarification answer and context in state for intent extraction to use
        // This will be used by intent extraction to understand the context and apply defaults if needed
        state.setClarificationAnswer(userAnswer);
        state.setClarificationContext(clarificationContext);
        state.setExpectedAnswerType(expectedAnswerType);
        
        // Store the question in state temporarily so intent extraction can use it in the prompt
        // (We'll clear clarificationState after intent extraction uses it)
        // Note: The question is still accessible via clarificationState until we clear it
        
        return state;
    }
    
    /**
     * Asks a clarifying question if intent is ambiguous.
     * 
     * @param state Current orchestration state
     * @param requestContext Request context
     * @return Updated orchestration state with clarification question
     */
    public OrchestrationState askClarifier(OrchestrationState state, RequestContext requestContext) {
        String correlationId = requestContext.getCorrelationId();
        log.debug("Step ASK_CLARIFIER - correlationId: {}", correlationId);
        
        String clarificationNeeded = state.getClarificationNeeded();
        String question = generateClarificationQuestion(clarificationNeeded, state);
        
        // Set clarification state in session
        if (state.getSessionContext() != null) {
            ChatSessionContext.ClarificationState clarificationState = ChatSessionContext.ClarificationState.builder()
                    .question(question)
                    .expectedAnswerType(getExpectedAnswerType(clarificationNeeded))
                    .clarificationContext(clarificationNeeded)
                    .askedAt(Instant.now())
                    .build();
            state.getSessionContext().setClarificationState(clarificationState);
        }
        
        // Create response with clarification question
        ChatResponse response = ChatResponse.builder()
                .answer(question)
                .correlationId(correlationId)
                .explanation("Clarification needed: " + clarificationNeeded)
                .build();
        
        state.setResponse(response);
        log.info("Clarification question asked - correlationId: {}, question: {}", correlationId, question);
        
        return state;
    }
    
    /**
     * Generates a clarification question based on what needs clarification.
     * 
     * @param clarificationNeeded What needs clarification (e.g., "domain", "metric", "time_range", "account_selection")
     * @param state Orchestration state
     * @return Clarification question string
     */
    private String generateClarificationQuestion(String clarificationNeeded, OrchestrationState state) {
        if (clarificationNeeded == null) {
            return "Could you please provide more details about your request?";
        }
        
        return switch (clarificationNeeded.toLowerCase()) {
            case "domain" -> "Which type of information are you looking for? (e.g., accounts, credit cards, loans, mortgages, deposits, securities)";
            case "metric" -> "What would you like to know? (e.g., balance, list of transactions, total amount, count)";
            case "time_range" -> "Which time period would you like? (e.g., last week, last month, yesterday, or specific dates)";
            case "account_selection" -> "Which account would you like to check? (e.g., main account, or specify account number)";
            default -> "Could you please provide more details about: " + clarificationNeeded + "?";
        };
    }
    
    /**
     * Gets the expected answer type for a clarification context.
     * 
     * @param clarificationNeeded What needs clarification
     * @return Expected answer type
     */
    private String getExpectedAnswerType(String clarificationNeeded) {
        if (clarificationNeeded == null) {
            return "text";
        }
        
        return switch (clarificationNeeded.toLowerCase()) {
            case "time_range" -> "time_range";
            case "account_selection" -> "account";
            case "domain" -> "domain";
            case "metric" -> "metric";
            default -> "text";
        };
    }
    
    /**
     * Creates a fallback clarification response if askClarifier fails to create one.
     * 
     * @param correlationId Correlation ID
     * @param clarificationNeeded What needs clarification
     * @return Fallback ChatResponse
     */
    public ChatResponse createClarificationFallbackResponse(String correlationId, String clarificationNeeded) {
        String question = clarificationNeeded != null 
                ? "Could you please provide more details about: " + clarificationNeeded + "?"
                : "Could you please provide more details about your request?";
        
        return ChatResponse.builder()
                .answer(question)
                .correlationId(correlationId)
                .explanation("Clarification needed: " + (clarificationNeeded != null ? clarificationNeeded : "unknown"))
                .build();
    }
}
