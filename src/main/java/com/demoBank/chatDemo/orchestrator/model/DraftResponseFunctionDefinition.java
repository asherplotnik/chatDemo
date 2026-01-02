package com.demoBank.chatDemo.orchestrator.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Function definition for structured response drafting.
 * Defines the structured function schema for function calling.
 * 
 * Response structure:
 * - introduction: Natural language introduction text
 * - table: Structured table data (can be null if no table needed)
 * - dataSource: Information about where the data came from
 */
@Data
@Builder
public class DraftResponseFunctionDefinition {
    
    public static Map<String, Object> getFunctionSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "introduction", Map.of(
                    "type", "string",
                    "description", "A concise, natural language introduction (2-4 lines) that answers the customer's question. Include key numbers, amounts, dates, and descriptions."
                ),
                "tables", Map.of(
                    "type", List.of("array", "null"),
                    "description", "List of structured table data. Create SEPARATE tables for each account/entity to keep data organized. Always include tables when there is data to display. Set to null or empty array only if no data exists.",
                    "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "accountName", Map.of(
                                "type", "string",
                                "description", "Name/nickname of the account this table represents (e.g., 'Main Account', 'Savings Account')"
                            ),
                            "type", Map.of(
                                "type", "string",
                                "description", "Type of table: 'transactions', 'balance', 'summary', 'list', or 'custom'",
                                "enum", List.of("transactions", "balance", "summary", "list", "custom")
                            ),
                            "headers", Map.of(
                                "type", "array",
                                "description", "Array of column header names (e.g., ['Date', 'Amount', 'Description'])",
                                "items", Map.of("type", "string")
                            ),
                            "rows", Map.of(
                                "type", "array",
                                "description", "Array of row objects. Each row is an object with keys matching header names.",
                                "items", Map.of(
                                    "type", "object",
                                    "description", "A row object with keys matching header names",
                                    "additionalProperties", true
                                )
                            ),
                            "metadata", Map.of(
                                "type", "object",
                                "description", "Optional metadata about the table",
                                "properties", Map.of(
                                    "rowCount", Map.of(
                                        "type", "integer",
                                        "description", "Total number of rows in the table"
                                    ),
                                    "hasTotals", Map.of(
                                        "type", "boolean",
                                        "description", "Whether the table includes totals row"
                                    ),
                                    "totals", Map.of(
                                        "type", "object",
                                        "description", "Totals row data (if hasTotals is true)",
                                        "additionalProperties", true
                                    )
                                )
                            )
                        ),
                        "required", List.of("type", "headers", "rows")
                    )
                ),
                "dataSource", Map.of(
                    "type", "object",
                    "description", "Information about where the data came from",
                    "properties", Map.of(
                        "api", Map.of(
                            "type", "string",
                            "description", "API source (e.g., 'current-accounts', 'credit-cards', 'loans')"
                        ),
                        "timeRange", Map.of(
                            "type", "string",
                            "description", "Time range used (e.g., '2025-12-01 to 2025-12-13')"
                        ),
                        "entities", Map.of(
                            "type", "array",
                            "description", "List of entity identifiers used (account IDs, card IDs, etc.)",
                            "items", Map.of("type", "string")
                        ),
                        "description", Map.of(
                            "type", "string",
                            "description", "Human-readable description of the data source (e.g., 'Retrieved from your current accounts for December 1-13, 2025')"
                        )
                    ),
                    "required", List.of("description")
                )
            ),
            "required", List.of("introduction", "dataSource")
        );
    }
    
    public static final String FUNCTION_NAME = "draft_structured_response";
    public static final String FUNCTION_DESCRIPTION = """
        Drafts a structured customer-facing response from normalized banking data.
        Always includes an introduction, list of tables (one per account if data exists), and data source information.
        Create SEPARATE tables for each account/entity to keep data organized.
        The tables structure allows the frontend to reliably identify and render tabular data.
        """;
}
