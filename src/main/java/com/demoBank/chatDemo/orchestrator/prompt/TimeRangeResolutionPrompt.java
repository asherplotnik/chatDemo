package com.demoBank.chatDemo.orchestrator.prompt;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * System prompts for LLM-based time range resolution.
 * 
 * Used when deterministic parsing cannot resolve the time range hint.
 */
public class TimeRangeResolutionPrompt {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    
    private TimeRangeResolutionPrompt() {}
    
    /**
     * Gets the system prompt for resolving complex time range expressions.
     * 
     * @param timezone Timezone string (e.g., "Asia/Jerusalem") or null for system default
     * @return System prompt for time range resolution
     */
    public static String getSystemPrompt(String timezone) {
        String today = LocalDate.now().format(DATE_FORMATTER);
        String timezoneInfo = timezone != null && !timezone.isBlank() 
                ? String.format("Current timezone: %s. Today's date: %s.", timezone, today)
                : String.format("Today's date: %s.", today);
        
        return """
            You are a time range resolution system. Your task is to convert relative time expressions 
            into absolute date ranges in YYYY-MM-DD format.
            
            %s
            
            IMPORTANT: Week starts on Sunday (first day of the week).
            
            Rules:
            1. Convert the user's time expression into a date range with fromDate and toDate
            2. Both dates must be in YYYY-MM-DD format
            3. fromDate must be before or equal to toDate
            4. Use the current date as reference point
            5. Consider the timezone if provided
            6. Week calculations: Sunday is the first day of the week, Saturday is the last day
            
            Examples:
            - "since last november" → fromDate: "2023-11-01", toDate: "2024-01-15" (assuming today is Jan 15, 2024)
            - "from January to March" → fromDate: "2024-01-01", toDate: "2024-03-31"
            - "last quarter" → Calculate based on current quarter
            - "since last Monday" → Calculate the date of last Monday, toDate: today
            - "this week" → fromDate: last Sunday, toDate: today
            - "last week" → fromDate: Sunday of last week, toDate: Saturday of last week
            
            Use the resolve_time_range function to return the resolved dates.
            """.formatted(timezoneInfo);
    }
}
