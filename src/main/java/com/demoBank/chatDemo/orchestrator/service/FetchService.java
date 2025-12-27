package com.demoBank.chatDemo.orchestrator.service;

import com.demoBank.chatDemo.bankApi.*;
import com.demoBank.chatDemo.gateway.model.ChatSessionContext;
import com.demoBank.chatDemo.gateway.model.RequestContext;
import com.demoBank.chatDemo.orchestrator.dto.IntentExtractionResponse;
import com.demoBank.chatDemo.orchestrator.model.OrchestrationState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for fetching data from banking APIs.
 * 
 * Handles:
 * - Calling banking APIs based on extracted intents
 * - Handling default time ranges when not resolved
 * - Storing API responses with metadata
 * - Creating execution plan with metadata
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FetchService {
    
    private final CurrentAccountsService currentAccountsService;
    private final CreditCardsService creditCardsService;
    private final DepositsService depositsService;
    private final ForeignCurrentAccountsService foreignCurrentAccountsService;
    private final LoansService loansService;
    private final MortgageService mortgageService;
    private final SecuritiesService securitiesService;

    /**
     * Fetches data from banking APIs based on extracted intents.
     * 
     * @param state Current orchestration state
     * @param requestContext Request context
     * @return Updated orchestration state with fetched data
     */
    public OrchestrationState fetchData(OrchestrationState state, RequestContext requestContext) {
        String correlationId = requestContext.getCorrelationId();
        String customerId = state.getCustomerId();
        
        log.debug("Step FETCH - correlationId: {}, customerId: {}", correlationId, maskCustomerId(customerId));
        
        // Get intent list from state
        List<IntentExtractionResponse.IntentData> intents = state.getExtractedIntent();
        if (intents == null || intents.isEmpty()) {
            log.warn("No intents found in state - correlationId: {}", correlationId);
            return state;
        }
        
        // Get resolved time range
        ChatSessionContext.TimeRange timeRange = null;
        if (state.getResolvedTimeRange() instanceof ChatSessionContext.TimeRange) {
            timeRange = (ChatSessionContext.TimeRange) state.getResolvedTimeRange();
        }
        
        boolean usingDefaultTimeRange = false;
        if (timeRange == null || timeRange.getFromDate() == null || timeRange.getToDate() == null) {
            log.warn("Time range not resolved - correlationId: {}, using default (start of current month to today)", correlationId);
            
            // Use default time range: start of current month to today
            LocalDate today = LocalDate.now();
            LocalDate monthStart = today.withDayOfMonth(1);
            DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
            
            timeRange = ChatSessionContext.TimeRange.builder()
                    .fromDate(monthStart.format(formatter))
                    .toDate(today.format(formatter))
                    .build();
            
            usingDefaultTimeRange = true;
            
            // Log that default dates were used (for explanation generation later)
            log.info("Using default time range - correlationId: {}, fromDate: {}, toDate: {}, reason: time range not resolved from user input", 
                    correlationId, timeRange.getFromDate(), timeRange.getToDate());
        }
        
        String fromDate = timeRange.getFromDate();
        String toDate = timeRange.getToDate();
        
        // Create execution plan with metadata
        Map<String, Object> executionPlan = new HashMap<>();
        executionPlan.put("fromDate", fromDate);
        executionPlan.put("toDate", toDate);
        executionPlan.put("usingDefaultTimeRange", usingDefaultTimeRange);
        if (usingDefaultTimeRange) {
            executionPlan.put("defaultTimeRangeReason", "Time range was not resolved from user input. Using default range: start of current month to today.");
        }
        executionPlan.put("intentCount", intents != null ? intents.size() : 0);
        state.setExecutionPlan(executionPlan);
        
        // Store API responses for each intent
        List<Map<String, Object>> apiResponses = new ArrayList<>();
        
        // Iterate over each intent and call the appropriate API
        for (IntentExtractionResponse.IntentData intent : intents) {
            if (intent == null || intent.getDomain() == null) {
                log.warn("Skipping null or invalid intent - correlationId: {}", correlationId);
                continue;
            }
            
            String domain = intent.getDomain();
            
            // Skip UNKNOWN domain
            if ("UNKNOWN".equals(domain)) {
                log.debug("Skipping UNKNOWN domain intent - correlationId: {}", correlationId);
                continue;
            }
            
            log.info("Calling API for intent - correlationId: {}, domain: {}, metric: {}", 
                    correlationId, domain, intent.getMetric());
            
            try {
                Map<String, Object> apiResponse = callApiForIntent(
                        intent, customerId, fromDate, toDate, correlationId);
                
                // Store response with intent metadata
                Map<String, Object> responseWithMetadata = new HashMap<>();
                responseWithMetadata.put("domain", domain);
                responseWithMetadata.put("metric", intent.getMetric());
                responseWithMetadata.put("intent", intent);
                responseWithMetadata.put("apiResponse", apiResponse);
                apiResponses.add(responseWithMetadata);
                
            } catch (Exception e) {
                log.error("Error calling API for intent - correlationId: {}, domain: {}, error: {}", 
                        correlationId, domain, e.getMessage(), e);
                // Continue with other intents even if one fails
            }
        }
        
        // Store API responses in state
        state.setFetchedData(apiResponses);
        
        log.info("Fetch completed - correlationId: {}, intents processed: {}, APIs called: {}", 
                correlationId, intents.size(), apiResponses.size());
        
        return state;
    }
    
    /**
     * Calls the appropriate banking API based on intent domain.
     * 
     * @param intent Intent data containing domain, metric, and entity hints
     * @param customerId Customer ID (from trusted source)
     * @param fromDate Start date (YYYY-MM-DD)
     * @param toDate End date (YYYY-MM-DD)
     * @param correlationId Correlation ID for logging
     * @return API response as Map/Object
     */
    private Map<String, Object> callApiForIntent(
            IntentExtractionResponse.IntentData intent,
            String customerId,
            String fromDate,
            String toDate,
            String correlationId) throws IOException {
        
        String domain = intent.getDomain();
        IntentExtractionResponse.EntityHints entityHints = intent.getEntityHints();
        
        // Extract entity hints and filter out masked account IDs
        // Masked IDs (like "****1234") cannot be used for API filtering
        List<String> accountIds = entityHints != null ? entityHints.getAccountIds() : null;
        accountIds = filterOutMaskedAccountIds(accountIds, correlationId);
        List<String> cardIds = entityHints != null ? entityHints.getCardIds() : null;
        
        // Determine if transactions should be included based on metric
        Boolean includeTransactions = shouldIncludeTransactions(intent.getMetric());
        
        // Call appropriate API based on domain
        return switch (domain) {
            case "current-accounts" -> currentAccountsService.getCurrentAccountsData(
                    customerId, fromDate, toDate, accountIds, includeTransactions);
            
            case "foreign-current-accounts" -> foreignCurrentAccountsService.getForeignCurrentAccountsData(
                    customerId, fromDate, toDate, accountIds, includeTransactions);
            
            case "credit-cards" -> {
                // For credit cards, use cardIds as last4Digits (nickname)
                String last4Digits = (cardIds != null && !cardIds.isEmpty()) ? cardIds.get(0) : null;
                yield creditCardsService.getCreditCardsData(
                        customerId, fromDate, toDate, last4Digits, includeTransactions);
            }
            
            case "loans" -> {
                // Extract nickname from parameters if available
                String nickname = extractNickname(intent);
                yield loansService.getLoansData(
                        customerId, fromDate, toDate, nickname, includeTransactions );
            }
            
            case "mortgages" -> {
                String nickname = extractNickname(intent);
                yield mortgageService.getMortgageData(
                        customerId, fromDate, toDate, nickname, includeTransactions);
            }
            
            case "deposits" -> {
                String nickname = extractNickname(intent);
                yield depositsService.getDepositsData(
                        customerId, fromDate, toDate, nickname, includeTransactions);
            }
            
            case "securities" -> securitiesService.getSecuritiesData(customerId, includeTransactions);
            
            default -> {
                log.warn("Unknown domain - correlationId: {}, domain: {}", correlationId, domain);
                throw new IllegalArgumentException("Unknown domain: " + domain);
            }
        };
    }
    
    /**
     * Determines if transactions should be included based on metric.
     * 
     * @param metric Metric from intent (e.g., "balance", "list", "sum")
     * @return true if transactions should be included, false otherwise
     */
    private Boolean shouldIncludeTransactions(String metric) {
        if (metric == null) {
            return true; // Default to including transactions
        }
        
        // Metrics that require transactions: list, sum, count, max, min, average
        return switch (metric.toLowerCase()) {
            case "balance" -> false; // Balance doesn't need transactions
            case "list", "sum", "count", "max", "min", "average" -> true;
            default -> true; // Default to including transactions
        };
    }
    
    /**
     * Extracts nickname from intent parameters or entity hints.
     * 
     * @param intent Intent data
     * @return Nickname string or null
     */
    private String extractNickname(IntentExtractionResponse.IntentData intent) {
        // Try to get nickname from parameters
        if (intent.getParameters() != null) {
            Object nicknameObj = intent.getParameters().get("nickname");
            if (nicknameObj != null) {
                return nicknameObj.toString();
            }
        }
        
        // Try to get from entity hints (cardIds for credit cards, otherEntities for others)
        if (intent.getEntityHints() != null) {
            List<String> cardIds = intent.getEntityHints().getCardIds();
            if (cardIds != null && !cardIds.isEmpty()) {
                return cardIds.get(0);
            }
            
            // Check otherEntities for loan/deposit IDs
            Map<String, List<String>> otherEntities = intent.getEntityHints().getOtherEntities();
            if (otherEntities != null && !otherEntities.isEmpty()) {
                // Try common keys
                for (String key : List.of("loanIds", "depositIds", "mortgageIds")) {
                    List<String> ids = otherEntities.get(key);
                    if (ids != null && !ids.isEmpty()) {
                        return ids.get(0);
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Filters out masked account IDs from the list.
     * Masked account IDs (containing "****") cannot be used for API filtering
     * because the API expects real account IDs.
     * 
     * @param accountIds List of account IDs (may contain masked IDs)
     * @param correlationId Correlation ID for logging
     * @return Filtered list with only real account IDs, or null if all were masked
     */
    private List<String> filterOutMaskedAccountIds(List<String> accountIds, String correlationId) {
        if (accountIds == null || accountIds.isEmpty()) {
            return null;
        }
        
        List<String> realAccountIds = accountIds.stream()
                .filter(id -> id != null && !id.contains("****"))
                .toList();
        
        if (realAccountIds.isEmpty()) {
            log.info("All account IDs are masked - correlationId: {}, maskedIds: {}. " +
                    "Will fetch all accounts without filtering.", correlationId, accountIds);
            return null; // Return null to fetch all accounts
        }
        
        if (realAccountIds.size() < accountIds.size()) {
            log.info("Filtered out masked account IDs - correlationId: {}, " +
                    "originalCount: {}, filteredCount: {}, maskedIds: {}",
                    correlationId, accountIds.size(), realAccountIds.size(),
                    accountIds.stream().filter(id -> id != null && id.contains("****")).toList());
        }
        
        return realAccountIds;
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
