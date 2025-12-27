package com.demoBank.chatDemo.orchestrator.service;

import com.demoBank.chatDemo.gateway.model.ChatSessionContext;
import com.demoBank.chatDemo.gateway.model.RequestContext;
import com.demoBank.chatDemo.orchestrator.dto.DraftResponseDTO;
import com.demoBank.chatDemo.orchestrator.model.DraftResponseFunctionDefinition;
import com.demoBank.chatDemo.orchestrator.model.NormalizedData;
import com.demoBank.chatDemo.orchestrator.model.OrchestrationState;
import com.demoBank.chatDemo.orchestrator.prompt.DraftResponsePrompt;
import com.demoBank.chatDemo.orchestrator.util.TimeRangeResolver;
import com.demoBank.chatDemo.translation.dto.GroqApiRequest;
import com.demoBank.chatDemo.translation.dto.GroqApiResponse;
import com.demoBank.chatDemo.translation.service.GroqApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for drafting structured customer-facing responses from normalized banking data.
 * 
 * Handles:
 * - Generating structured responses with introduction, table, and data source
 * - Using function calling to ensure consistent JSON structure
 * - Including conversation history for context
 * - Formatting normalized data for LLM consumption
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DraftResponseService {
    
    private final GroqApiClient groqApiClient;
    private final ObjectMapper objectMapper;
    
    @Value("${groq.api.model:llama-3.3-70b-versatile}")
    private String draftResponseModel;
    
    /**
     * Step 7: DRAFT - Generate structured answer text, table, and explanation.
     * Uses function calling to ensure consistent JSON structure.
     * 
     * @param state Current orchestration state with normalized data
     * @param requestContext Request context
     * @return Updated orchestration state with drafted response
     */
    public OrchestrationState draftResponse(OrchestrationState state, RequestContext requestContext) {
        String correlationId = requestContext.getCorrelationId();
        log.debug("Step DRAFT - correlationId: {}", correlationId);
        
        List<NormalizedData> normalizedData = state.getNormalizedData();
        if (normalizedData == null || normalizedData.isEmpty()) {
            log.warn("No normalized data to draft response from - correlationId: {}", correlationId);
            return state;
        }
        
        try {
            // Format normalized data for LLM
            String formattedData = formatNormalizedDataForLLM(normalizedData, state, requestContext);
            
            // Build system prompt with context
            String systemPrompt = buildSystemPrompt(state, requestContext);
            
            // Build user message with data
            String userMessage = buildUserMessage(formattedData, state, requestContext);
            
            // Build conversation history messages
            List<GroqApiRequest.Message> conversationHistory = buildConversationHistory(state, requestContext);
            
            // Create function definition
            GroqApiRequest.Tool draftTool = GroqApiRequest.Tool.builder()
                    .type("function")
                    .function(GroqApiRequest.Function.builder()
                            .name(DraftResponseFunctionDefinition.FUNCTION_NAME)
                            .description(DraftResponseFunctionDefinition.FUNCTION_DESCRIPTION)
                            .parameters(DraftResponseFunctionDefinition.getFunctionSchema())
                            .build())
                    .build();
            
            log.info("Calling Groq API for response drafting - correlationId: {}, dataCount: {}, conversationHistorySize: {}", 
                    correlationId, normalizedData.size(), conversationHistory.size());
            
            // Call Groq API with function calling and conversation history
            GroqApiResponse groqResponse = callGroqApiWithHistory(
                    systemPrompt,
                    userMessage,
                    conversationHistory,
                    List.of(draftTool),
                    "required",
                    draftResponseModel
            );
            
            // Check if response contains tool calls
            if (!groqResponse.hasToolCalls()) {
                log.warn("Groq API did not return tool calls for response drafting - correlationId: {}", correlationId);
                throw new IllegalStateException("Groq API did not return expected function call for response drafting");
            }
            
            // Parse function call response
            DraftResponseDTO draftResponse = parseDraftResponse(groqResponse, correlationId);
            
            // Log parsed response details
            int tableCount = draftResponse.getTables() != null ? draftResponse.getTables().size() : 0;
            log.info("Parsed draft response - correlationId: {}, tableCount: {}", 
                    correlationId, tableCount);
            
            if (draftResponse.getTables() != null) {
                for (int i = 0; i < draftResponse.getTables().size(); i++) {
                    DraftResponseDTO.TableData table = draftResponse.getTables().get(i);
                    log.info("Table {} - correlationId: {}, accountName: {}, type: {}, rowCount: {}", 
                            i + 1, correlationId, table.getAccountName(), table.getType(),
                            table.getRows() != null ? table.getRows().size() : 0);
                }
            }
            
            // Validate and ensure tables exist if there's data
            if ((draftResponse.getTables() == null || draftResponse.getTables().isEmpty()) 
                    && hasDataToDisplay(normalizedData)) {
                log.warn("WARNING: LLM did not include tables but data exists - correlationId: {}, creating fallback tables", 
                        correlationId);
                draftResponse = ensureTablesExist(draftResponse, normalizedData, state, correlationId);
            }
            
            if (draftResponse.getTables() == null || draftResponse.getTables().isEmpty()) {
                log.warn("WARNING: Draft response has no tables - correlationId: {}, introduction: {}", 
                        correlationId, 
                        draftResponse.getIntroduction() != null && draftResponse.getIntroduction().length() > 50 
                                ? draftResponse.getIntroduction().substring(0, 50) + "..." 
                                : draftResponse.getIntroduction());
            }
            
            // Convert to ChatResponse
            com.demoBank.chatDemo.gateway.dto.ChatResponse chatResponse = convertToChatResponse(
                    draftResponse, correlationId);
            
            state.setResponse(chatResponse);
            
            log.info("Response drafting completed - correlationId: {}, tableCount: {}, tokens: {}", 
                    correlationId,
                    chatResponse.getTables() != null ? chatResponse.getTables().size() : 0,
                    groqResponse.getUsage() != null ? groqResponse.getUsage().getTotalTokens() : "unknown");
            
        } catch (Exception e) {
            log.error("Error drafting response with Groq API - correlationId: {}, error: {}", 
                    correlationId, e.getMessage(), e);
            // Create fallback response
            state.setResponse(createFallbackResponse(correlationId, e));
        }
        
        return state;
    }
    
    /**
     * Calls Groq API with conversation history support.
     * 
     * @param systemPrompt System prompt
     * @param userMessage Current user message
     * @param conversationHistory Previous conversation messages
     * @param tools Function tools
     * @param toolChoice Tool choice strategy
     * @param model Model to use
     * @return GroqApiResponse
     */
    private GroqApiResponse callGroqApiWithHistory(
            String systemPrompt,
            String userMessage,
            List<GroqApiRequest.Message> conversationHistory,
            List<GroqApiRequest.Tool> tools,
            String toolChoice,
            String model) {
        
        // Build messages list: system prompt + conversation history + current user message
        List<GroqApiRequest.Message> messages = new ArrayList<>();
        
        // Add system prompt
        messages.add(GroqApiRequest.Message.builder()
                .role("system")
                .content(systemPrompt)
                .build());
        
        // Add conversation history
        messages.addAll(conversationHistory);
        
        // Add current user message
        messages.add(GroqApiRequest.Message.builder()
                .role("user")
                .content(userMessage)
                .build());
        
        // Build request manually to include conversation history
        GroqApiRequest request = GroqApiRequest.builder()
                .messages(messages)
                .model(model)
                .temperature(0.3)
                .maxCompletionTokens(2048) // Increased for structured responses
                .topP(1.0)
                .stream(false)
                .stop(null)
                .tools(tools)
                .toolChoice(toolChoice)
                .build();
        
        // Call API directly (we'll need to add a method to GroqApiClient or call it here)
        // For now, let's add a method to GroqApiClient
        return groqApiClient.callGroqApiWithToolsAndHistory(request);
    }
    
    /**
     * Builds conversation history from session context.
     * 
     * @param state Orchestration state
     * @param requestContext Request context
     * @return List of conversation messages
     */
    private List<GroqApiRequest.Message> buildConversationHistory(
            OrchestrationState state, RequestContext requestContext) {
        
        List<GroqApiRequest.Message> history = new ArrayList<>();
        
        ChatSessionContext sessionContext = state.getSessionContext();
        if (sessionContext == null || sessionContext.getConversationSummaries() == null) {
            return history;
        }
        
        List<ChatSessionContext.ConversationSummary> summaries = 
                sessionContext.getConversationSummaries();
        
        // Limit to last 5 conversations to avoid token limits
        int maxHistory = Math.min(5, summaries.size());
        List<ChatSessionContext.ConversationSummary> recentSummaries = 
                summaries.subList(Math.max(0, summaries.size() - maxHistory), summaries.size());
        
        for (ChatSessionContext.ConversationSummary summary : recentSummaries) {
            // Add user message
            history.add(GroqApiRequest.Message.builder()
                    .role("user")
                    .content(summary.getUserMessage())
                    .build());
            
            // Add assistant response summary
            history.add(GroqApiRequest.Message.builder()
                    .role("assistant")
                    .content("Previous response: " + summary.getResponseSummary())
                    .build());
        }
        
        log.debug("Built conversation history - correlationId: {}, messageCount: {}", 
                requestContext.getCorrelationId(), history.size());
        
        return history;
    }
    
    /**
     * Formats normalized data for LLM consumption.
     * 
     * @param normalizedData List of normalized data
     * @param state Orchestration state
     * @param requestContext Request context
     * @return Formatted data string
     */
    private String formatNormalizedDataForLLM(
            List<NormalizedData> normalizedData, 
            OrchestrationState state, 
            RequestContext requestContext) {
        
        try {
            // Convert to JSON for LLM
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(normalizedData);
        } catch (Exception e) {
            log.error("Error formatting normalized data - correlationId: {}", 
                    requestContext.getCorrelationId(), e);
            return "Error formatting data: " + e.getMessage();
        }
    }
    
    /**
     * Builds system prompt with context.
     * 
     * @param state Orchestration state
     * @param requestContext Request context
     * @return System prompt string
     */
    private String buildSystemPrompt(OrchestrationState state, RequestContext requestContext) {
        // Get base prompt
        String basePrompt = DraftResponsePrompt.getBaseSystemPrompt();
        
        // Add request context if available
        if (state.getExtractedIntent() != null && !state.getExtractedIntent().isEmpty()) {
            var intent = state.getExtractedIntent().get(0);
            String domain = intent.getDomain();
            String metric = intent.getMetric();
            String timeRange = formatTimeRange(state.getResolvedTimeRange());
            
            basePrompt += "\n\nREQUEST CONTEXT:\n";
            basePrompt += "Domain: " + (domain != null ? domain : "unknown") + "\n";
            basePrompt += "Metric: " + (metric != null ? metric : "unknown") + "\n";
            if (timeRange != null && !timeRange.isBlank()) {
                basePrompt += "Time Range: " + timeRange + "\n";
            }
        }
        
        return basePrompt;
    }
    
    /**
     * Builds user message with formatted data.
     * 
     * @param formattedData Formatted normalized data
     * @param state Orchestration state
     * @param requestContext Request context
     * @return User message string
     */
    private String buildUserMessage(
            String formattedData, 
            OrchestrationState state, 
            RequestContext requestContext) {
        
        StringBuilder message = new StringBuilder();
        message.append("Please draft a structured response using the draft_structured_response function.\n\n");
        message.append("NORMALIZED BANKING DATA:\n");
        message.append(formattedData);
        message.append("\n\n");
        
        // Add specific guidance based on metric
        if (state.getExtractedIntent() != null && !state.getExtractedIntent().isEmpty()) {
            var intent = state.getExtractedIntent().get(0);
            String metric = intent.getMetric();
            
            message.append("METRIC: ").append(metric != null ? metric : "unknown").append("\n\n");
            
            if ("list".equals(metric) || "transactions".equals(intent.getDomain())) {
                message.append("INSTRUCTIONS:\n");
                message.append("- Extract all transactions/items from the normalized data\n");
                message.append("- Create a table with type 'transactions' or 'list'\n");
                message.append("- Include columns: Date, Amount, Description, Merchant (or appropriate columns)\n");
                message.append("- Format amounts with currency symbols\n");
                message.append("- Sort by date (most recent first) if applicable\n");
            } else if ("balance".equals(metric)) {
                message.append("INSTRUCTIONS:\n");
                message.append("- Extract balance information from all entities\n");
                message.append("- Create a table with type 'balance'\n");
                message.append("- Include columns: Account, Balance, Currency\n");
                message.append("- Use account nicknames when available\n");
            } else if ("sum".equals(metric) || "count".equals(metric) || "average".equals(metric)) {
                message.append("INSTRUCTIONS:\n");
                message.append("- Calculate the requested metric from the data\n");
                message.append("- Show the result in the introduction\n");
                message.append("- Create a summary table with type 'summary'\n");
                message.append("- Include totals in table metadata if applicable\n");
            }
        }
        
        message.append("\n");
        message.append("CRITICAL REQUIREMENTS:\n");
        message.append("1. You MUST call the draft_structured_response function\n");
        message.append("2. You MUST include a tables field (LIST) with structured data if ANY data exists\n");
        message.append("3. Create SEPARATE tables for EACH account/entity - do NOT mix accounts in one table\n");
        message.append("4. Each table MUST have accountName field set to the account nickname/name\n");
        message.append("5. If normalized data contains transactions from multiple accounts: create ONE table per account with that account's transactions\n");
        message.append("6. If normalized data contains balances from multiple accounts: create ONE table per account with that account's balance\n");
        message.append("7. If user asks for both transactions AND balance: create separate tables per account\n");
        message.append("8. NEVER set tables to null or empty if there is data in the normalized data\n");
        message.append("9. The frontend CANNOT display data without the tables field - it is ESSENTIAL\n");
        message.append("\n");
        message.append("Table structure (one per account):\n");
        message.append("- For transactions: accountName='Account Name (Balance)', type='transactions', headers=['Date', 'Amount', 'Description', 'Merchant']\n");
        message.append("- For balances: accountName='Account Name (Balance)', type='balance', headers=['Balance', 'Currency', 'Available']\n");
        message.append("- CRITICAL: Always include balance in accountName if balance data exists: 'Account Name (₪1,234.56)'\n");
        message.append("- Format balance with currency: ₪ for ILS, $ for USD, € for EUR\n");
        message.append("- Each row must be a complete object with all header keys\n");
        message.append("- Include metadata with rowCount\n");
        message.append("- Group transactions/balances by account - each account gets its own table\n");
        
        return message.toString();
    }
    
    /**
     * Parses function call response from Groq API.
     * 
     * @param groqResponse Groq API response containing tool calls
     * @param correlationId Correlation ID for logging
     * @return Parsed DraftResponseDTO
     */
    private DraftResponseDTO parseDraftResponse(GroqApiResponse groqResponse, String correlationId) {
        try {
            List<GroqApiResponse.ToolCall> toolCalls = groqResponse.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                throw new IllegalStateException("No tool calls found in response");
            }
            
            // Find the draft response function call
            GroqApiResponse.ToolCall draftToolCall = toolCalls.stream()
                    .filter(tc -> DraftResponseFunctionDefinition.FUNCTION_NAME.equals(tc.getFunction().getName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Draft response function call not found in response"));
            
            String argumentsJson = draftToolCall.getFunction().getArguments();
            if (argumentsJson == null || argumentsJson.isBlank()) {
                throw new IllegalStateException("Function arguments are empty");
            }
            
            // Parse the function arguments JSON
            return objectMapper.readValue(argumentsJson, DraftResponseDTO.class);
            
        } catch (Exception e) {
            log.error("Error parsing draft response - correlationId: {}, error: {}", 
                    correlationId, e.getMessage(), e);
            throw new IllegalStateException("Failed to parse draft response: " + e.getMessage(), e);
        }
    }
    
    /**
     * Converts DraftResponseDTO to ChatResponse.
     * 
     * @param draftResponse Draft response DTO
     * @param correlationId Correlation ID
     * @return ChatResponse
     */
    private com.demoBank.chatDemo.gateway.dto.ChatResponse convertToChatResponse(
            DraftResponseDTO draftResponse, String correlationId) {
        
        // Build answer: introduction + table (if exists) + data source
        StringBuilder answer = new StringBuilder();
        answer.append(draftResponse.getIntroduction());
        
        // Add data source as explanation
        String explanation = draftResponse.getDataSource() != null 
                ? draftResponse.getDataSource().getDescription()
                : "Response generated from normalized banking data";
        
        return com.demoBank.chatDemo.gateway.dto.ChatResponse.builder()
                .answer(answer.toString())
                .explanation(explanation)
                .correlationId(correlationId)
                .tables(draftResponse.getTables()) // Set tables field
                .build();
    }
    
    /**
     * Checks if there is data that should be displayed in a table.
     * 
     * @param normalizedData Normalized data
     * @return true if there's data to display
     */
    private boolean hasDataToDisplay(List<NormalizedData> normalizedData) {
        if (normalizedData == null || normalizedData.isEmpty()) {
            return false;
        }
        
        for (NormalizedData data : normalizedData) {
            if (data.getEntities() != null) {
                for (com.demoBank.chatDemo.orchestrator.model.NormalizedEntity entity : data.getEntities()) {
                    // Check if there are transactions
                    if (entity.getTransactions() != null && !entity.getTransactions().isEmpty()) {
                        return true;
                    }
                    // Check if there's balance information
                    if (entity.getBalance() != null) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Ensures tables exist in the draft response if data is available.
     * Creates separate tables for each account/entity from normalized data.
     * 
     * @param draftResponse Draft response without tables
     * @param normalizedData Normalized data
     * @param state Orchestration state
     * @param correlationId Correlation ID
     * @return Draft response with tables added (one per account)
     */
    private DraftResponseDTO ensureTablesExist(
            DraftResponseDTO draftResponse,
            List<NormalizedData> normalizedData,
            OrchestrationState state,
            String correlationId) {
        
        try {
            List<DraftResponseDTO.TableData> tables = new ArrayList<>();
            
            // Group data by entity/account and create separate tables
            for (NormalizedData data : normalizedData) {
                if (data.getEntities() != null) {
                    for (com.demoBank.chatDemo.orchestrator.model.NormalizedEntity entity : data.getEntities()) {
                        String baseAccountName = entity.getNickname() != null 
                                ? entity.getNickname() 
                                : entity.getEntityId();
                        
                        // Format account name with balance if available
                        String accountNameWithBalance = formatAccountNameWithBalance(baseAccountName, entity.getBalance());
                        
                        // Create transactions table if entity has transactions
                        if (entity.getTransactions() != null && !entity.getTransactions().isEmpty()) {
                            List<String> headers = List.of("Date", "Amount", "Description", "Merchant");
                            List<Map<String, Object>> rows = new ArrayList<>();
                            
                            for (com.demoBank.chatDemo.orchestrator.model.NormalizedTransaction tx : entity.getTransactions()) {
                                Map<String, Object> row = new HashMap<>();
                                row.put("Date", tx.getDate() != null ? tx.getDate() : "");
                                String amountStr = "";
                                if (tx.getAmount() != null) {
                                    amountStr = tx.getCurrency() != null 
                                            ? tx.getCurrency() + " " + tx.getAmount().toString()
                                            : tx.getAmount().toString();
                                }
                                row.put("Amount", amountStr);
                                row.put("Description", tx.getDescription() != null ? tx.getDescription() : "");
                                String merchant = tx.getMerchant() != null && tx.getMerchant().getName() != null
                                        ? tx.getMerchant().getName()
                                        : "";
                                row.put("Merchant", merchant);
                                rows.add(row);
                            }
                            
                            DraftResponseDTO.TableData table = DraftResponseDTO.TableData.builder()
                                    .accountName(accountNameWithBalance)
                                    .type("transactions")
                                    .headers(headers)
                                    .rows(rows)
                                    .metadata(DraftResponseDTO.TableMetadata.builder()
                                            .rowCount(rows.size())
                                            .hasTotals(false)
                                            .build())
                                    .build();
                            
                            tables.add(table);
                        }
                        
                        // Create balance table if entity has balance
                        if (entity.getBalance() != null) {
                            List<String> headers = List.of("Balance", "Currency", "Available");
                            List<Map<String, Object>> rows = new ArrayList<>();
                            
                            Map<String, Object> row = new HashMap<>();
                            String balanceStr = "";
                            if (entity.getBalance().getAvailable() != null) {
                                balanceStr = entity.getBalance().getCurrency() != null
                                        ? entity.getBalance().getCurrency() + " " + entity.getBalance().getAvailable().toString()
                                        : entity.getBalance().getAvailable().toString();
                            } else if (entity.getBalance().getCurrent() != null) {
                                balanceStr = entity.getBalance().getCurrency() != null
                                        ? entity.getBalance().getCurrency() + " " + entity.getBalance().getCurrent().toString()
                                        : entity.getBalance().getCurrent().toString();
                            }
                            row.put("Balance", balanceStr);
                            row.put("Currency", entity.getBalance().getCurrency() != null 
                                    ? entity.getBalance().getCurrency() : "ILS");
                            row.put("Available", entity.getBalance().getAvailable() != null 
                                    ? entity.getBalance().getAvailable().toString() : "");
                            rows.add(row);
                            
                            DraftResponseDTO.TableData table = DraftResponseDTO.TableData.builder()
                                    .accountName(accountNameWithBalance)
                                    .type("balance")
                                    .headers(headers)
                                    .rows(rows)
                                    .metadata(DraftResponseDTO.TableMetadata.builder()
                                            .rowCount(1)
                                            .hasTotals(false)
                                            .build())
                                    .build();
                            
                            tables.add(table);
                        }
                    }
                }
            }
            
            if (tables.isEmpty()) {
                // No data to create tables from
                return draftResponse;
            }
            
            log.info("Created fallback tables - correlationId: {}, tableCount: {}", 
                    correlationId, tables.size());
            
            // Return new draft response with tables
            return DraftResponseDTO.builder()
                    .introduction(draftResponse.getIntroduction())
                    .tables(tables)
                    .dataSource(draftResponse.getDataSource())
                    .build();
            
        } catch (Exception e) {
            log.error("Error creating fallback tables - correlationId: {}", correlationId, e);
            return draftResponse; // Return original if fallback fails
        }
    }
    
    /**
     * Formats account name with balance information.
     * 
     * @param accountName Base account name/nickname
     * @param balance Balance information (can be null)
     * @return Account name with balance appended if available (e.g., "Main Account (₪1,234.56)")
     */
    private String formatAccountNameWithBalance(String accountName, com.demoBank.chatDemo.orchestrator.model.NormalizedBalance balance) {
        if (balance == null) {
            return accountName;
        }
        
        String balanceStr = null;
        String currency = balance.getCurrency() != null ? balance.getCurrency() : "ILS";
        
        // Prefer available balance, fallback to current balance
        if (balance.getAvailable() != null) {
            balanceStr = formatBalanceAmount(balance.getAvailable(), currency);
        } else if (balance.getCurrent() != null) {
            balanceStr = formatBalanceAmount(balance.getCurrent(), currency);
        }
        
        if (balanceStr != null && !balanceStr.isBlank()) {
            return accountName + " (" + balanceStr + ")";
        }
        
        return accountName;
    }
    
    /**
     * Formats a balance amount with currency symbol.
     * 
     * @param amount Balance amount
     * @param currency Currency code
     * @return Formatted balance string (e.g., "₪1,234.56" or "USD 1,234.56")
     */
    private String formatBalanceAmount(Double amount, String currency) {
        if (amount == null) {
            return null;
        }
        
        // Format with currency symbol
        if ("ILS".equals(currency) || "NIS".equals(currency)) {
            return "₪" + String.format("%.2f", amount);
        } else if ("USD".equals(currency)) {
            return "$" + String.format("%.2f", amount);
        } else if ("EUR".equals(currency)) {
            return "€" + String.format("%.2f", amount);
        } else {
            return currency + " " + String.format("%.2f", amount);
        }
    }
    
    /**
     * Formats time range object to string.
     * 
     * @param resolvedTimeRange Time range object (can be ChatSessionContext.TimeRange or TimeRangeResolver.ResolvedTimeRange)
     * @return Formatted time range string or null
     */
    private String formatTimeRange(Object resolvedTimeRange) {
        if (resolvedTimeRange == null) {
            return null;
        }
        
        if (resolvedTimeRange instanceof ChatSessionContext.TimeRange) {
            ChatSessionContext.TimeRange timeRange = (ChatSessionContext.TimeRange) resolvedTimeRange;
            return timeRange.getFromDate() + " to " + timeRange.getToDate();
        } else if (resolvedTimeRange instanceof TimeRangeResolver.ResolvedTimeRange) {
            TimeRangeResolver.ResolvedTimeRange timeRange = (TimeRangeResolver.ResolvedTimeRange) resolvedTimeRange;
            return timeRange.getFromDate() + " to " + timeRange.getToDate();
        }
        
        return null;
    }
    
    /**
     * Creates a fallback response when drafting fails.
     * 
     * @param correlationId Correlation ID
     * @param exception Exception that occurred
     * @return Fallback ChatResponse
     */
    private com.demoBank.chatDemo.gateway.dto.ChatResponse createFallbackResponse(
            String correlationId, Exception exception) {
        
        log.error("Creating fallback response - correlationId: {}", correlationId, exception);
        return com.demoBank.chatDemo.gateway.dto.ChatResponse.builder()
                .answer("I encountered an error formatting your response. Please try again.")
                .correlationId(correlationId)
                .explanation("Drafting error: " + exception.getMessage())
                .tables(null)
                .build();
    }
}
