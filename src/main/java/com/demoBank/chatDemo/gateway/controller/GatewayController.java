package com.demoBank.chatDemo.gateway.controller;

import com.demoBank.chatDemo.gateway.dto.ChatRequest;
import com.demoBank.chatDemo.gateway.dto.ChatResponse;
import com.demoBank.chatDemo.gateway.service.GatewayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Gateway REST controller - thin HTTP layer for chat requests.
 * 
 * Responsibilities:
 * - Handle HTTP requests/responses
 * - Extract HTTP headers
 * - Delegate business logic to GatewayService
 */
@RestController
@RequestMapping("/api/v1/chat")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RequiredArgsConstructor
public class GatewayController {
    
    private static final String CUSTOMER_ID_HEADER = "X-Customer-ID";
    
    private final GatewayService gatewayService;
    
    /**
     * Handle preflight OPTIONS requests for CORS.
     */
    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> options() {
        return ResponseEntity.ok().build();
    }
    
    /**
     * Chat endpoint - receives chat messages from authenticated customers.
     * 
     * @param request Chat request containing messageText
     * @param customerIdHeader Customer ID from HTTP header (trusted)
     * @return Chat response with answer
     */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request,
            @RequestHeader(value = CUSTOMER_ID_HEADER, required = false) String customerIdHeader) {
        
        ChatResponse response = gatewayService.processChatRequest(request, customerIdHeader);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Logout endpoint - removes session context from memory for the customer.
     * POC: Simple logout that removes session based on X-Customer-ID header.
     * 
     * @param customerIdHeader Customer ID from HTTP header (trusted)
     * @return Success response
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader(value = CUSTOMER_ID_HEADER, required = false) String customerIdHeader) {
        
        gatewayService.logout(customerIdHeader);
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Logged out successfully");
        return ResponseEntity.ok(response);
    }
}
