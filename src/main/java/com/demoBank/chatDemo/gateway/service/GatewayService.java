package com.demoBank.chatDemo.gateway.service;

import com.demoBank.chatDemo.gateway.dto.ChatRequest;
import com.demoBank.chatDemo.gateway.dto.ChatResponse;
import com.demoBank.chatDemo.gateway.exception.MissingCustomerIdException;
import com.demoBank.chatDemo.gateway.exception.RateLimitExceededException;
import com.demoBank.chatDemo.gateway.model.ChatSessionContext;
import com.demoBank.chatDemo.gateway.model.RequestContext;
import com.demoBank.chatDemo.gateway.util.CustomerIdMasker;
import com.demoBank.chatDemo.guard.exception.MaliciousContentException;
import com.demoBank.chatDemo.guard.model.GuardResult;
import com.demoBank.chatDemo.guard.service.GuardService;
import com.demoBank.chatDemo.language.model.LanguageDetectionResult;
import com.demoBank.chatDemo.language.service.LanguageDetector;
import com.demoBank.chatDemo.orchestrator.service.OrchestratorService;
import com.demoBank.chatDemo.translation.model.TranslationResult;
import com.demoBank.chatDemo.translation.service.InboundTranslator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Gateway service - handles all business logic for the gateway.
 * 
 * Responsibilities:
 * - Extract and validate customerId from header (trusted source)
 * - Generate correlationId
 * - Manage session context
 * - Enforce rate limiting
 * - Detect language on every message (handles language switching)
 * - Store detected language in session (first time only, for audit)
 * - Validate message security (prompt injection and malicious intent checks)
 * - Translate Hebrew to English if needed
 * - Forward to downstream components
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayService {
    
    private final CorrelationIdService correlationIdService;
    private final RateLimiter rateLimiter;
    private final SessionService sessionService;
    private final LanguageDetector languageDetector;
    private final GuardService guardService;
    private final InboundTranslator inboundTranslator;
    private final OrchestratorService orchestratorService;
    private final OutboundTranslationService outboundTranslationService;
    
    /**
     * Processes a chat request through the gateway.
     * 
     * @param request Chat request containing messageText
     * @param customerIdHeader Customer ID from HTTP header (trusted)
     * @return Chat response with answer
     * @throws MissingCustomerIdException if customer ID header is missing
     * @throws RateLimitExceededException if rate limit is exceeded
     */
    public ChatResponse processChatRequest(ChatRequest request, String customerIdHeader) {
        String customerId = extractAndValidateCustomerId(customerIdHeader);
        
        String correlationId = correlationIdService.generateCorrelationId();
        
        validateRateLimit(customerId, correlationId);
        
        // Fetch or create session context
        ChatSessionContext session = sessionService.getOrCreateSession(customerId);
        
        // Log session context if it exists
        logSessionContext(session, correlationId);
        
        LanguageDetectionResult languageResult = establishLanguage(session, request.getMessageText(), correlationId);
        
        validateMessageSecurity(request.getMessageText(), correlationId, languageResult.isHebrew());
        
        String messageTextForProcessing = translateToEnglishIfHebrewDetected(request.getMessageText(), languageResult, session, correlationId);

        // Create request context with session information
        RequestContext context = createRequestContext(
                customerId, 
                correlationId, 
                session,
                request.getMessageText(),
                messageTextForProcessing
        );
        
        log.info("Chat request received - correlationId: {}, customerId: {}, sessionId: {}, hasContext: {}", 
                correlationId, CustomerIdMasker.mask(customerId), session.getSessionId(),
                hasActiveContext(session));
        
        // Forward to Orchestrator for processing
        ChatResponse response = orchestratorService.orchestrate(context);
        
        // Translate response back to Hebrew if original message was Hebrew
        ChatResponse chatResponse = outboundTranslationService.translateResponseToHebrew(response, session, correlationId);
        chatResponse.setLanguage(context.getSessionContext().getLanguageCode());
        return chatResponse;
    }
    
    /**
     * Logout - removes session context from memory for the customer.
     * POC: Simple logout that removes session based on X-Customer-ID header.
     * 
     * @param customerIdHeader Customer ID from HTTP header (trusted)
     */
    public void logout(String customerIdHeader) {
        String customerId = extractAndValidateCustomerId(customerIdHeader);
        String correlationId = UUID.randomUUID().toString();
        
        log.info("Logout request - correlationId: {}, customerId: {}", 
                correlationId, CustomerIdMasker.mask(customerId));
        
        sessionService.invalidateSession(customerId);
        
        log.info("Session invalidated - correlationId: {}, customerId: {}", 
                correlationId, CustomerIdMasker.mask(customerId));
    }

    private String translateToEnglishIfHebrewDetected(String messageText, LanguageDetectionResult languageResult, ChatSessionContext session, String correlationId) {
        TranslationResult translationResult = null;
        String messageTextForProcessing = messageText;
        if (languageResult.requiresTranslation()) {
            translationResult = inboundTranslator.translateToEnglish(messageText, correlationId);
            if (translationResult.isSuccess()) {
                messageTextForProcessing = translationResult.getTranslatedText();
                log.info("Message translated - correlationId: {}, sessionId: {}, original length: {}, translated length: {}",
                        correlationId, session.getSessionId(),
                        messageText.length(),
                        messageTextForProcessing.length());
            } else {
                log.warn("Translation failed - correlationId: {}, sessionId: {}, error: {}",
                        correlationId, session.getSessionId(), translationResult.getErrorMessage());
                // Continue with original text if translation fails
            }
        }
        return messageTextForProcessing;
    }

    /**
     * Extracts and validates customer ID from header.
     * 
     * @param customerIdHeader Customer ID from HTTP header
     * @return Validated and trimmed customer ID
     * @throws MissingCustomerIdException if customer ID is missing or blank
     */
    private String extractAndValidateCustomerId(String customerIdHeader) {
        if (customerIdHeader == null || customerIdHeader.isBlank()) {
            log.error("Missing customerId header");
            throw new MissingCustomerIdException("Customer ID header is required");
        }
        return customerIdHeader.trim();
    }
    
    /**
     * Validates rate limit for the customer.
     * 
     * @param customerId Customer ID
     * @param correlationId Correlation ID for logging
     * @throws RateLimitExceededException if rate limit is exceeded
     */
    private void validateRateLimit(String customerId, String correlationId) {
        if (!rateLimiter.isAllowed(customerId)) {
            log.warn("Rate limit exceeded for customerId: {} (correlationId: {})", 
                    CustomerIdMasker.mask(customerId), correlationId);
            throw new RateLimitExceededException("Rate limit exceeded. Please try again later.");
        }
    }
    
    /**
     * Validates message security for prompt injections and malicious intent.
     * This check happens BEFORE translation to prevent malicious content from being processed.
     * 
     * @param messageText Message text to validate
     * @param correlationId Correlation ID for logging
     * @param isHebrew Whether the message is in Hebrew (for language-specific error messages)
     * @throws MaliciousContentException if malicious content is detected
     */
    private void validateMessageSecurity(String messageText, String correlationId, boolean isHebrew) {
        GuardResult guardResult = guardService.validateMessage(messageText, correlationId);
        
        if (!guardResult.isSafe()) {
            String detectedIssues = buildDetectedIssuesString(guardResult);
            log.warn("Security check failed - correlationId: {}, riskScore: {}, detectedIssues: {}, reason: {}", 
                    correlationId, guardResult.getRiskScore(), detectedIssues, guardResult.getRejectionReason());
            throw new MaliciousContentException(
                    guardResult.getRejectionReason() != null 
                            ? guardResult.getRejectionReason() 
                            : "Message contains potentially malicious content and cannot be processed.",
                    isHebrew);
        }
        
        log.debug("Message passed security guard - correlationId: {}, riskScore: {}", 
                correlationId, guardResult.getRiskScore());
    }
    
    /**
     * Builds a string describing detected security issues for logging.
     * 
     * @param guardResult Guard result containing detection flags
     * @return Comma-separated string of detected issues
     */
    private String buildDetectedIssuesString(GuardResult guardResult) {
        StringBuilder issues = new StringBuilder();
        if (guardResult.isPromptInjectionDetected()) {
            issues.append("promptInjection");
        }
        if (guardResult.isMaliciousIntentDetected()) {
            if (issues.length() > 0) issues.append(", ");
            issues.append("maliciousIntent");
        }
        if (guardResult.isUnpermittedActionDetected()) {
            if (issues.length() > 0) issues.append(", ");
            issues.append("unpermittedAction");
        }
        return issues.length() > 0 ? issues.toString() : "unknown";
    }
    
    /**
     * Detects language for every message and stores it in session only the first time.
     * Always performs language detection to handle language switching, but maintains
     * session state for audit/tracking purposes.
     * 
     * @param session Chat session context
     * @param messageText Message text to detect language from
     * @param correlationId Correlation ID for logging
     * @return Language detection result
     */
    private LanguageDetectionResult establishLanguage(ChatSessionContext session, String messageText, String correlationId) {
        // Always detect language on every message (to handle language switching)
        LanguageDetectionResult languageResult = languageDetector.detectLanguage(messageText);
        
        log.info("Language detected - correlationId: {}, sessionId: {}, language: {}, confidence: {}, requiresTranslation: {}, " +
                "sessionLanguage: {}", 
                correlationId, session.getSessionId(), languageResult.getLanguageCode(), 
                String.format("%.2f", languageResult.getConfidence()),
                languageResult.requiresTranslation(),
                session.getLanguageCode());
        
        // Store language in session only the first time (for audit/tracking)
        if (!session.isLanguageEstablished()) {
            session.setLanguageCode(languageResult.getLanguageCode());
            session.setLanguageConfidence(languageResult.getConfidence());
            sessionService.updateSession(session);
            log.debug("Stored language in session for first time - sessionId: {}, language: {}", 
                    session.getSessionId(), languageResult.getLanguageCode());
        }
        
        return languageResult;
    }
    
    /**
     * Gets the language from session context if available.
     * 
     * @param session Chat session context
     * @return Language code ("he" or "en") or null if not established
     */
    private String getLanguageFromSession(ChatSessionContext session) {
        return session.isLanguageEstablished() ? session.getLanguageCode() : null;
    }
    
    /**
     * Creates request context with customer ID, correlation ID, session context, message texts, and timestamp.
     * 
     * @param customerId Customer ID
     * @param correlationId Correlation ID
     * @param session Chat session context
     * @param originalMessageText Original message text from user
     * @param translatedMessageText Translated message text (English) if original was Hebrew, otherwise same as original
     * @return Request context
     */
    private RequestContext createRequestContext(
            String customerId, 
            String correlationId, 
            ChatSessionContext session,
            String originalMessageText,
            String translatedMessageText) {
        return RequestContext.builder()
                .customerId(customerId)
                .correlationId(correlationId)
                .sessionId(session.getSessionId())
                .sessionContext(session) // Include full session context
                .originalMessageText(originalMessageText)
                .translatedMessageText(translatedMessageText)
                .receivedAt(Instant.now())
                .build();
    }
    
    /**
     * Logs session context information for debugging and audit purposes.
     * 
     * @param session Chat session context
     * @param correlationId Correlation ID for logging
     */
    private void logSessionContext(ChatSessionContext session, String correlationId) {
        if (hasActiveContext(session)) {
            log.debug("Session context - correlationId: {}, sessionId: {}, language: {}, timezone: {}, " +
                    "hasLastIntent: {}, hasTimeRange: {}, hasSelectedEntities: {}, hasClarification: {}, hasDefaults: {}",
                    correlationId,
                    session.getSessionId(),
                    session.getLanguageCode(),
                    session.getTimezone(),
                    session.getLastResolvedIntent() != null,
                    session.getLastResolvedTimeRange() != null,
                    session.getLastSelectedEntities() != null,
                    session.getClarificationState() != null,
                    session.getDefaults() != null);
        }
    }
    
    /**
     * Checks if session has active context (beyond just language detection).
     * 
     * @param session Chat session context
     * @return true if session has any context beyond basic language detection
     */
    private boolean hasActiveContext(ChatSessionContext session) {
        return session.getLastResolvedIntent() != null ||
               session.getLastResolvedTimeRange() != null ||
               session.getLastSelectedEntities() != null ||
               session.getClarificationState() != null ||
               session.getDefaults() != null ||
               session.getTimezone() != null;
    }
    
    /**
     * Builds a placeholder response indicating language was detected and translated if needed.
     * This is temporary until the full pipeline is implemented.
     *
     * @param translationResult        Translation result if Hebrew was translated, null otherwise
     * @param correlationId            Correlation ID
     * @param languageResult           Language detection result
     * @param sessionId                Session ID
     * @param messageTextForProcessing
     * @return Placeholder chat response
     */
    private ChatResponse buildPlaceholderResponse(
            String correlationId,
            LanguageDetectionResult languageResult,
            String sessionId,
            Boolean isTranslated, String messageTextForProcessing) {
        
        String answer;
        String explanation;
        
        if (languageResult.requiresTranslation() && isTranslated) {
            answer = String.format("Gateway received your message in Hebrew. Translation complete (%s). Processing pipeline coming soon.", messageTextForProcessing);
            explanation = String.format("Detected language: %s (confidence: %.2f). Translated to English. SessionId: %s. Next step: Security Guard",
                    languageResult.getLanguageCode(), 
                    languageResult.getConfidence(),
                    sessionId);
        } else {
            answer = String.format("Gateway received your message in %s. Language detection complete. Processing pipeline coming soon.", 
                    languageResult.isHebrew() ? "Hebrew" : "English");
            explanation = String.format("Detected language: %s (confidence: %.2f). SessionId: %s. Next step: %s", 
                    languageResult.getLanguageCode(), 
                    languageResult.getConfidence(),
                    sessionId,
                    languageResult.requiresTranslation() ? "Inbound Translator" : "Security Guard");
        }
        
        return ChatResponse.builder()
                .answer(answer)
                .correlationId(correlationId)
                .explanation(explanation)
                .build();
    }
}
