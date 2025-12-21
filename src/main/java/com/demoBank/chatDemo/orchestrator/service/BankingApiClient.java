package com.demoBank.chatDemo.orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Client for calling banking APIs.
 * 
 * Handles HTTP communication with all banking API endpoints:
 * - Current Accounts Transactions
 * - Foreign Current Accounts Transactions
 * - Credit Cards
 * - Loans
 * - Mortgages
 * - Deposits
 * - Securities
 */
@Slf4j
@Service
public class BankingApiClient {
    
    private final ObjectMapper objectMapper;
    private RestClient restClient;
    
    @Value("${banking.api.base-url:http://localhost:8081/api}")
    private String baseUrl;
    
    @Value("${banking.api.timeout:30000}")
    private int timeoutMs;
    
    public BankingApiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Gets or initializes the RestClient instance.
     */
    private RestClient getRestClient() {
        if (restClient == null) {
            this.restClient = RestClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
        }
        return restClient;
    }
    
    /**
     * Calls the Current Accounts Transactions API.
     * 
     * @param customerId Customer ID (from trusted source)
     * @param fromDate Start date (YYYY-MM-DD)
     * @param toDate End date (YYYY-MM-DD)
     * @param accountIds Optional list of account IDs to filter
     * @param includePending Whether to include pending transactions (default: true)
     * @param pageSize Max number of transactions per account (default: 100, max: 500)
     * @param cursor Cursor for pagination (optional)
     * @param timezone IANA timezone (default: Asia/Jerusalem)
     * @param includeTransactions Whether to include transactions (default: true)
     * @param correlationId Correlation ID for logging
     * @return API response as Map/Object
     */
    public Map<String, Object> getCurrentAccountsTransactions(
            String customerId,
            String fromDate,
            String toDate,
            List<String> accountIds,
            Boolean includePending,
            Integer pageSize,
            String cursor,
            String timezone,
            Boolean includeTransactions,
            String correlationId) {
        
        log.info("Calling Current Accounts Transactions API - correlationId: {}, customerId: {}, fromDate: {}, toDate: {}", 
                correlationId, maskCustomerId(customerId), fromDate, toDate);
        
        try {
            RestClient.RequestHeadersSpec<?> request = getRestClient().get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/v1/current-accounts/transactions")
                                .queryParam("fromDate", fromDate)
                                .queryParam("toDate", toDate);
                        
                        // Add optional parameters
                        if (includePending != null) {
                            uriBuilder.queryParam("includePending", includePending);
                        }
                        if (pageSize != null) {
                            uriBuilder.queryParam("pageSize", pageSize);
                        }
                        if (cursor != null) {
                            uriBuilder.queryParam("cursor", cursor);
                        }
                        if (timezone != null && !timezone.isBlank()) {
                            uriBuilder.queryParam("timezone", timezone);
                        }
                        if (accountIds != null && !accountIds.isEmpty()) {
                            // accountList is an array parameter with explode: true
                            for (String accountId : accountIds) {
                                uriBuilder.queryParam("accountList", accountId);
                            }
                        }
                        if (includeTransactions != null) {
                            uriBuilder.queryParam("includeTransactions", includeTransactions);
                        }
                        
                        return uriBuilder.build();
                    })
                    .header("customerID", customerId)
                    .header("X-Correlation-Id", correlationId);
            
