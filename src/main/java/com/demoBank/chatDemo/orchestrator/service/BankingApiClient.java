//package com.demoBank.chatDemo.orchestrator.service;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.core.ParameterizedTypeReference;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.MediaType;
//import org.springframework.stereotype.Service;
//import org.springframework.web.client.RestClient;
//
//import java.util.List;
//import java.util.Map;
//
///**
// * Client for calling banking APIs.
// *
// * Handles HTTP communication with all banking API endpoints:
// * - Current Accounts Transactions
// * - Foreign Current Accounts Transactions
// * - Credit Cards
// * - Loans
// * - Mortgages
// * - Deposits
// * - Securities
// */
//@Slf4j
//@Service
//public class BankingApiClient {
//
//    private final ObjectMapper objectMapper;
//    private RestClient restClient;
//
//    @Value("${banking.api.base-url:http://localhost:8080}")
//    private String baseUrl;
//
//    @Value("${banking.api.timeout:30000}")
//    private int timeoutMs;
//
//    public BankingApiClient(ObjectMapper objectMapper) {
//        this.objectMapper = objectMapper;
//    }
//
//    /**
//     * Gets or initializes the RestClient instance.
//     */
//    private RestClient getRestClient() {
//        if (restClient == null) {
//            this.restClient = RestClient.builder()
//                    .baseUrl(baseUrl)
//                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
//                    .build();
//        }
//        return restClient;
//    }
//
//    /**
//     * Generic method to call banking APIs.
//     *
//     * All banking APIs follow a consistent pattern with fromDate/toDate parameters.
//     * This method handles the common HTTP GET request pattern.
//     *
//     * @param customerId Customer ID (from trusted source)
//     * @param fromDate Start date (YYYY-MM-DD)
//     * @param toDate End date (YYYY-MM-DD)
//     * @param accountIds Optional list of account IDs to filter (for account-based APIs)
//     * @param includeTransactions Whether to include transactions (for transaction APIs)
//     * @param path API endpoint path (e.g., "/v1/current-accounts/transactions")
//     * @param nickname Optional nickname/last4Digits for credit card filtering
//     * @param correlationId Correlation ID for logging
//     * @return API response as Map/Object
//     */
//    private Map<String, Object> callBankingApi(
//            String customerId,
//            String fromDate,
//            String toDate,
//            List<String> accountIds,
//            Boolean includeTransactions,
//            String path,
//            String nickname,
//            String correlationId) {
//
//        log.info("Calling {} API - correlationId: {}, customerId: {}, fromDate: {}, toDate: {}",
//                path, correlationId, maskCustomerId(customerId), fromDate, toDate);
//
//        try {
//            RestClient.RequestHeadersSpec<?> request = getRestClient().get()
//                    .uri(uriBuilder -> {
//                        uriBuilder.path(path)
//                                .queryParam("fromDate", fromDate)
//                                .queryParam("toDate", toDate);
//
//                        // Add optional parameters
//                        if (accountIds != null && !accountIds.isEmpty()) {
//                            // accountList is an array parameter with explode: true
//                            for (String accountId : accountIds) {
//                                uriBuilder.queryParam("accountList", accountId);
//                            }
//                        }
//                        if (includeTransactions != null) {
//                            // Securities API uses includePositions instead of includeTransactions
//                            if (path.contains("securities")) {
//                                uriBuilder.queryParam("includePositions", includeTransactions);
//                            } else {
//                                uriBuilder.queryParam("includeTransactions", includeTransactions);
//                            }
//                        }
//                        if (nickname != null && !nickname.isBlank()) {
//                            // For credit cards, nickname is used as last4Digits
//                            if (path.contains("credit-cards")) {
//                                uriBuilder.queryParam("last4Digits", nickname);
//                            } else {
//                                uriBuilder.queryParam("nicknameFilter", nickname);
//                            }
//                        }
//                        return uriBuilder.build();
//                    })
//                    .header("customerID", customerId)
//                    .header("X-Correlation-Id", correlationId);
//
//            Map<String, Object> response = request.retrieve()
//                    .onStatus(status -> status.isError(), (req, res) -> {
//                        log.error("{} API error - correlationId: {}, status: {}, customerId: {}",
//                                path, correlationId, res.getStatusCode(), maskCustomerId(customerId));
//                        throw new RuntimeException("API call failed with status: " + res.getStatusCode());
//                    })
//                    .body(new ParameterizedTypeReference<>() {});
//
//            log.info("{} API call successful - correlationId: {}, customerId: {}",
//                    path, correlationId, maskCustomerId(customerId));
//
//            return response;
//
//        } catch (Exception e) {
//            log.error("Error calling {} API - correlationId: {}, customerId: {}, error: {}",
//                    path, correlationId, maskCustomerId(customerId), e.getMessage(), e);
//            throw new RuntimeException("Failed to call " + path + " API: " + e.getMessage(), e);
//        }
//    }
//
//    /**
//     * Calls the Current Accounts Transactions API.
//     *
//     * @param customerId Customer ID (from trusted source)
//     * @param fromDate Start date (YYYY-MM-DD)
//     * @param toDate End date (YYYY-MM-DD)
//     * @param accountIds Optional list of account IDs to filter
//     * @param includeTransactions Whether to include transactions (default: true)
//     * @param correlationId Correlation ID for logging
//     * @return API response as Map/Object
//     */
//    public Map<String, Object> getCurrentAccountsTransactions(
//            String customerId,
//            String fromDate,
//            String toDate,
//            List<String> accountIds,
//            Boolean includeTransactions,
//            String correlationId) {
//
//        return callBankingApi(
//                customerId,
//                fromDate,
//                toDate,
//                accountIds,
//                includeTransactions,
//                "/v1/current-accounts/transactions",
//                null,
//                correlationId
//        );
//    }
//
//    /**
//     * Calls the Foreign Current Accounts Transactions API.
//     *
//     * @param customerId Customer ID (from trusted source)
//     * @param fromDate Start date (YYYY-MM-DD)
//     * @param toDate End date (YYYY-MM-DD)
//     * @param accountIds Optional list of account IDs to filter
//     * @param correlationId Correlation ID for logging
//     * @return API response as Map/Object
//     */
//    public Map<String, Object> getForeignCurrentAccountsTransactions(
//            String customerId,
//            String fromDate,
//            String toDate,
//            List<String> accountIds,
//            String correlationId) {
//
//        return callBankingApi(
//                customerId,
//                fromDate,
//                toDate,
//                accountIds,
//                true, // includeTransactions default true
//                "/v1/foreign-current-accounts/transactions",
//                null,
//                correlationId
//        );
//    }
//
//    /**
//     * Calls the Credit Cards API.
//     *
//     * @param customerId Customer ID (from trusted source)
//     * @param fromDate Start date (YYYY-MM-DD)
//     * @param toDate End date (YYYY-MM-DD)
//     * @param last4Digits Optional last 4 digits of card to filter (nickname)
//     * @param correlationId Correlation ID for logging
//     * @return API response as Map/Object
//     */
//    public Map<String, Object> getCreditCards(
//            String customerId,
//            String fromDate,
//            String toDate,
//            String last4Digits,
//            String correlationId) {
//
//        return callBankingApi(
//                customerId,
//                fromDate,
//                toDate,
//                null, // accountIds not used for credit cards
//                true, // includeTransactions default true
//                "/v1/credit-cards/transactions",
//                last4Digits, // nickname used as last4Digits for credit cards
//                correlationId
//        );
//    }
//
//    /**
//     * Calls the Loans API.
//     *
//     * @param customerId Customer ID (from trusted source)
//     * @param fromDate Start date (YYYY-MM-DD)
//     * @param toDate End date (YYYY-MM-DD)
//     * @param includeTransactions Whether to include transactions
//     * @param nickname Optional nickname to filter
//     * @param correlationId Correlation ID for logging
//     * @return API response as Map/Object
//     */
//    public Map<String, Object> getLoans(
//            String customerId,
//            String fromDate,
//            String toDate,
//            Boolean includeTransactions,
//            String nickname,
//            String correlationId) {
//
//        return callBankingApi(
//                customerId,
//                fromDate,
//                toDate,
//                null, // accountIds not used
//                includeTransactions,
//                "/v1/loans",
//                nickname,
//                correlationId
//        );
//    }
//
//    /**
//     * Calls the Mortgages API.
//     *
//     * @param customerId Customer ID (from trusted source)
//     * @param fromDate Start date (YYYY-MM-DD)
//     * @param toDate End date (YYYY-MM-DD)
//     * @param includeTransactions Whether to include transactions
//     * @param nickname Optional nickname to filter
//     * @param correlationId Correlation ID for logging
//     * @return API response as Map/Object
//     */
//    public Map<String, Object> getMortgages(
//            String customerId,
//            String fromDate,
//            String toDate,
//            Boolean includeTransactions,
//            String nickname,
//            String correlationId) {
//
//        return callBankingApi(
//                customerId,
//                fromDate,
//                toDate,
//                null, // accountIds not used
//                includeTransactions,
//                "/v1/mortgages",
//                nickname,
//                correlationId
//        );
//    }
//
//    /**
//     * Calls the Deposits API.
//     *
//     * @param customerId Customer ID (from trusted source)
//     * @param fromDate Start date (YYYY-MM-DD)
//     * @param toDate End date (YYYY-MM-DD)
//     * @param includeTransactions Whether to include transactions
//     * @param nickname Optional nickname to filter
//     * @param correlationId Correlation ID for logging
//     * @return API response as Map/Object
//     */
//    public Map<String, Object> getDeposits(
//            String customerId,
//            String fromDate,
//            String toDate,
//            Boolean includeTransactions,
//            String nickname,
//            String correlationId) {
//
//        return callBankingApi(
//                customerId,
//                fromDate,
//                toDate,
//                null, // accountIds not used
//                includeTransactions,
//                "/v1/deposits",
//                nickname,
//                correlationId
//        );
//    }
//
//    /**
//     * Calls the Securities API.
//     *
//     * @param customerId Customer ID (from trusted source)
//     * @param fromDate Start date (YYYY-MM-DD)
//     * @param toDate End date (YYYY-MM-DD)
//     * @param includePositions Whether to include positions
//     * @param correlationId Correlation ID for logging
//     * @return API response as Map/Object
//     */
//    public Map<String, Object> getSecurities(
//            String customerId,
//            String fromDate,
//            String toDate,
//            Boolean includePositions,
//            String correlationId) {
//
//        return callBankingApi(
//                customerId,
//                fromDate,
//                toDate,
//                null, // accountIds not used
//                includePositions,
//                "/v1/securities",
//                null,
//                correlationId
//        );
//    }
//
//    /**
//     * Masks customer ID for logging (privacy compliance).
//     *
//     * @param customerId Customer ID to mask
//     * @return Masked customer ID
//     */
//    private String maskCustomerId(String customerId) {
//        if (customerId == null || customerId.length() <= 4) {
//            return "****";
//        }
//        return customerId.substring(0, 2) + "****" + customerId.substring(customerId.length() - 2);
//    }
//}
