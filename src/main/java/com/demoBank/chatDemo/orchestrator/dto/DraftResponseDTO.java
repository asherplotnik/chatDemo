package com.demoBank.chatDemo.orchestrator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO for structured response from draft response function call.
 * 
 * Matches the function schema defined in DraftResponseFunctionDefinition.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DraftResponseDTO {
    
    /**
     * Natural language introduction (2-4 lines).
     */
    private String introduction;
    
    /**
     * Structured table data. Can be null if no table is needed.
     */
    private TableData table;
    
    /**
     * Information about where the data came from.
     */
    private DataSource dataSource;
    
    /**
     * Table data structure.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TableData {
        /**
         * Type of table: 'transactions', 'balance', 'summary', 'list', or 'custom'.
         */
        private String type;
        
        /**
         * Column header names.
         */
        private List<String> headers;
        
        /**
         * Array of row objects. Each row is a map with keys matching header names.
         */
        private List<Map<String, Object>> rows;
        
        /**
         * Optional metadata about the table.
         */
        private TableMetadata metadata;
    }
    
    /**
     * Table metadata.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TableMetadata {
        /**
         * Total number of rows in the table.
         */
        private Integer rowCount;
        
        /**
         * Whether the table includes totals row.
         */
        private Boolean hasTotals;
        
        /**
         * Totals row data (if hasTotals is true).
         */
        private Map<String, Object> totals;
    }
    
    /**
     * Data source information.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DataSource {
        /**
         * API source (e.g., 'current-accounts', 'credit-cards', 'loans').
         */
        private String api;
        
        /**
         * Time range used (e.g., '2025-12-01 to 2025-12-13').
         */
        private String timeRange;
        
        /**
         * List of entity identifiers used (account IDs, card IDs, etc.).
         */
        private List<String> entities;
        
        /**
         * Human-readable description of the data source.
         */
        private String description;
    }
}
