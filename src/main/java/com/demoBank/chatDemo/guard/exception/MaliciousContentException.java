package com.demoBank.chatDemo.guard.exception;

/**
 * Exception thrown when malicious content or prompt injection is detected.
 */
public class MaliciousContentException extends RuntimeException {
    
    private final boolean isHebrew;
    
    public MaliciousContentException(String message) {
        super(message);
        this.isHebrew = false;
    }
    
    public MaliciousContentException(String message, Throwable cause) {
        super(message, cause);
        this.isHebrew = false;
    }
    
    public MaliciousContentException(String message, boolean isHebrew) {
        super(message);
        this.isHebrew = isHebrew;
    }
    
    public MaliciousContentException(String message, boolean isHebrew, Throwable cause) {
        super(message, cause);
        this.isHebrew = isHebrew;
    }
    
    public boolean isHebrew() {
        return isHebrew;
    }
}
