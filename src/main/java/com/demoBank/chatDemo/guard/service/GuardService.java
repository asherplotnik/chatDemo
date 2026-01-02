package com.demoBank.chatDemo.guard.service;

import com.demoBank.chatDemo.guard.dto.GuardApiResponse;
import com.demoBank.chatDemo.guard.exception.MaliciousContentException;
import com.demoBank.chatDemo.guard.exception.SecurityContentException;
import com.demoBank.chatDemo.guard.model.GuardFunctionDefinition;
import com.demoBank.chatDemo.guard.model.GuardResult;
import com.demoBank.chatDemo.guard.prompt.GuardSystemPrompt;
import com.demoBank.chatDemo.translation.dto.GroqApiRequest;
import com.demoBank.chatDemo.translation.dto.GroqApiResponse;
import com.demoBank.chatDemo.translation.service.GroqApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Security guard service - checks for prompt injections and malicious intent.
 * 
 * Responsibilities:
 * - Detect prompt injection patterns
 * - Detect signs of malicious intent
 * - Assess risk level of incoming messages
 * - Block unsafe content before it reaches downstream services
 * 
 * This service should be called BEFORE translation to prevent malicious content
 * from being processed or translated.
 * 
 * Uses Groq API with different system prompts for security checks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuardService {
    
    private final GroqApiClient groqApiClient;
    private final ObjectMapper objectMapper;
    
    @Value("${guard.use-groq-api:true}")
    private boolean useGroqApi;
    
    @Value("${guard.check-type:comprehensive}")
    private String checkType; // Options: "comprehensive", "prompt-injection", "malicious-intent"
    
    @Value("${groq.api.model:llama-3.3-70b-versatile}")
    private String guardModel;
    
    /**
     * Validates a message for prompt injections and malicious intent.
     * 
     * @param messageText The message text to validate
     * @param correlationId Correlation ID for logging
     * @return GuardResult containing validation results
     * @throws MaliciousContentException if malicious content is detected
     */
    public GuardResult validateMessage(String messageText, String correlationId) {
        log.debug("Validating message for security threats - correlationId: {}, useGroqApi: {}, checkType: {}", 
                correlationId, useGroqApi, checkType);
        
        if (messageText == null || messageText.isBlank()) {
            log.warn("Empty message provided for validation - correlationId: {}", correlationId);
            return GuardResult.builder()
                    .isSafe(true)
                    .promptInjectionDetected(false)
                    .maliciousIntentDetected(false)
                    .riskScore(0.0)
                    .confidence(1.0)
                    .build();
        }
        
        if (useGroqApi) {
            return validateWithGroqApi(messageText, correlationId);
        } else {
            return createStubGuardResult(correlationId);
        }
    }
    
    /**
     * Validates message using Groq API with function calling.
     * Uses structured function calls instead of JSON parsing for more reliable results.
     * 
     * @param messageText Message text to validate
     * @param correlationId Correlation ID for logging
     * @return GuardResult with validation results
     */
    private GuardResult validateWithGroqApi(String messageText, String correlationId) {
        try {
            String systemPrompt = getSystemPromptForCheckType();
            
            // Create function definition for guard check
            GroqApiRequest.Tool guardTool = GroqApiRequest.Tool.builder()
                    .type("function")
                    .function(GroqApiRequest.Function.builder()
                            .name(GuardFunctionDefinition.FUNCTION_NAME)
                            .description(GuardFunctionDefinition.FUNCTION_DESCRIPTION)
                            .parameters(GuardFunctionDefinition.getFunctionSchema())
                            .build())
                    .build();
            
            log.info("Calling Groq API with function calling for security guard check - correlationId: {}, checkType: {}, message length: {}", 
                    correlationId, checkType, messageText.length());
            
            // Use "required" to force the function call, or "auto" to let the model decide
            GroqApiResponse groqResponse = groqApiClient.callGroqApiWithTools(
                    systemPrompt, 
                    messageText,
                    List.of(guardTool),
                    "required", // Force the function call - Groq API only accepts string values: "none", "auto", "required"
                    guardModel
            );
            
            // Check if response contains tool calls
            if (!groqResponse.hasToolCalls()) {
                log.warn("Groq API did not return tool calls for guard check - correlationId: {}", correlationId);
                throw new SecurityContentException("Groq API did not return expected function call");
            }
            
            // Extract function call arguments
            GuardApiResponse guardResponse = parseFunctionCallResponse(groqResponse, correlationId);
            
            GuardResult result = GuardResult.builder()
                    .isSafe(guardResponse.getIsSafe() != null ? guardResponse.getIsSafe() : true)
                    .promptInjectionDetected(guardResponse.getPromptInjectionDetected() != null ? guardResponse.getPromptInjectionDetected() : false)
                    .maliciousIntentDetected(guardResponse.getMaliciousIntentDetected() != null ? guardResponse.getMaliciousIntentDetected() : false)
                    .unpermittedActionDetected(guardResponse.getUnpermittedActionDetected() != null ? guardResponse.getUnpermittedActionDetected() : false)
                    .riskScore(guardResponse.getRiskScore() != null ? guardResponse.getRiskScore() : 0.0)
                    .confidence(guardResponse.getConfidence() != null ? guardResponse.getConfidence() : 0.0)
                    .rejectionReason(guardResponse.getRejectionReason())
                    .build();
            
            log.info("Guard check completed via Groq API (function calling) - correlationId: {}, isSafe: {}, riskScore: {}, confidence: {}, tokens: {}", 
                    correlationId, 
                    result.isSafe(), 
                    result.getRiskScore(),
                    result.getConfidence(),
                    groqResponse.getUsage() != null ? groqResponse.getUsage().getTotalTokens() : "unknown");
            
            return result;
            
        } catch (Exception e) {
            log.error("Error validating message with Groq API - correlationId: {}, error: {}", correlationId, e.getMessage(), e);
            
            // On error, fall back to stub (fail open for now, but log the error)
            // In production, you might want to fail closed
            return createStubGuardResult(correlationId);
        }
    }
    
    /**
     * Parses function call response from Groq API.
     * Extracts the function arguments which contain the structured guard result.
     * 
     * @param groqResponse Groq API response containing tool calls
     * @param correlationId Correlation ID for logging
     * @return Parsed GuardApiResponse
     */
    private GuardApiResponse parseFunctionCallResponse(GroqApiResponse groqResponse, String correlationId) {
        try {
            List<GroqApiResponse.ToolCall> toolCalls = groqResponse.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                throw new SecurityContentException("No tool calls found in response");
            }
            
            // Find the guard function call
            GroqApiResponse.ToolCall guardToolCall = toolCalls.stream()
                    .filter(tc -> GuardFunctionDefinition.FUNCTION_NAME.equals(tc.getFunction().getName()))
                    .findFirst()
                    .orElseThrow(() -> new SecurityContentException("Guard function call not found in response"));
            
            String argumentsJson = guardToolCall.getFunction().getArguments();
            if (argumentsJson == null || argumentsJson.isBlank()) {
                throw new SecurityContentException("Function arguments are empty");
            }
            
            // Parse the function arguments JSON
            return objectMapper.readValue(argumentsJson, GuardApiResponse.class);
            
        } catch (Exception e) {
            log.error("Error parsing function call response - correlationId: {}, error: {}", correlationId, e.getMessage(), e);
            throw new SecurityContentException("Failed to parse function call response: " + e.getMessage());
        }
    }
    
    /**
     * Gets the appropriate system prompt based on check type configuration.
     * 
     * @return System prompt string
     */
    private String getSystemPromptForCheckType() {
        return switch (checkType.toLowerCase()) {
            case "prompt-injection" -> GuardSystemPrompt.promptInjectionDetectionPrompt;
            case "malicious-intent" -> GuardSystemPrompt.maliciousIntentDetectionPrompt;
            case "comprehensive" -> GuardSystemPrompt.comprehensiveSecurityPrompt;
            default -> {
                log.warn("Unknown check type: {}, using comprehensive check", checkType);
                yield  GuardSystemPrompt.condensedComprehensiveSecurityPrompt;
            }
        };
    }
    
    
    /**
     * Creates a stub guard result for POC purposes or fallback.
     * 
     * @param correlationId Correlation ID for logging
     * @return GuardResult that always passes
     */
    private GuardResult createStubGuardResult(String correlationId) {
        log.debug("Using stub guard result - correlationId: {}", correlationId);
        
        return GuardResult.builder()
                .isSafe(true)
                .promptInjectionDetected(false)
                .maliciousIntentDetected(false)
                .unpermittedActionDetected(false)
                .riskScore(0.0)
                .confidence(1.0)
                .build();
    }
    
    /**
     * Checks if the message contains prompt injection patterns.
     * 
     * @param messageText The message text to check
     * @return true if prompt injection patterns are detected
     */
    private boolean detectPromptInjection(String messageText) {
        // TODO: Implement prompt injection detection
        // Examples to detect:
        // - System prompt extraction attempts
        // - Role-playing attacks
        // - Instruction injection patterns
        // - Jailbreak attempts
        return false;
    }
    
    /**
     * Checks if the message contains signs of malicious intent.
     * 
     * @param messageText The message text to check
     * @return true if malicious intent is detected
     */
    private boolean detectMaliciousIntent(String messageText) {
        // TODO: Implement malicious intent detection
        // Examples to detect:
        // - Social engineering attempts
        // - Phishing patterns
        // - Data exfiltration attempts
        // - Unauthorized access attempts
        return false;
    }
    
    /**
     * Calculates a risk score for the message.
     * 
     * @param messageText The message text to assess
     * @return Risk score between 0.0 (safe) and 1.0 (highly dangerous)
     */
    private double calculateRiskScore(String messageText) {
        // TODO: Implement risk scoring algorithm
        // Consider multiple factors:
        // - Prompt injection patterns
        // - Malicious intent indicators
        // - Message length anomalies
        // - Character encoding tricks
        // - Context-based analysis
        return 0.0;
    }
}
