package com.demoBank.chatDemo.gateway.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for generating correlation IDs for request tracking.
 */
@Service
public class CorrelationIdService {
    
    /**
     * Generates a unique correlation ID for request tracking.
     * 
     * @return A UUID-based correlation ID
     */
    public String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }
}
