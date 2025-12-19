package com.demoBank.chatDemo.gateway.service;

import com.demoBank.chatDemo.gateway.util.CustomerIdMasker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory rate limiter.
 * For POC purposes - in production, consider using Redis or a dedicated rate limiting library.
 * 
 * Rate limit: 15 requests per minute per customer.
 */
@Slf4j
@Service
public class RateLimiter {
    
    private static final int MAX_REQUESTS_PER_MINUTE = 15;
    private static final long WINDOW_SIZE_SECONDS = 60;
    
    // In-memory store: customerId -> list of request timestamps
    private final Map<String, RequestWindow> customerWindows = new ConcurrentHashMap<>();
    
    /**
     * Checks if the request should be allowed based on rate limiting.
     * 
     * @param customerId The customer ID to check rate limit for
     * @return true if request is allowed, false if rate limit exceeded
     */
    public boolean isAllowed(String customerId) {
        RequestWindow window = customerWindows.computeIfAbsent(
            customerId, 
            k -> new RequestWindow()
        );
        
        Instant now = Instant.now();
        window.cleanOldRequests(now);
        
        if (window.getRequestCount() >= MAX_REQUESTS_PER_MINUTE) {
            log.warn("Rate limit exceeded for customerId: {}", CustomerIdMasker.mask(customerId));
            return false;
        }
        
        window.addRequest(now);
        return true;
    }
    
    /**
     * Inner class to track request window for a customer.
     */
    private static class RequestWindow {
        private final java.util.List<Instant> requests = new java.util.ArrayList<>();
        
        synchronized void addRequest(Instant timestamp) {
            requests.add(timestamp);
        }
        
        synchronized void cleanOldRequests(Instant now) {
            Instant cutoff = now.minusSeconds(WINDOW_SIZE_SECONDS);
            requests.removeIf(timestamp -> timestamp.isBefore(cutoff));
        }
        
        synchronized int getRequestCount() {
            return requests.size();
        }
    }
}
