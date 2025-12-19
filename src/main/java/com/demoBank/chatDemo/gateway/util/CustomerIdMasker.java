package com.demoBank.chatDemo.gateway.util;

/**
 * Utility class for masking customer IDs in logs (privacy compliance).
 */
public class CustomerIdMasker {
    
    /**
     * Masks customer ID for logging (privacy compliance).
     * Shows first 2 and last 2 characters, masks the middle.
     * 
     * @param customerId The customer ID to mask
     * @return Masked customer ID (e.g., "12****34")
     */
    public static String mask(String customerId) {
        if (customerId == null || customerId.length() <= 4) {
            return "****";
        }
        return customerId.substring(0, 2) + "****" + customerId.substring(customerId.length() - 2);
    }
}
