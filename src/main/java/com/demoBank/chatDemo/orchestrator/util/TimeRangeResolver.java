package com.demoBank.chatDemo.orchestrator.util;

import com.demoBank.chatDemo.gateway.model.ChatSessionContext;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for resolving time range hints to absolute dates.
 * 
 * Uses deterministic logic for common patterns, falls back to LLM for complex cases.
 */
@Slf4j
public class TimeRangeResolver {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    
    private TimeRangeResolver() {}
    
    /**
     * Result of time range resolution.
     */
    public static class ResolvedTimeRange {
        private final String fromDate; // YYYY-MM-DD
        private final String toDate;   // YYYY-MM-DD
        private final boolean needsLLM; // true if LLM resolution is needed
        
        public ResolvedTimeRange(String fromDate, String toDate, boolean needsLLM) {
            this.fromDate = fromDate;
            this.toDate = toDate;
            this.needsLLM = needsLLM;
        }
        
        public String getFromDate() {
            return fromDate;
        }
        
        public String getToDate() {
            return toDate;
        }
        
        public boolean needsLLM() {
            return needsLLM;
        }
    }
    
    /**
     * Attempts to resolve time range hint using deterministic logic.
     * 
     * @param timeRangeHint Time range hint from intent extraction (e.g., "last week", "yesterday")
     * @param timezone Timezone string (e.g., "Asia/Jerusalem") or null for system default
     * @return ResolvedTimeRange with dates if resolved deterministically, or needsLLM=true if LLM is needed
     */
    public static ResolvedTimeRange resolveDeterministically(String timeRangeHint, String timezone) {
        if (timeRangeHint == null || timeRangeHint.isBlank()) {
            // No hint provided - use default (start of current month to today)
            return getDefaultTimeRange();
        }
        
        String hint = timeRangeHint.trim().toLowerCase();
        LocalDate today = getTodayInTimezone(timezone);
        
        // Try to parse as already formatted date range: "2024-01-01 to 2024-01-31"
        ResolvedTimeRange parsedRange = parseFormattedDateRange(hint);
        if (parsedRange != null) {
            return parsedRange;
        }
        
        // Common patterns that can be resolved deterministically
        return switch (hint) {
            case "today" -> new ResolvedTimeRange(
                formatDate(today), 
                formatDate(today), 
                false
            );
            case "yesterday" -> {
                LocalDate yesterday = today.minusDays(1);
                yield new ResolvedTimeRange(
                    formatDate(yesterday), 
                    formatDate(yesterday), 
                    false
                );
            }
            case "this week" -> {
                // Sunday is the first day of the week (Israel)
                // Java DayOfWeek: MONDAY=1, TUESDAY=2, ..., SUNDAY=7
                // Convert to Sunday-based: Sunday=0, Monday=1, ..., Saturday=6
                int dayOfWeek = today.getDayOfWeek().getValue();
                int daysFromSunday = dayOfWeek == 7 ? 0 : dayOfWeek; // Sunday=0, Monday=1, ..., Saturday=6
                LocalDate weekStart = today.minusDays(daysFromSunday);
                yield new ResolvedTimeRange(
                    formatDate(weekStart), 
                    formatDate(today), 
                    false
                );
            }
            case "last week" -> {
                // Sunday is the first day of the week (Israel)
                // Calculate last week: from last Sunday to last Saturday
                int dayOfWeek = today.getDayOfWeek().getValue();
                int daysFromSunday = dayOfWeek == 7 ? 0 : dayOfWeek; // Sunday=0, Monday=1, ..., Saturday=6
                LocalDate lastWeekEnd = today.minusDays(daysFromSunday + 1); // Last Saturday
                LocalDate lastWeekStart = lastWeekEnd.minusDays(6); // Last Sunday
                yield new ResolvedTimeRange(
                    formatDate(lastWeekStart), 
                    formatDate(lastWeekEnd), 
                    false
                );
            }
            case "this month" -> {
                LocalDate monthStart = today.withDayOfMonth(1);
                yield new ResolvedTimeRange(
                    formatDate(monthStart), 
                    formatDate(today), 
                    false
                );
            }
            case "last month" -> {
                LocalDate lastMonthEnd = today.withDayOfMonth(1).minusDays(1);
                LocalDate lastMonthStart = lastMonthEnd.withDayOfMonth(1);
                yield new ResolvedTimeRange(
                    formatDate(lastMonthStart), 
                    formatDate(lastMonthEnd), 
                    false
                );
            }
            default -> {
                // Try to parse "last N days/weeks/months" pattern
                ResolvedTimeRange parsed = parseLastNPattern(hint, today);
                if (parsed != null) {
                    yield parsed;
                }
                
                // Try to parse "past N days/weeks/months" pattern
                parsed = parsePastNPattern(hint, today);
                if (parsed != null) {
                    yield parsed;
                }
                
                // Complex or ambiguous - needs LLM
                log.debug("Time range hint '{}' cannot be resolved deterministically, will use LLM", timeRangeHint);
                yield new ResolvedTimeRange(null, null, true);
            }
        };
    }
    
