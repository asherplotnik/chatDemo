package com.demoBank.chatDemo.orchestrator.prompt;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * System prompts for intent extraction.
 * 
 * Contains prompts for extracting structured intent from user messages,
 * including handling clarification answers with default fallbacks.
 */
public class IntentExtractionPrompt {
    
    private IntentExtractionPrompt() {}
    
    /**
     * Default values for clarification contexts.
     * Used when user's answer to a clarifying question is unsatisfactory.
     */
    public static class ClarificationDefaults {
        
        /**
         * Default time range: start of current month to today.
         * Example: If today is 2025-12-13, returns "2025-12-01 to 2025-12-13".
         */
        public static String getDefaultTimeRange() {
            LocalDate today = LocalDate.now();
            LocalDate monthStart = today.withDayOfMonth(1);
            return String.format("%s to %s", 
                monthStart.format(DateTimeFormatter.ISO_LOCAL_DATE),
                today.format(DateTimeFormatter.ISO_LOCAL_DATE));
        }
        
        /**
         * Default account selection: main account.
         * The system should select the account with nickname containing "Main" or "Primary",
         * or the first account if multiple accounts exist.
         */
        public static String getDefaultAccountSelection() {
            return "main account (account with 'Main' or 'Primary' in nickname, or first account if multiple exist)";
        }
        
        /**
         * Default metric: depends on domain, but typically "list" or "balance".
         */
        public static String getDefaultMetric(String domain) {
            return switch (domain != null ? domain.toLowerCase() : "") {
                case "foreign-current-accounts", "foreigncurrentaccounts" -> "list";
                case "current-accounts", "currentaccounts" -> "balance";
                case "credit-cards", "creditcards" -> "list";
                case "loans" -> "balance";
                case "mortgages" -> "balance";
                case "deposits" -> "balance";
                case "securities" -> "list";
                default -> "list";
            };
        }
        
        /**
         * Default domain: if unclear, use "current-accounts".
         */
        public static String getDefaultDomain() {
            return "current-accounts";
        }
    }
    
    /**
     * Gets the base system prompt for intent extraction.
     * 
     * @return Base system prompt
     */
    public static String getBaseSystemPrompt() {
        return """
            You are an intent extraction system for a banking chat assistant.
            Your task is to extract structured intent from customer messages.
            
            Extract the following information:
            - Domain: current accounts, foreign current accounts, credit cards, loans, mortgages, deposits, securities, or UNKNOWN
            - Metric: balance, count, sum, max, min, average, list
            - Time range hints: relative expressions like "last week", "yesterday", "this month"
            - Entity hints: account references, card references, etc.
            
            IMPORTANT - Entity Hints Extraction Rules:
            - ONLY extract account IDs, card IDs, or other entity references if the user EXPLICITLY mentions them in the current message
            - Do NOT infer or invent entity IDs that are not mentioned by the user
            - Do NOT use account IDs from previous conversations unless explicitly mentioned in the current message
            - If no entity references are mentioned, set entityHints to null
            - Examples:
              * User says "show balance for account ****1234" -> extract accountIds: ["****1234"]
              * User says "show my balance" -> set entityHints to null (no account mentioned)
              * User says "show transactions" -> set entityHints to null (no account mentioned)
            
            IMPORTANT - UNKNOWN Domain Classification:
            Use domain "UNKNOWN" for messages that are conversational and do not require banking data access:
            - Greetings: "hi", "hello", "good morning", "hey"
            - Thanks: "thanks", "thank you", "appreciate it", "thank you very much"
            - Farewells: "bye", "goodbye", "see you", "have a good day"
            - Introductions: "I'm [name]", "my name is [name]", "hi im [name]"
            - General questions: "who are you", "what can you do", "what are your capabilities"
            - Small talk: "how are you", "what's up", "how's it going"
            - Other conversational messages that don't request banking information
            
            When domain is UNKNOWN:
            - Set metric to "list" (it won't be used, but required by schema)
            - Set timeRangeHint to null
            - Set entityHints to null
            - Set parameters to null or empty
            
            Note: Domain values should be returned in kebab-case format (e.g., "current-accounts", "foreign-current-accounts", "credit-cards"), except "UNKNOWN" which should be uppercase.
            
            Return structured intent data using the extract_intent function.
            """;
    }
    
    /**
     * Gets system prompt with clarification context and default fallbacks.
     * Use this when processing a clarification answer.
     * 
     * @param clarificationContext Context of what was clarified (e.g., "time_range", "account_selection")
     * @param userAnswer User's answer to the clarifying question
     * @param expectedAnswerType Expected type of answer (e.g., "time_range", "account", "yes_no")
     * @param originalQuestion The clarifying question that was asked
     * @return System prompt with clarification caveat
     */
    public static String getSystemPromptWithClarification(
            String clarificationContext, 
            String userAnswer, 
            String expectedAnswerType,
            String originalQuestion) {
        
        String defaultFallback = getDefaultForContext(clarificationContext);
        
        return getBaseSystemPrompt() + """
            
            
            CLARIFICATION CONTEXT:
            The user was asked: "%s"
            The user answered: "%s"
            Expected answer type: %s
            Clarification context: %s
            
            IMPORTANT - DEFAULT FALLBACK RULE:
            If the user's answer is unsatisfactory, unclear, or doesn't match the expected type,
            you MUST use the following default instead:
            
            %s
            
            Examples of unsatisfactory answers that should trigger defaults:
            - "I don't know", "I'm not sure", "maybe", "whatever"
            - Answers that don't match the expected type
            - Empty or very vague answers
            
            When you detect an unsatisfactory answer:
            1. Use the default value specified above
            2. Extract intent normally with the default applied
            3. Do not ask for clarification again
            
            If the answer IS satisfactory, extract intent normally using the user's answer.
            """.formatted(
                originalQuestion != null ? originalQuestion : "Unknown question",
                userAnswer != null ? userAnswer : "",
                expectedAnswerType != null ? expectedAnswerType : "unknown",
                clarificationContext != null ? clarificationContext : "unknown",
                defaultFallback
            );
    }
    
    /**
     * Gets the default value description for a given clarification context.
     * 
     * @param clarificationContext Context type (e.g., "time_range", "account_selection")
     * @return Default value description
     */
    private static String getDefaultForContext(String clarificationContext) {
        if (clarificationContext == null) {
            return "No default specified";
        }
        
        return switch (clarificationContext.toLowerCase()) {
            case "time_range" -> String.format(
                "Time range default: %s (start of current month to today)", 
                ClarificationDefaults.getDefaultTimeRange()
            );
            case "account_selection" -> String.format(
                "Account selection default: %s", 
                ClarificationDefaults.getDefaultAccountSelection()
            );
            case "metric" -> "Metric default: 'list' (or 'balance' for account-related domains)";
            case "domain" -> String.format(
                "Domain default: %s", 
                ClarificationDefaults.getDefaultDomain()
            );
            default -> String.format(
                "Default for %s: Use sensible default based on context", 
                clarificationContext
            );
        };
    }
}
