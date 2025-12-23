package com.demoBank.chatDemo.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Transaction reference for summary (largest debit/credit).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NormalizedTransactionReference {
    
    private Double amount;
    
    private String currency;
    
    private String transactionId;
    
    private String bookingDate;
    
    private String description;
}
