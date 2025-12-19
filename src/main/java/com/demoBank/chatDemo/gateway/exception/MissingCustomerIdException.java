package com.demoBank.chatDemo.gateway.exception;

/**
 * Exception thrown when customerId header is missing.
 */
public class MissingCustomerIdException extends RuntimeException {
    
    public MissingCustomerIdException(String message) {
        super(message);
    }
}
