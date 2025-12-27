package com.demoBank.chatDemo.gateway.dto;

import com.demoBank.chatDemo.orchestrator.dto.DraftResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
    private String language = "en"; // Default to English
    
    /**
     * List of structured table data. Each table represents data for a specific account/entity.
     * Can be null or empty if no tables are needed.
     * Frontend can check this field to identify and render tabular data.
     * Each table should be rendered separately, typically one per account.
     */
    private List<DraftResponseDTO.TableData> tables;
}
