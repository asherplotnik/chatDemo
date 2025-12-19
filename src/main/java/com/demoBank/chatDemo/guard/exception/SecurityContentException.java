package com.demoBank.chatDemo.guard.exception;

/**
 * Exception thrown when malicious content or prompt injection is detected.
 */
public class SecurityContentException extends RuntimeException {

    public SecurityContentException(String message) {
        super(message);
    }

    public SecurityContentException(String message, Throwable cause) {
        super(message, cause);
    }
}