    /**
     * Parses formatted date range like "2024-01-01 to 2024-01-31" or "2024-01-01-2024-01-31".
     */
    private static ResolvedTimeRange parseFormattedDateRange(String hint) {
        // Pattern: "YYYY-MM-DD to YYYY-MM-DD" or "YYYY-MM-DD - YYYY-MM-DD" or "YYYY-MM-DD YYYY-MM-DD"
        Pattern pattern = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2})\\s*(?:to|-)\\s*(\\d{4}-\\d{2}-\\d{2})"
        );
        Matcher matcher = pattern.matcher(hint);
        
        if (matcher.find()) {
            try {
                String fromStr = matcher.group(1);
                String toStr = matcher.group(2);
                LocalDate fromDate = LocalDate.parse(fromStr, DATE_FORMATTER);
                LocalDate toDate = LocalDate.parse(toStr, DATE_FORMATTER);
                
                // Validate: fromDate should be before or equal to toDate
                if (!fromDate.isAfter(toDate)) {
                    return new ResolvedTimeRange(
                        formatDate(fromDate), 
                        formatDate(toDate), 
                        false
                    );
                }
            } catch (DateTimeParseException e) {
                log.debug("Failed to parse formatted date range: {}", hint);
            }
        }
        
        return null;
    }
    
    /**
     * Parses "last N days/weeks/months" pattern.
     */
    private static ResolvedTimeRange parseLastNPattern(String hint, LocalDate today) {
        Pattern pattern = Pattern.compile("last\\s+(\\d+)\\s+(day|days|week|weeks|month|months)");
        Matcher matcher = pattern.matcher(hint);
        
        if (matcher.find()) {
            int count = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2).toLowerCase();
            
            LocalDate fromDate = switch (unit) {
                case "day", "days" -> today.minusDays(count - 1);
                case "week", "weeks" -> today.minusWeeks(count).plusDays(1);
                case "month", "months" -> today.minusMonths(count).plusDays(1);
                default -> null;
            };
            
            if (fromDate != null) {
                return new ResolvedTimeRange(
                    formatDate(fromDate), 
                    formatDate(today), 
                    false
                );
            }
        }
        
        return null;
    }
    
    /**
     * Parses "past N days/weeks/months" pattern.
     */
    private static ResolvedTimeRange parsePastNPattern(String hint, LocalDate today) {
        Pattern pattern = Pattern.compile("past\\s+(\\d+)\\s+(day|days|week|weeks|month|months)");
        Matcher matcher = pattern.matcher(hint);
        
        if (matcher.find()) {
            int count = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2).toLowerCase();
            
            LocalDate fromDate = switch (unit) {
                case "day", "days" -> today.minusDays(count - 1);
                case "week", "weeks" -> today.minusWeeks(count).plusDays(1);
                case "month", "months" -> today.minusMonths(count).plusDays(1);
                default -> null;
            };
            
            if (fromDate != null) {
                return new ResolvedTimeRange(
                    formatDate(fromDate), 
                    formatDate(today), 
                    false
                );
            }
        }
        
        return null;
    }
    
    /**
     * Gets today's date in the specified timezone.
     */
    private static LocalDate getTodayInTimezone(String timezone) {
        if (timezone != null && !timezone.isBlank()) {
            try {
                ZoneId zoneId = ZoneId.of(timezone);
                return LocalDate.now(zoneId);
            } catch (Exception e) {
                log.warn("Invalid timezone '{}', using system default", timezone);
            }
        }
        return LocalDate.now();
    }
    
    /**
     * Formats LocalDate to YYYY-MM-DD string.
     */
    private static String formatDate(LocalDate date) {
        return date.format(DATE_FORMATTER);
    }
    
    /**
     * Gets default time range (start of current month to today).
     * Example: If today is 2025-12-13, returns 2025-12-01 to 2025-12-13.
     */
    public static ResolvedTimeRange getDefaultTimeRange() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        return new ResolvedTimeRange(
            formatDate(monthStart), 
            formatDate(today), 
            false
        );
    }
}
