package com.demoBank.chatDemo.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for chat messages.
 * Contains only the message text - customerId comes from HTTP header.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    
    @NotBlank(message = "messageText cannot be blank")
    private String messageText;
}
