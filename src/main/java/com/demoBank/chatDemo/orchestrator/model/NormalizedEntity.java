package com.demoBank.chatDemo.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Normalized entity - unified representation of any banking entity
 * (account, card, loan, mortgage, deposit, securities account).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NormalizedEntity {
    
    /**
     * Entity identifier (accountId, cardId, loanId, etc.)
     */
    private String entityId;
    
    /**
     * Entity type: "ACCOUNT", "CARD", "LOAN", "MORTGAGE", "DEPOSIT", "SECURITIES_ACCOUNT"
     */
    private String entityType;
    
    /**
     * Nickname/display name
     */
    private String nickname;
    
    /**
     * Currency code (e.g., "ILS", "USD", "EUR")
     */
    private String currency;
    
    /**
     * Status: "ACTIVE", "CLOSED", etc.
     */
    private String status;
    
    /**
     * Unified balance structure
     */
    private NormalizedBalance balance;
    
    /**
     * Normalized transactions
     */
    private List<NormalizedTransaction> transactions;
    
    /**
     * Transactions summary
     */
    private NormalizedTransactionsSummary transactionsSummary;
    
    /**
     * Domain-specific fields preserved as-is for reference
     * (e.g., billingCycle for cards, schedule for loans, segments for mortgages)
     */
    private Map<String, Object> domainSpecific;
}
