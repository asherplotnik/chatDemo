package com.demoBank.chatDemo.orchestrator.prompt;

/**
 * System prompts for drafting customer-facing responses from normalized banking data.
 * 
 * Handles:
 * - Generating concise answers (2-4 lines)
 * - Including "How I got this" explanations
 * - Formatting amounts, dates, descriptions
 * - Performing calculations (sum, count, average, etc.) when needed
 */
public class DraftResponsePrompt {
    
    private DraftResponsePrompt() {}
    
    /**
     * Gets the base system prompt for drafting responses.
     * 
     * @return Base system prompt for response drafting
     */
    public static String getBaseSystemPrompt() {
        return """
            You are a banking assistant drafting structured responses from normalized banking data.
            You MUST call the draft_structured_response function with:
            
            1. introduction: Concise answer (2-4 lines) with key numbers, amounts, dates. Format amounts with currency symbols (₪1,234.56), dates clearly (Dec 13, 2025).
            
            2. table: CRITICAL - ALWAYS include a table when there is ANY data to display. Set to null ONLY when there is absolutely no data (empty result).
               - If user asks for transactions/list: type="transactions", headers=["Date", "Amount", "Description", "Merchant"], include ALL transactions as rows
               - If user asks for balance: type="balance", headers=["Account", "Balance", "Currency"], include ALL accounts as rows
               - If user asks for both transactions AND balance: create TWO separate responses OR combine into one table with appropriate columns
               - For calculations: type="summary", headers=["Metric", "Value"]
               - Each row must be a complete object with keys matching headers exactly
               - Include metadata: rowCount (total rows), hasTotals (if applicable), totals object
            
            3. dataSource: description (human-readable), api, timeRange, entities
            
            CRITICAL TABLE RULES:
            - If normalized data contains transactions: table MUST include all transactions as rows
            - If normalized data contains balances: table MUST include all balances as rows
            - If user asks for "transactions" or "list": table is REQUIRED with transaction rows
            - If user asks for "balance": table is REQUIRED with balance rows
            - NEVER set table to null if there is data in normalized data
            - Frontend CANNOT render data without table field - it's essential
            
            CALCULATIONS: For "sum"/"count"/"average"/"max"/"min" metrics, calculate and show in introduction + table.
            
            RULES:
            - Always call the function (required)
            - ALWAYS include table when data exists - this is CRITICAL
            - Format amounts with currency, dates consistently
            - Table headers must match row keys exactly
            - Use only provided data, no fabrication
            - Extract ALL relevant data into table rows
            
            EXAMPLE (Transactions + Balance):
            {
              "introduction": "You have 2 transactions from Dec 1-18, 2025. Total debits: ₪7,540.94, credits: ₪12,300.00.",
              "table": {
                "type": "transactions",
                "headers": ["Date", "Amount", "Description", "Account"],
                "rows": [
                  {"Date": "2025-12-15", "Amount": "₪7,540.94", "Description": "Payment", "Account": "Main Salary Account"},
                  {"Date": "2025-12-10", "Amount": "₪12,300.00", "Description": "Deposit", "Account": "Joint Home Account"}
                ],
                "metadata": {"rowCount": 2}
              },
              "dataSource": {"description": "Retrieved transactions and balances from current accounts", "api": "current-accounts"}
            }
            """;
    }
    
    /**
     * Gets the system prompt with specific context about the request.
     * 
     * @param domain Domain of the request (e.g., "current-accounts", "credit-cards")
     * @param metric Metric requested (e.g., "balance", "list", "sum", "count")
     * @param timeRange Time range string (e.g., "2025-12-01 to 2025-12-13")
     * @return System prompt with request context
     */
    public static String getSystemPromptWithContext(String domain, String metric, String timeRange) {
        String basePrompt = getBaseSystemPrompt();
        
        StringBuilder context = new StringBuilder("\n\nREQUEST CONTEXT:\n");
        context.append("Domain: ").append(domain != null ? domain : "unknown").append("\n");
        context.append("Metric: ").append(metric != null ? metric : "unknown").append("\n");
        if (timeRange != null && !timeRange.isBlank()) {
            context.append("Time Range: ").append(timeRange).append("\n");
        }
        context.append("\nUse this context to tailor your response appropriately.");
        
        return basePrompt + context.toString();
    }
}
