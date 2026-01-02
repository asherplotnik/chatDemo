package com.demoBank.chatDemo.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Normalized balance structure - unified across all entity types.
 * 
 * Maps different balance formats:
 * - Current accounts: balances.current, balances.available, balances.holds
 * - Credit cards: currentBalance.postedBalance, currentBalance.pendingAmount, limits.availableCredit
 * - Loans/Mortgages/Deposits: balances.principalOutstanding, balances.accruedInterest, balances.totalOutstanding
 * - Securities: valuation.marketValueBase, valuation.cashBalanceBase, valuation.totalValueBase
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NormalizedBalance {
    
    /**
     * Timestamp when balance was calculated (ISO format)
     */
    private String asOf;
    
    /**
     * Currency code
     */
    private String currency;
    
    /**
     * Current balance (mapped from current/postedBalance/principalOutstanding)
     */
    private Double current;
    
    /**
     * Available balance (mapped from available/availableCredit)
     * Null if not applicable (e.g., loans)
     */
    private Double available;
    
    /**
     * Pending/holds amount (mapped from holds/pendingAmount)
     * Null if not applicable
     */
    private Double pending;
    
    /**
     * Credit limit (for credit cards and overdraft accounts)
     * Null if not applicable
     */
    private Double creditLimit;
    
    /**
     * Available credit (for credit cards)
     * Null if not applicable
     */
    private Double availableCredit;
    
    /**
     * Principal outstanding (for loans, mortgages, deposits)
     * Null if not applicable
     */
    private Double principalOutstanding;
    
    /**
     * Accrued interest (for loans, mortgages, deposits)
     * Null if not applicable
     */
    private Double accruedInterest;
    
    /**
     * Total outstanding (for loans, mortgages)
     * Null if not applicable
     */
    private Double totalOutstanding;
    
    /**
     * Market value (for securities)
     * Null if not applicable
     */
    private Double marketValue;
    
    /**
     * Cash balance (for securities)
     * Null if not applicable
     */
    private Double cashBalance;
    
    /**
     * Total value (for securities)
     * Null if not applicable
     */
    private Double totalValue;
}
