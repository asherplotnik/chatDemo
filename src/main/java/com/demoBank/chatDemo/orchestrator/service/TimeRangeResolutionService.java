package com.demoBank.chatDemo.orchestrator.service;

import com.demoBank.chatDemo.gateway.model.ChatSessionContext;
import com.demoBank.chatDemo.gateway.model.RequestContext;
import com.demoBank.chatDemo.orchestrator.dto.IntentExtractionResponse;
import com.demoBank.chatDemo.orchestrator.model.OrchestrationState;
import com.demoBank.chatDemo.orchestrator.model.TimeRangeFunctionDefinition;
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

import java.util.List;

/**
 * Service for resolving time range hints to absolute dates.
 * 
 * Handles:
 * - Deterministic resolution for common patterns
 * - LLM-based resolution for complex expressions
 * - Timezone-aware date calculations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimeRangeResolutionService {
    
    private final GroqApiClient groqApiClient;
    private final ObjectMapper objectMapper;
    
    @Value("${groq.api.model:llama-3.3-70b-versatile}")
    private String timeRangeResolutionModel;
    
    /**
     * Resolves absolute dates from relative time expressions.
     * Uses deterministic logic for common patterns, falls back to LLM for complex cases.
     * 
     * @param state Current orchestration state
     * @param requestContext Request context
     * @return Updated orchestration state with resolved time range
     */
    public OrchestrationState resolveTimeRange(OrchestrationState state, RequestContext requestContext) {
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
            resolved = resolveTimeRangeWithLLM(timeRangeHint, timezone, correlationId, state);
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
     * @param state Orchestration state (for conversation summaries)
     * @return ResolvedTimeRange with absolute dates
     */
    private TimeRangeResolver.ResolvedTimeRange resolveTimeRangeWithLLM(
            String timeRangeHint, String timezone, String correlationId, OrchestrationState state) {
        
        try {
            String systemPrompt = TimeRangeResolutionPrompt.getSystemPrompt(timezone);
            
            // Add conversation summaries if available
            String conversationContext = formatConversationSummaries(state, correlationId);
            if (conversationContext != null && !conversationContext.isBlank()) {
                systemPrompt = systemPrompt + "\n\n" + conversationContext;
            }
            
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
                    timeRangeResolutionModel
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
     * Formats conversation summaries for inclusion in LLM prompts.
     * 
     * @param state Orchestration state with session context
     * @param correlationId Correlation ID for logging
     * @return Formatted conversation context string or null if no summaries available
     */
    private String formatConversationSummaries(OrchestrationState state, String correlationId) {
        if (state == null || state.getSessionContext() == null) {
            return null;
        }
        
        List<ChatSessionContext.ConversationSummary> summaries = 
            state.getSessionContext().getConversationSummaries();
        
        if (summaries == null || summaries.isEmpty()) {
            return null;
        }
        
        StringBuilder context = new StringBuilder();
        context.append("PREVIOUS CONVERSATION CONTEXT:\n");
        context.append("The following is a summary of previous questions and responses in this conversation.\n");
        context.append("Use this context to understand what the customer has already seen.\n\n");
        
        int index = 1;
        for (ChatSessionContext.ConversationSummary summary : summaries) {
            context.append("[Message #").append(index).append("]\n");
            context.append("User: \"").append(summary.getUserMessage()).append("\"\n");
            context.append("Response: ").append(summary.getResponseSummary()).append("\n\n");
            index++;
        }
        
        context.append("IMPORTANT: Use this context to avoid redundant requests and to understand follow-up questions.");
        
        log.debug("Formatted conversation summaries for time range prompt - correlationId: {}, summaryCount: {}", 
                correlationId, summaries.size());
        
        return context.toString();
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
}
