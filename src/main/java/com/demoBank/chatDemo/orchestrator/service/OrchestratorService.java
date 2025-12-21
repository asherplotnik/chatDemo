package com.demoBank.chatDemo.orchestrator.service;

import com.demoBank.chatDemo.gateway.dto.ChatResponse;
import com.demoBank.chatDemo.gateway.model.ChatSessionContext;
import com.demoBank.chatDemo.gateway.model.RequestContext;
import com.demoBank.chatDemo.orchestrator.dto.IntentExtractionResponse;
import com.demoBank.chatDemo.orchestrator.model.IntentFunctionDefinition;
import com.demoBank.chatDemo.orchestrator.model.OrchestrationState;
import com.demoBank.chatDemo.orchestrator.model.TimeRangeFunctionDefinition;
import com.demoBank.chatDemo.orchestrator.prompt.IntentExtractionPrompt;
import com.demoBank.chatDemo.orchestrator.prompt.ConversationalResponsePrompt;
import com.demoBank.chatDemo.orchestrator.prompt.TimeRangeResolutionPrompt;
import com.demoBank.chatDemo.orchestrator.util.TimeRangeResolver;
import com.demoBank.chatDemo.translation.dto.GroqApiRequest;
import com.demoBank.chatDemo.translation.dto.GroqApiResponse;
import com.demoBank.chatDemo.translation.service.GroqApiClient;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
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
    
    @Value("${groq.api.intent-extraction.model:llama-3.3-70b-versatile}")
    private String intentExtractionModel;
    
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
            state = loadContext(state, requestContext);
            
            // Step 2: IF AWAITING_CLARIFICATION ? APPLY_CLARIFICATION then INTENT_EXTRACT : INTENT_EXTRACT
            if (state.isAwaitingClarification()) {
                state = applyClarification(state, requestContext);
                // After applying clarification, still need to extract intent with clarification context
            }
            // Always extract intent (with clarification context if clarification was applied)
            state = intentExtract(state, requestContext);
            
            // Check if all intents are UNKNOWN (conversational, non-banking messages)
            if (isAllIntentsUnknown(state)) {
                log.info("All intents are UNKNOWN - taking conversational response path - correlationId: {}", correlationId);
                // Handle UNKNOWN intent with conversational response (skip data-fetching steps)
                state = handleUnknownIntent(state, requestContext);
                saveContext(state, requestContext);
                // Ensure response is created before returning
                if (state.getResponse() == null) {
                    log.warn("handleUnknownIntent did not create response - correlationId: {}, creating fallback response", correlationId);
                    state.setResponse(createUnknownIntentFallbackResponse(requestContext.getCorrelationId()));
                }
                return state.getResponse();
            }
            
            // Step 3: RESOLVE_TIME_RANGE
            state = resolveTimeRange(state, requestContext);
            
            // Step 4: PLAN
            state = plan(state, requestContext);
            
            // Step 5: IF NEEDS_CLARIFIER -> ASK_CLARIFIER -> SAVE_CONTEXT -> END
            if (state.needsClarifier()) {
                state = askClarifier(state, requestContext);
                saveContext(state, requestContext);
                // Ensure response is created before returning
                if (state.getResponse() == null) {
                    log.warn("askClarifier did not create response - correlationId: {}, creating fallback response", correlationId);
                    state.setResponse(createClarificationFallbackResponse(requestContext.getCorrelationId(), state.getClarificationNeeded()));
                }
                return state.getResponse();
            }
            
            // Step 6: FETCH
            state = fetch(state, requestContext);
            
            // Step 7: NORMALIZE
            state = normalize(state, requestContext);
            
            // Step 8: COMPUTE
            state = compute(state, requestContext);
            
            // Step 9: DRAFT
            state = draft(state, requestContext);
            
            // Step 10: SAVE_CONTEXT
            saveContext(state, requestContext);
            
            // Step 11: RESPOND
            return respond(state, requestContext);
            
        } catch (Exception e) {
            log.error("Error in orchestration - correlationId: {}", correlationId, e);
            return createErrorResponse(correlationId, e);
        }
    }
    
    /**
     * Step 1: LOAD_CONTEXT - Load conversation context from session.
     * 
     * @param state Current orchestration state
     * @param requestContext Request context
     * @return Updated orchestration state with loaded context
     */
    private OrchestrationState loadContext(OrchestrationState state, RequestContext requestContext) {
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
        if (sessionContext.getLastAccessedAt() != null) {
            Duration timeSinceLastAccess = Duration.between(sessionContext.getLastAccessedAt(), Instant.now());
            Duration sessionTtl = Duration.ofMinutes(30);
            
            if (timeSinceLastAccess.compareTo(sessionTtl) > 0) {
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
        
        // Check if awaiting clarification
        if (sessionContext.getClarificationState() != null) {
            state.setAwaitingClarification(true);
            log.debug("Awaiting clarification - correlationId: {}, question: {}, expectedType: {}",
                    correlationId,
                    sessionContext.getClarificationState().getQuestion(),
                    sessionContext.getClarificationState().getExpectedAnswerType());
        }
        
        // Load previous intent for follow-up questions
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
        
        // Load previous time range for follow-up questions
        if (sessionContext.getLastResolvedTimeRange() != null) {
            state.setResolvedTimeRange(sessionContext.getLastResolvedTimeRange());
            log.debug("Loaded previous time range - correlationId: {}, fromDate: {}, toDate: {}",
                    correlationId,
                    sessionContext.getLastResolvedTimeRange().getFromDate(),
                    sessionContext.getLastResolvedTimeRange().getToDate());
        }
        
        // Load previous selected entities for follow-up questions
        if (sessionContext.getLastSelectedEntities() != null) {
            log.debug("Loaded previous selected entities - correlationId: {}, accountIds: {}, cardIds: {}",
                    correlationId,
                    sessionContext.getLastSelectedEntities().getAccountIds() != null 
                            ? sessionContext.getLastSelectedEntities().getAccountIds().size() : 0,
                    sessionContext.getLastSelectedEntities().getCardIds() != null 
                            ? sessionContext.getLastSelectedEntities().getCardIds().size() : 0);
        }
        
        // Initialize defaults if not present
        if (sessionContext.getDefaults() == null) {
            ChatSessionContext.SessionDefaults defaults = ChatSessionContext.SessionDefaults.builder()
                    .transactionStatus("posted") // Default to posted transactions
                    .pagingCursorPolicy("auto") // Default to auto paging
                    .pageSize(10) // Default page size
                    .build();
            sessionContext.setDefaults(defaults);
            log.debug("Initialized default preferences - correlationId: {}", correlationId);
        }
        
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
        
        return state;
    }
    
    /**
     * Step 2a: APPLY_CLARIFICATION - Extract answer to previously asked clarifying question.
     * Stores the answer and clarification context in state for intentExtract to use.
     * 
     * @param state Current orchestration state
     * @param requestContext Request context
     * @return Updated orchestration state with clarification answer stored
     */
    private OrchestrationState applyClarification(OrchestrationState state, RequestContext requestContext) {
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
        
        // Store clarification answer and context in state for intentExtract to use
        // This will be used by intentExtract to understand the context and apply defaults if needed
        state.setClarificationAnswer(userAnswer);
        state.setClarificationContext(clarificationContext);
        state.setExpectedAnswerType(expectedAnswerType);
        
        // Store the question in state temporarily so intentExtract can use it in the prompt
        // (We'll clear clarificationState after intentExtract uses it)
        // Note: The question is still accessible via clarificationState until we clear it
        
        return state;
    }
    
    /**
     * Step 2b: INTENT_EXTRACT - Extract structured intent from user message.
     * If clarification was applied, uses system prompt with clarification context and defaults.
     * 
     * @param state Current orchestration state
     * @param requestContext Request context
     * @return Updated orchestration state with extracted intent
     */
    private OrchestrationState intentExtract(OrchestrationState state, RequestContext requestContext) {
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
     * Step 3: RESOLVE_TIME_RANGE - Resolve absolute dates from relative time expressions.
     * 
     * Uses deterministic logic for common patterns, falls back to LLM for complex cases.
     * 
     * @param state Current orchestration state
     * @param requestContext Request context
     * @return Updated orchestration state with resolved time range
     */
    private OrchestrationState resolveTimeRange(OrchestrationState state, RequestContext requestContext) {
        String correlationId = requestContext.getCorrelationId();
        log.debug("Step RESOLVE_TIME_RANGE - correlationId: {}", correlationId);
        
        // Get timezone from session context if available
        String timezone = null;
        if (state.getSessionContext() != null) {
            timezone = state.getSessionContext().getTimezone();
        }
        
        // Get time range hint from extracted intents
        // Use the first intent's timeRangeHint (if multiple intents, they should have same time range)
        String timeRangeHint = null;
        List<IntentExtractionResponse.IntentData> intents = state.getExtractedIntent();
        if (intents != null && !intents.isEmpty()) {
            IntentExtractionResponse.IntentData firstIntent = intents.get(0);
            if (firstIntent != null) {
                timeRangeHint = firstIntent.getTimeRangeHint();
            }
        }
        
        // Check if we already have a resolved time range from session context
        if (state.getResolvedTimeRange() != null && state.getResolvedTimeRange() instanceof ChatSessionContext.TimeRange) {
            ChatSessionContext.TimeRange existingRange = (ChatSessionContext.TimeRange) state.getResolvedTimeRange();
            log.debug("Using existing resolved time range from session - correlationId: {}, fromDate: {}, toDate: {}", 
                    correlationId, existingRange.getFromDate(), existingRange.getToDate());
            return state;
        }
        
        // Try deterministic resolution first
        TimeRangeResolver.ResolvedTimeRange resolved = TimeRangeResolver.resolveDeterministically(timeRangeHint, timezone);
        
        if (resolved.needsLLM()) {
            // Complex expression - use LLM
            log.info("Time range hint requires LLM resolution - correlationId: {}, hint: {}", correlationId, timeRangeHint);
            resolved = resolveTimeRangeWithLLM(timeRangeHint, timezone, correlationId);
        }
        
        // Create TimeRange object and store in state
        ChatSessionContext.TimeRange timeRange = ChatSessionContext.TimeRange.builder()
                .fromDate(resolved.getFromDate())
                .toDate(resolved.getToDate())
                .build();
        
        state.setResolvedTimeRange(timeRange);
        
        log.info("Time range resolved - correlationId: {}, hint: {}, fromDate: {}, toDate: {}", 
                correlationId, timeRangeHint != null ? timeRangeHint : "default", 
                resolved.getFromDate(), resolved.getToDate());
        
        return state;
    }
    
    /**
     * Resolves complex time range expressions using LLM with function calling.
     * 
     * @param timeRangeHint Time range hint to resolve
     * @param timezone Timezone string or null
     * @param correlationId Correlation ID for logging
     * @return ResolvedTimeRange with absolute dates
     */
    private TimeRangeResolver.ResolvedTimeRange resolveTimeRangeWithLLM(
            String timeRangeHint, String timezone, String correlationId) {
        
        try {
            String systemPrompt = TimeRangeResolutionPrompt.getSystemPrompt(timezone);
            String userMessage = "Resolve this time expression: " + timeRangeHint;
            
            // Create function definition for time range resolution
            GroqApiRequest.Tool timeRangeTool = GroqApiRequest.Tool.builder()
                    .type("function")
                    .function(GroqApiRequest.Function.builder()
                            .name(TimeRangeFunctionDefinition.FUNCTION_NAME)
                            .description(TimeRangeFunctionDefinition.FUNCTION_DESCRIPTION)
                            .parameters(TimeRangeFunctionDefinition.getFunctionSchema())
                            .build())
                    .build();
            
            log.info("Calling Groq API for time range resolution with function calling - correlationId: {}, hint: {}", 
                    correlationId, timeRangeHint);
            
            // Call Groq API with function calling
            GroqApiResponse groqResponse = groqApiClient.callGroqApiWithTools(
                    systemPrompt,
                    userMessage,
                    List.of(timeRangeTool),
                    "required", // Force the function call
                    intentExtractionModel
            );
            
            // Check if response contains tool calls
            if (!groqResponse.hasToolCalls()) {
                log.warn("Groq API did not return tool calls for time range resolution - correlationId: {}", correlationId);
                return TimeRangeResolver.getDefaultTimeRange();
            }
            
            // Parse function call response
            TimeRangeLLMResponse llmResponse = parseTimeRangeResponse(groqResponse, correlationId);
            
            if (llmResponse.getFromDate() == null || llmResponse.getToDate() == null) {
                log.warn("LLM response missing dates - correlationId: {}, using default", correlationId);
                return TimeRangeResolver.getDefaultTimeRange();
            }
            
            return new TimeRangeResolver.ResolvedTimeRange(
                    llmResponse.getFromDate(),
                    llmResponse.getToDate(),
                    false
            );
            
        } catch (Exception e) {
            log.error("Error resolving time range with LLM - correlationId: {}, error: {}", 
                    correlationId, e.getMessage(), e);
            // Fall back to default
            return TimeRangeResolver.getDefaultTimeRange();
        }
    }
    
    /**
     * Parses function call response from Groq API for time range resolution.
     * 
     * @param groqResponse Groq API response containing tool calls
     * @param correlationId Correlation ID for logging
     * @return Parsed TimeRangeLLMResponse
     */
    private TimeRangeLLMResponse parseTimeRangeResponse(GroqApiResponse groqResponse, String correlationId) {
        try {
            List<GroqApiResponse.ToolCall> toolCalls = groqResponse.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                throw new IllegalStateException("No tool calls found in response");
            }
            
            // Find the time range resolution function call
            GroqApiResponse.ToolCall timeRangeToolCall = toolCalls.stream()
                    .filter(tc -> TimeRangeFunctionDefinition.FUNCTION_NAME.equals(tc.getFunction().getName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Time range resolution function call not found in response"));
            
            String argumentsJson = timeRangeToolCall.getFunction().getArguments();
            if (argumentsJson == null || argumentsJson.isBlank()) {
                throw new IllegalStateException("Function arguments are empty");
            }
            
            // Parse the function arguments JSON
            return objectMapper.readValue(argumentsJson, TimeRangeLLMResponse.class);
            
        } catch (Exception e) {
            log.error("Error parsing time range resolution response - correlationId: {}, error: {}", 
                    correlationId, e.getMessage(), e);
            throw new IllegalStateException("Failed to parse time range resolution response: " + e.getMessage(), e);
        }
    }
    
    /**
     * DTO for LLM time range response (parsed from function call).
     */
    private static class TimeRangeLLMResponse {
        @JsonProperty("fromDate")
        private String fromDate;
        
        @JsonProperty("toDate")
        private String toDate;
        
        public String getFromDate() {
            return fromDate;
        }
        
        public void setFromDate(String fromDate) {
            this.fromDate = fromDate;
        }
        
        public String getToDate() {
            return toDate;
        }
        
        public void setToDate(String toDate) {
            this.toDate = toDate;
        }
    }
    
    /**
     * Step 4: PLAN - Choose which read-only APIs to call.
     * 
     * @param state Current orchestration state
     * @param requestContext Request context
     * @return Updated orchestration state with execution plan
     */
    private OrchestrationState plan(OrchestrationState state, RequestContext requestContext) {
        String correlationId = requestContext.getCorrelationId();
        String customerId = state.getCustomerId();
        
        log.debug("Step PLAN - correlationId: {}, customerId: {}", correlationId, maskCustomerId(customerId));
        
        // TODO: Implement planning logic
        // - Map intent to API endpoints
        // - Determine which APIs to call based on domain/metric
        // - Create execution plan with customerId included
        // - Store plan in state
        // - IMPORTANT: Plan must include customerId from state.getCustomerId() (trusted source)
        // - NEVER use customerId from messageText - only from RequestContext
        return state;
    }
    
    /**
     * Step 5: ASK_CLARIFIER - Ask a clarifying question if intent is ambiguous.
     * 
     * @param state Current orchestration state
     * @param requestContext Request context
     * @return Updated orchestration state with clarification question
     */
    private OrchestrationState askClarifier(OrchestrationState state, RequestContext requestContext) {
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
    private ChatResponse createClarificationFallbackResponse(String correlationId, String clarificationNeeded) {
        String question = clarificationNeeded != null 
                ? "Could you please provide more details about: " + clarificationNeeded + "?"
                : "Could you please provide more details about your request?";
        
        return ChatResponse.builder()
                .answer(question)
                .correlationId(correlationId)
                .explanation("Clarification needed: " + (clarificationNeeded != null ? clarificationNeeded : "unknown"))
                .build();
    }
    
    /**
     * Step 6: FETCH - Call read-only APIs to fetch data.
     * 
     * @param state Current orchestration state
     * @param requestContext Request context
     * @return Updated orchestration state with fetched data
     */
    private OrchestrationState fetch(OrchestrationState state, RequestContext requestContext) {
        String correlationId = requestContext.getCorrelationId();
        String customerId = state.getCustomerId(); // Get from state (validated in RECEIVE)
        
        log.debug("Step FETCH - correlationId: {}, customerId: {}", correlationId, maskCustomerId(customerId));
        
        // TODO: Implement data fetching logic
        // - Execute API calls based on plan
        // - Use customerId from state.getCustomerId() (trusted source from HTTP header)
        // - NEVER use customerId from messageText - only from RequestContext
        // - Handle API errors and retries
        // - Store raw API responses in state
        // - Pass customerId in API request headers/parameters
        
        return state;
    }
    
    /**
     * Step 7: NORMALIZE - Convert API responses to canonical internal models.
     * 
     * @param state Current orchestration state
     * @param requestContext Request context
     * @return Updated orchestration state with normalized data
     */
    private OrchestrationState normalize(OrchestrationState state, RequestContext requestContext) {
        log.debug("Step NORMALIZE - correlationId: {}", requestContext.getCorrelationId());
        // TODO: Implement normalization logic
        // - Convert different API response formats to unified models
        // - Handle different data structures
        // - Store normalized data in state
        return state;
    }
    
    /**
     * Step 8: COMPUTE - Perform deterministic calculations.
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
     * Step 9: DRAFT - Generate answer text and explanation.
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
     * Step 10: SAVE_CONTEXT - Save conversation context to session.
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
     * Step 11: RESPOND - Return final response to user.
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
        
        // TODO: Implement response creation logic
        // - Get drafted response from state
        // - Translate to Hebrew if needed (outbound translation)
        // - Apply compliance masking
        // - Create ChatResponse
        
        // Temporary: Return placeholder response until drafting is implemented
        ChatResponse response = ChatResponse.builder()
                .answer(state.getDraftedAnswer() != null ? state.getDraftedAnswer() : "Processing your request...")
                .correlationId(requestContext.getCorrelationId())
                .explanation(state.getDraftedExplanation() != null ? state.getDraftedExplanation() : "Orchestration in progress")
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
     * Handles UNKNOWN intents (conversational, non-banking messages).
     * Generates appropriate conversational responses using LLM.
     * 
     * @param state Current orchestration state
     * @param requestContext Request context
     * @return Updated orchestration state with conversational response
     */
    private OrchestrationState handleUnknownIntent(OrchestrationState state, RequestContext requestContext) {
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
    private ChatResponse createUnknownIntentFallbackResponse(String correlationId) {
        return ChatResponse.builder()
                .answer("Hello! How can I assist you with your banking needs today?")
                .correlationId(correlationId)
                .explanation("Fallback response for UNKNOWN intent")
                .build();
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
