package com.demoBank.chatDemo.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Normalized transactions summary.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NormalizedTransactionsSummary {
    
    /**
     * Start date (ISO date format)
     */
    private String fromDate;
    
    /**
     * End date (ISO date format)
     */
    private String toDate;
    
    /**
     * Total number of transactions
     */
    private Integer transactionCount;
    
    /**
     * Total debits
     */
    private Double totalDebits;
    
    /**
     * Total credits
     */
    private Double totalCredits;
    
    /**
     * Largest debit transaction reference (null if not available)
     */
    private NormalizedTransactionReference largestDebit;
    
    /**
     * Largest credit transaction reference (null if not available)
     */
    private NormalizedTransactionReference largestCredit;
    
    /**
     * Last activity date (ISO date format, null if not available)
     */
    private String lastActivityDate;
    
    /**
     * ILS equivalent amounts (foreign accounts only, null if not applicable)
     */
    private NormalizedIlsEquivalent ilsEquivalent;
}
