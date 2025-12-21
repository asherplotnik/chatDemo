package com.demoBank.chatDemo.orchestrator.service;

import com.demoBank.chatDemo.gateway.dto.ChatResponse;
import com.demoBank.chatDemo.gateway.model.RequestContext;
import com.demoBank.chatDemo.orchestrator.dto.IntentExtractionResponse;
import com.demoBank.chatDemo.orchestrator.model.IntentFunctionDefinition;
import com.demoBank.chatDemo.orchestrator.model.OrchestrationState;
import com.demoBank.chatDemo.orchestrator.prompt.ConversationalResponsePrompt;
import com.demoBank.chatDemo.orchestrator.prompt.IntentExtractionPrompt;
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
 * Service for intent processing - extraction and handling.
 * 
 * Handles:
 * - Intent extraction using LLM with function calling
 * - Handling UNKNOWN intents with conversational responses
 * - Clarification context handling
 * - System prompt selection based on clarification state
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentService {
    
    private final GroqApiClient groqApiClient;
    private final ObjectMapper objectMapper;
    
    @Value("${groq.api.intent-extraction.model:llama-3.3-70b-versatile}")
    private String intentExtractionModel;
    
    /**
     * Extracts structured intent from user message.
     * If clarification was applied, uses system prompt with clarification context and defaults.
     * 
     * @param state Current orchestration state
     * @param requestContext Request context
     * @return Updated orchestration state with extracted intent
     */
    public OrchestrationState extractIntent(OrchestrationState state, RequestContext requestContext) {
        String correlationId = requestContext.getCorrelationId();
        log.debug("Step INTENT_EXTRACT - correlationId: {}", correlationId);
        
        String systemPrompt = getSystemPromptForIntentExtraction(state, correlationId);
        String messageText = requestContext.getTranslatedMessageText();
        
        try {
            // Create function definition for intent extraction
            GroqApiRequest.Tool intentTool = GroqApiRequest.Tool.builder()
                    .type("function")
                    .function(GroqApiRequest.Function.builder()
                            .name(IntentFunctionDefinition.FUNCTION_NAME)
                            .description(IntentFunctionDefinition.FUNCTION_DESCRIPTION)
                            .parameters(IntentFunctionDefinition.getFunctionSchema())
                            .build())
                    .build();
            
            log.info("Calling Groq API for intent extraction - correlationId: {}, message length: {}", 
                    correlationId, messageText.length());
            
            // Call Groq API with function calling
            GroqApiResponse groqResponse = groqApiClient.callGroqApiWithTools(
                    systemPrompt,
                    messageText,
                    List.of(intentTool),
                    "required", // Force the function call
                    intentExtractionModel
            );
            
            // Check if response contains tool calls
            if (!groqResponse.hasToolCalls()) {
                log.warn("Groq API did not return tool calls for intent extraction - correlationId: {}", correlationId);
                throw new IllegalStateException("Groq API did not return expected function call for intent extraction");
            }
            
            // Parse function call response
            IntentExtractionResponse intentResponse = parseIntentExtractionResponse(groqResponse, correlationId);
            
            // Store extracted intents in state
            state.setExtractedIntent(intentResponse.getIntents());
            
            // Handle clarification needs
            if (Boolean.TRUE.equals(intentResponse.getNeedsClarification())) {
                state.setNeedsClarifier(true);
                state.setClarificationNeeded(intentResponse.getClarificationNeeded());
                log.info("Intent extraction indicates clarification needed - correlationId: {}, clarificationNeeded: {}", 
                        correlationId, intentResponse.getClarificationNeeded());
            }
            
            // Log if defaults were used
            if (Boolean.TRUE.equals(intentResponse.getUsedDefault())) {
                log.info("Defaults were applied during intent extraction - correlationId: {}, reason: {}, intents: {}", 
                        correlationId, intentResponse.getDefaultReason(), intentResponse.getIntents().size());
            }
            
            log.info("Intent extraction completed - correlationId: {}, intents: {}, confidence: {}, tokens: {}", 
                    correlationId,
                    intentResponse.getIntents() != null ? intentResponse.getIntents().size() : 0,
                    intentResponse.getConfidence(),
                    groqResponse.getUsage() != null ? groqResponse.getUsage().getTotalTokens() : "unknown");
            
        } catch (Exception e) {
            log.error("Error extracting intent with Groq API - correlationId: {}, error: {}", correlationId, e.getMessage(), e);
            // On error, set needsClarifier to true to ask for clarification
            state.setNeedsClarifier(true);
            state.setClarificationNeeded("intent_extraction_failed");
            throw new IllegalStateException("Failed to extract intent: " + e.getMessage(), e);
        }
        
        return state;
    }
    
    /**
     * Parses function call response from Groq API for intent extraction.
     * 
     * @param groqResponse Groq API response containing tool calls
     * @param correlationId Correlation ID for logging
     * @return Parsed IntentExtractionResponse
     */
    private IntentExtractionResponse parseIntentExtractionResponse(GroqApiResponse groqResponse, String correlationId) {
        try {
            List<GroqApiResponse.ToolCall> toolCalls = groqResponse.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                throw new IllegalStateException("No tool calls found in response");
            }
            
            // Find the intent extraction function call
            GroqApiResponse.ToolCall intentToolCall = toolCalls.stream()
                    .filter(tc -> IntentFunctionDefinition.FUNCTION_NAME.equals(tc.getFunction().getName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Intent extraction function call not found in response"));
            
            String argumentsJson = intentToolCall.getFunction().getArguments();
            if (argumentsJson == null || argumentsJson.isBlank()) {
                throw new IllegalStateException("Function arguments are empty");
            }
            
            // Parse the function arguments JSON
            return objectMapper.readValue(argumentsJson, IntentExtractionResponse.class);
            
        } catch (Exception e) {
            log.error("Error parsing intent extraction response - correlationId: {}, error: {}", correlationId, e.getMessage(), e);
            throw new IllegalStateException("Failed to parse intent extraction response: " + e.getMessage(), e);
        }
    }
    
    /**
     * Gets the appropriate system prompt for intent extraction.
     * If clarification context exists, uses prompt with clarification and defaults.
     * Otherwise, uses base prompt.
     * 
     * @param state Orchestration state
     * @param correlationId Correlation ID for logging
     * @return System prompt string
     */
    private String getSystemPromptForIntentExtraction(OrchestrationState state, String correlationId) {
        // Check if we're processing a clarification answer
        if (state.getClarificationAnswer() != null && state.getClarificationContext() != null) {
            log.info("Extracting intent with clarification context - correlationId: {}, context: {}, answer: {}", 
                    correlationId, state.getClarificationContext(), state.getClarificationAnswer());
            
            // Get the original question from session context (still available since we haven't cleared it yet)
            String originalQuestion = null;
            if (state.getSessionContext() != null && 
                state.getSessionContext().getClarificationState() != null) {
                originalQuestion = state.getSessionContext().getClarificationState().getQuestion();
            }
            
            // Use prompt with clarification context and default fallbacks
            String systemPrompt = IntentExtractionPrompt.getSystemPromptWithClarification(
                state.getClarificationContext(),
                state.getClarificationAnswer(),
                state.getExpectedAnswerType(),
                originalQuestion
            );
            
            // Clear clarification state from session and state after using it
            if (state.getSessionContext() != null) {
                state.getSessionContext().setClarificationState(null);
            }
            state.setClarificationAnswer(null);
            state.setClarificationContext(null);
            state.setExpectedAnswerType(null);
            state.setAwaitingClarification(false);
            
            return systemPrompt;
        } else {
            // Normal intent extraction without clarification
            return IntentExtractionPrompt.getBaseSystemPrompt();
        }
    }
    
    /**
     * Handles UNKNOWN intents (conversational, non-banking messages).
     * Generates appropriate conversational responses using LLM.
     * 
     * @param state Current orchestration state
     * @param requestContext Request context
     * @return Updated orchestration state with conversational response
     */
    public OrchestrationState handleUnknownIntent(OrchestrationState state, RequestContext requestContext) {
        String correlationId = requestContext.getCorrelationId();
        log.debug("Step HANDLE_UNKNOWN_INTENT - correlationId: {}", correlationId);
        
        String messageText = requestContext.getTranslatedMessageText();
        
        try {
            // Get conversational response prompt (all responses in English, translation handled later)
            String systemPrompt = ConversationalResponsePrompt.SYSTEM_PROMPT;
            
            log.info("Calling Groq API for conversational response - correlationId: {}, message length: {}", 
                    correlationId, messageText.length());
            
            // Call Groq API without function calling (simple chat completion)
            GroqApiResponse groqResponse = groqApiClient.callGroqApi(
                    systemPrompt,
                    messageText,
                    intentExtractionModel // Reuse the same model as intent extraction
            );
            
            // Extract response content
            String responseText = groqResponse.getContent();
            if (responseText == null || responseText.isBlank()) {
                log.warn("Groq API returned empty response for conversational message - correlationId: {}", correlationId);
                throw new IllegalStateException("Groq API returned empty response");
            }
            
            // Create ChatResponse
            ChatResponse response = ChatResponse.builder()
                    .answer(responseText.trim())
                    .correlationId(correlationId)
                    .explanation("Conversational response for UNKNOWN intent")
                    .build();
            
            state.setResponse(response);
            
            log.info("Conversational response generated - correlationId: {}, response length: {}, tokens: {}", 
                    correlationId,
                    responseText.length(),
                    groqResponse.getUsage() != null ? groqResponse.getUsage().getTotalTokens() : "unknown");
            
        } catch (Exception e) {
            log.error("Error generating conversational response with Groq API - correlationId: {}, error: {}", 
                    correlationId, e.getMessage(), e);
            // Don't set response - will use fallback
            // Log error but don't throw - let fallback handle it
        }
        
        return state;
    }
    
    /**
     * Creates a fallback response for UNKNOWN intents when handleUnknownIntent fails.
     * 
     * @param correlationId Correlation ID
     * @return Fallback ChatResponse
     */
    public ChatResponse createUnknownIntentFallbackResponse(String correlationId) {
        return ChatResponse.builder()
                .answer("Hello! How can I assist you with your banking needs today?")
                .correlationId(correlationId)
                .explanation("Fallback response for UNKNOWN intent")
                .build();
    }
}
