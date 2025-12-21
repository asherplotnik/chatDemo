package com.demoBank.chatDemo.orchestrator.model;

import java.util.List;
import java.util.Map;

/**
 * Function definition for time range resolution.
 * Defines the structured function schema for function calling.
 */
public class TimeRangeFunctionDefinition {
    
    public static Map<String, Object> getFunctionSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "fromDate", Map.of(
                    "type", "string",
                    "description", "Start date in YYYY-MM-DD format",
                    "pattern", "^\\d{4}-\\d{2}-\\d{2}$"
                ),
                "toDate", Map.of(
                    "type", "string",
                    "description", "End date in YYYY-MM-DD format",
                    "pattern", "^\\d{4}-\\d{2}-\\d{2}$"
                )
            ),
            "required", List.of("fromDate", "toDate")
        );
    }
    
    public static final String FUNCTION_NAME = "resolve_time_range";
    public static final String FUNCTION_DESCRIPTION = """
        Resolves a relative time expression into absolute dates.
        Returns start date (fromDate) and end date (toDate) in YYYY-MM-DD format.
        """;
}
