package com.demoBank.chatDemo.orchestrator.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Function definition for intent extraction.
 * Defines the structured function schema for function calling.
 */
@Data
@Builder
public class IntentFunctionDefinition {
    
    public static Map<String, Object> getFunctionSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "intents", Map.of(
                    "type", "array",
                    "description", "List of extracted intents. Can contain multiple intents for queries requiring multiple domains.",
                    "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "domain", Map.of(
                                "type", "string",
                                "description", "Domain: current accounts, foreign current accounts, credit cards, loans, mortgages, deposits, securities, or UNKNOWN for non-banking conversational messages",
                                "enum", List.of("current-accounts", "foreign-current-accounts", "credit-cards", "loans", "mortgages", "deposits", "securities", "UNKNOWN")
                            ),
                            "metric", Map.of(
                                "type", "string",
                                "description", "Metric: balance, count, sum, max, min, average, list",
                                "enum", List.of("balance", "count", "sum", "max", "min", "average", "list")
                            ),
                            "timeRangeHint", Map.of(
                                "type", List.of("string", "null"),
                                "description", "Relative time expression hint (e.g., 'last week', 'yesterday', 'this month'). Not absolute dates."
                            ),
                            "entityHints", Map.of(
                                "type", List.of("object", "null"),
                                "description", "Entity references (account IDs, card IDs, etc.)",
                                "properties", Map.of(
                                    "accountIds", Map.of(
                                        "type", List.of("array", "null"),
                                        "items", Map.of("type", "string"),
                                        "description", "List of account references ONLY if explicitly mentioned by the user in the current message (e.g., masked account numbers like '****1234'). Do NOT infer or invent account IDs. Set to null if no account is mentioned."
                                    ),
                                    "cardIds", Map.of(
                                        "type", List.of("array", "null"),
                                        "items", Map.of("type", "string"),
                                        "description", "List of card references ONLY if explicitly mentioned by the user in the current message (e.g., masked card numbers). Do NOT infer or invent card IDs. Set to null if no card is mentioned."
                                    ),
                                    "otherEntities", Map.of(
                                        "type", List.of("object", "null"),
                                        "description", "Other entity references ONLY if explicitly mentioned by the user in the current message (loan IDs, deposit IDs, etc.). Do NOT infer or invent entity IDs. Set to null if no other entities are mentioned.",
                                        "additionalProperties", Map.of("type", "array", "items", Map.of("type", "string"))
                                    )
                                )
                            ),
                            "parameters", Map.of(
                                "type", List.of("object", "null"),
                                "description", "Additional intent parameters as key-value pairs",
                                "additionalProperties", true
                            )
                        ),
                        "required", List.of("domain", "metric")
                    )
                ),
                "usedDefault", Map.of(
                    "type", "boolean",
                    "description", "Whether defaults were applied due to unsatisfactory clarification answer"
                ),
                "defaultReason", Map.of(
                    "type", List.of("string", "null"),
                    "description", "Reason for applying defaults, if applicable"
                ),
                "confidence", Map.of(
                    "type", "number",
                    "description", "Confidence level of the extraction from 0.0 to 1.0",
                    "minimum", 0.0,
                    "maximum", 1.0
                ),
                "needsClarification", Map.of(
                    "type", "boolean",
                    "description", "Whether clarification is needed due to ambiguous intent"
                ),
                "clarificationNeeded", Map.of(
                    "type", List.of("string", "null"),
                    "description", "What needs clarification (e.g., 'domain', 'metric', 'time_range', 'account_selection')"
                )
            ),
            "required", List.of("intents", "confidence")
        );
    }
    
    public static final String FUNCTION_NAME = "extract_intent";
    public static final String FUNCTION_DESCRIPTION = """
        Extracts structured intent from customer messages.
        Returns a list of intents (can be multiple for queries requiring multiple domains).
        Each intent contains domain, metric, time range hints, and entity hints.
        """;
}
