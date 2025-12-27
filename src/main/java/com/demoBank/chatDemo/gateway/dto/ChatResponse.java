package com.demoBank.chatDemo.gateway.dto;

import com.demoBank.chatDemo.orchestrator.dto.DraftResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for chat messages.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatResponse {
    
    private String answer;
    private String correlationId;
    private String explanation; // "How I got this" explanation
    
    /**
     * Structured table data. Can be null if no table is needed.
     * Frontend can check this field to identify and render tabular data.
     */
    private DraftResponseDTO.TableData table;
}
