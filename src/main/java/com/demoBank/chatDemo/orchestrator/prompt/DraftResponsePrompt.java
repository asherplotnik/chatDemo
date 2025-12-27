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
            
            2. tables: CRITICAL - ALWAYS include a LIST of tables when there is ANY data to display. Create SEPARATE tables for EACH account/entity.
               - Create ONE table per account/entity to keep data organized
               - Each table should have accountName field identifying the account (e.g., "Main Account", "Savings Account")
               - If user asks for transactions/list: type="transactions", headers=["Date", "Amount", "Description", "Merchant"], group transactions by account
               - If user asks for balance: type="balance", headers=["Account", "Balance", "Currency"], one table per account
               - If user asks for both transactions AND balance: create separate tables for each account showing both
               - For calculations: type="summary", headers=["Metric", "Value"]
               - Each row must be a complete object with keys matching headers exactly
               - Include metadata: rowCount (total rows), hasTotals (if applicable), totals object
               - Set to null or empty array ONLY when there is absolutely no data
            
            3. dataSource: description (human-readable), api, timeRange, entities
            
            CRITICAL TABLE RULES:
            - Create SEPARATE tables for EACH account/entity - do NOT mix accounts in one table
            - If normalized data contains transactions from multiple accounts: create one table per account
            - If normalized data contains balances from multiple accounts: create one table per account
            - Each table MUST have accountName field set to the account nickname/name
            - NEVER set tables to null or empty if there is data in normalized data
            - Frontend CANNOT render data without tables field - it's essential
            
            CALCULATIONS: For "sum"/"count"/"average"/"max"/"min" metrics, calculate and show in introduction + table.
            
            RULES:
            - Always call the function (required)
            - ALWAYS include tables list when data exists - this is CRITICAL
            - Create SEPARATE tables per account - never mix accounts
            - Format amounts with currency, dates consistently
            - Table headers must match row keys exactly
            - Use only provided data, no fabrication
            - Extract ALL relevant data into table rows, grouped by account
            
            EXAMPLE (Multiple Accounts):
            {
              "introduction": "You have transactions from 2 accounts. Main Account: 3 transactions totaling ₪1,200. Savings Account: 2 transactions totaling ₪800.",
              "tables": [
                {
                  "accountName": "Main Account",
                  "type": "transactions",
                  "headers": ["Date", "Amount", "Description"],
                  "rows": [
                    {"Date": "2025-12-15", "Amount": "₪500.00", "Description": "Payment"},
                    {"Date": "2025-12-10", "Amount": "₪700.00", "Description": "Deposit"}
                  ],
                  "metadata": {"rowCount": 3}
                },
                {
                  "accountName": "Savings Account",
                  "type": "transactions",
                  "headers": ["Date", "Amount", "Description"],
                  "rows": [
                    {"Date": "2025-12-12", "Amount": "₪300.00", "Description": "Transfer"},
                    {"Date": "2025-12-08", "Amount": "₪500.00", "Description": "Deposit"}
                  ],
                  "metadata": {"rowCount": 2}
                }
              ],
              "dataSource": {"description": "Retrieved transactions from current accounts", "api": "current-accounts"}
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