            Map<String, Object> response = request.retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> {
                        log.error("Current Accounts Transactions API error - correlationId: {}, status: {}, customerId: {}", 
                                correlationId, res.getStatusCode(), maskCustomerId(customerId));
                        throw new RuntimeException("API call failed with status: " + res.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<>() {});
            
            log.info("Current Accounts Transactions API call successful - correlationId: {}, customerId: {}", 
                    correlationId, maskCustomerId(customerId));
            
            return response;
            
        } catch (Exception e) {
            log.error("Error calling Current Accounts Transactions API - correlationId: {}, customerId: {}, error: {}", 
                    correlationId, maskCustomerId(customerId), e.getMessage(), e);
            throw new RuntimeException("Failed to call Current Accounts Transactions API: " + e.getMessage(), e);
        }
    }
    
    /**
     * Calls the Foreign Current Accounts Transactions API.
     * 
     * @param customerId Customer ID (from trusted source)
     * @param fromDate Start date (YYYY-MM-DD)
     * @param toDate End date (YYYY-MM-DD)
     * @param accountIds Optional list of account IDs to filter
     * @param correlationId Correlation ID for logging
     * @return API response as Map/Object
     */
    public Object getForeignCurrentAccountsTransactions(
            String customerId,
            String fromDate,
            String toDate,
            java.util.List<String> accountIds,
            String correlationId) {
        
        log.info("Calling Foreign Current Accounts Transactions API - correlationId: {}, customerId: {}, fromDate: {}, toDate: {}", 
                correlationId, maskCustomerId(customerId), fromDate, toDate);
        
        // TODO: Implement API call
        return null;
    }
    
    /**
     * Calls the Credit Cards API.
     * 
     * @param customerId Customer ID (from trusted source)
     * @param fromDate Start date (YYYY-MM-DD)
     * @param toDate End date (YYYY-MM-DD)
     * @param cardIds Optional list of card IDs to filter
     * @param includePending Whether to include pending transactions
     * @param correlationId Correlation ID for logging
     * @return API response as Map/Object
     */
    public Object getCreditCards(
            String customerId,
            String fromDate,
            String toDate,
            java.util.List<String> cardIds,
            Boolean includePending,
            String correlationId) {
        
        log.info("Calling Credit Cards API - correlationId: {}, customerId: {}, fromDate: {}, toDate: {}", 
                correlationId, maskCustomerId(customerId), fromDate, toDate);
        
        // TODO: Implement API call
        return null;
    }
    
    /**
     * Calls the Loans API.
     * 
     * @param customerId Customer ID (from trusted source)
     * @param fromDate Start date (YYYY-MM-DD)
     * @param toDate End date (YYYY-MM-DD)
     * @param correlationId Correlation ID for logging
     * @return API response as Map/Object
     */
    public Object getLoans(
            String customerId,
            String fromDate,
            String toDate,
            String correlationId) {
        
        log.info("Calling Loans API - correlationId: {}, customerId: {}, fromDate: {}, toDate: {}", 
                correlationId, maskCustomerId(customerId), fromDate, toDate);
        
        // TODO: Implement API call
        return null;
    }
    
    /**
     * Calls the Mortgages API.
     * 
     * @param customerId Customer ID (from trusted source)
     * @param fromDate Start date (YYYY-MM-DD)
     * @param toDate End date (YYYY-MM-DD)
     * @param correlationId Correlation ID for logging
     * @return API response as Map/Object
     */
    public Object getMortgages(
            String customerId,
            String fromDate,
            String toDate,
            String correlationId) {
        
        log.info("Calling Mortgages API - correlationId: {}, customerId: {}, fromDate: {}, toDate: {}", 
                correlationId, maskCustomerId(customerId), fromDate, toDate);
        
        // TODO: Implement API call
        return null;
    }
    
    /**
     * Calls the Deposits API.
     * 
     * @param customerId Customer ID (from trusted source)
     * @param fromDate Start date (YYYY-MM-DD)
     * @param toDate End date (YYYY-MM-DD)
     * @param correlationId Correlation ID for logging
     * @return API response as Map/Object
     */
    public Object getDeposits(
            String customerId,
            String fromDate,
            String toDate,
            String correlationId) {
        
        log.info("Calling Deposits API - correlationId: {}, customerId: {}, fromDate: {}, toDate: {}", 
                correlationId, maskCustomerId(customerId), fromDate, toDate);
        
        // TODO: Implement API call
        return null;
    }
    
    /**
     * Calls the Securities API.
     * 
     * @param customerId Customer ID (from trusted source)
     * @param fromDate Start date (YYYY-MM-DD)
     * @param toDate End date (YYYY-MM-DD)
     * @param correlationId Correlation ID for logging
     * @return API response as Map/Object
     */
    public Object getSecurities(
            String customerId,
            String fromDate,
            String toDate,
            String correlationId) {
        
        log.info("Calling Securities API - correlationId: {}, customerId: {}, fromDate: {}, toDate: {}", 
                correlationId, maskCustomerId(customerId), fromDate, toDate);
        
        // TODO: Implement API call
        return null;
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
