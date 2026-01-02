package com.demoBank.chatDemo.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Normalized transaction structure - unified across all domains.
 * 
 * Maps different transaction formats:
 * - Current accounts: transactionId, type, status, amount, bookingDate, valueDate
 * - Credit cards: transactionId, status, type, amount, transactionDate, postingDate
 * - Loans/Mortgages/Deposits: transactionId, type, status, amount, bookingDate
 * - Foreign accounts: same as current accounts + fxRate
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NormalizedTransaction {
    
    /**
     * Transaction identifier
     */
    private String transactionId;
    
    /**
     * Transaction type: "CREDIT", "DEBIT", "PURCHASE", "PAYMENT", 
     * "INSTALLMENT_PAYMENT", "MORTGAGE_PAYMENT", "OPENING", "WITHDRAWAL", etc.
     */
    private String type;
    
    /**
     * Status: "BOOKED", "POSTED", "PENDING"
     */
    private String status;
    
    /**
     * Transaction amount
     */
    private Double amount;
    
    /**
     * Currency code
     */
    private String currency;
    
    /**
     * Transaction date (bookingDate/transactionDate) - ISO date format
     */
    private String date;
    
    /**
     * Value date (valueDate/postingDate) - ISO date format
     * Null if not applicable
     */
    private String valueDate;
    
    /**
     * Transaction description
     */
    private String description;
    
    /**
     * Merchant information (null if not applicable)
     */
    private NormalizedMerchant merchant;
    
    /**
     * Category information (null if not applicable)
     */
    private NormalizedCategory category;
    
    /**
     * Counterparty information (null if not applicable)
     */
    private NormalizedCounterparty counterparty;
    
    /**
     * Transaction references (null if not applicable)
     */
    private NormalizedTransactionReferences references;
    
    /**
     * Enrichment data (null if not applicable)
     */
    private NormalizedEnrichment enrichment;
    
    /**
     * Installment information (credit cards only, null if not applicable)
     */
    private NormalizedInstallments installments;
    
    /**
     * FX rate information (foreign accounts only, null if not applicable)
     */
    private NormalizedFxRate fxRate;
}
