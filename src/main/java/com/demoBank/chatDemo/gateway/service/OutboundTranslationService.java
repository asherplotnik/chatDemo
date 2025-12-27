package com.demoBank.chatDemo.gateway.service;

import com.demoBank.chatDemo.gateway.dto.ChatResponse;
import com.demoBank.chatDemo.gateway.model.ChatSessionContext;
import com.demoBank.chatDemo.orchestrator.dto.DraftResponseDTO;
import com.demoBank.chatDemo.translation.service.OutboundTranslator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for translating ChatResponse from English to Hebrew.
 * Translates only user-facing text values, preserves technical fields and data.
 * Makes a SINGLE API call with all translatable values.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboundTranslationService {
    
    private final OutboundTranslator outboundTranslator;
    private final ObjectMapper objectMapper;
    
    /**
     * Translates ChatResponse to Hebrew if original message was Hebrew.
     * Makes a SINGLE API call with all translatable values.
     * 
     * @param response English response
     * @param session Session context with language information
     * @param correlationId Correlation ID for logging
     * @return Translated response (Hebrew if original was Hebrew, otherwise unchanged)
     */
    public ChatResponse translateResponseToHebrew(ChatResponse response, ChatSessionContext session, String correlationId) {
        // Check if original message was in Hebrew
        if (session == null || !"he".equals(session.getLanguageCode())) {
            log.debug("No translation needed - correlationId: {}, language: {}", 
                    correlationId, session != null ? session.getLanguageCode() : "unknown");
            return response; // No translation needed
        }
        
        log.info("Translating ChatResponse to Hebrew in single API call - correlationId: {}", correlationId);
        
        try {
            // Extract all translatable values into a structured format
            TranslationExtractionResult extraction = extractTranslatableValues(response);
            Map<String, String> translatableValues = extraction.translatableValues;
            Map<String, String> balanceParts = extraction.balanceParts;
            
            if (translatableValues.isEmpty()) {
                log.debug("No translatable values found - correlationId: {}", correlationId);
                return response;
            }
            
            // Make single API call to translate all values
            Map<String, String> translations = translateAllValues(translatableValues, correlationId);
            
            // Apply translations back to ChatResponse (including balance parts)
            return applyTranslations(response, translations, balanceParts);
            
        } catch (Exception e) {
            log.error("Error translating response to Hebrew - correlationId: {}, error: {}", 
                    correlationId, e.getMessage(), e);
            return response; // Return original if translation fails
        }
    }
    
    /**
     * Extracts all translatable text values from ChatResponse into a map with unique keys.
     * Also stores balance parts separately (not for translation, just for reconstruction).
     * 
     * @param response ChatResponse to extract values from
     * @return Pair of (translatable values map, balance parts map)
     */
    private TranslationExtractionResult extractTranslatableValues(ChatResponse response) {
        Map<String, String> values = new HashMap<>();
        Map<String, String> balanceParts = new HashMap<>(); // Store balance parts separately
        
        // Extract answer
        if (response.getAnswer() != null && !response.getAnswer().isBlank()) {
            values.put("ANSWER", response.getAnswer());
            log.debug("Extracted ANSWER for translation: {}", response.getAnswer().substring(0, Math.min(50, response.getAnswer().length())));
        }
        
        // Extract explanation (user-facing text that should be translated)
        if (response.getExplanation() != null && !response.getExplanation().isBlank()) {
            values.put("EXPLANATION", response.getExplanation());
            log.debug("Extracted EXPLANATION for translation: {}", response.getExplanation().substring(0, Math.min(50, response.getExplanation().length())));
        }
        
        // Extract table values
        if (response.getTables() != null && !response.getTables().isEmpty()) {
            for (int tableIdx = 0; tableIdx < response.getTables().size(); tableIdx++) {
                DraftResponseDTO.TableData table = response.getTables().get(tableIdx);
                String tablePrefix = "TABLE_" + tableIdx;
                
                // Extract account name (extract only name part, preserve balance separately)
                if (table.getAccountName() != null && !table.getAccountName().isBlank()) {
                    String accountName = table.getAccountName();
                    // Check if accountName contains balance in parentheses: "Account Name (₪1,234.56)"
                    int balanceStart = accountName.indexOf(" (");
                    if (balanceStart > 0 && accountName.endsWith(")")) {
                        // Extract only the name part (before balance) for translation
                        String namePart = accountName.substring(0, balanceStart);
                        values.put(tablePrefix + "_ACCOUNT_NAME", namePart);
                        // Store balance part separately (NOT for translation, just for reconstruction)
                        String balancePart = accountName.substring(balanceStart);
                        balanceParts.put(tablePrefix + "_ACCOUNT_BALANCE", balancePart);
                    } else {
                        // No balance, extract entire account name
                        values.put(tablePrefix + "_ACCOUNT_NAME", accountName);
                    }
                }
                
                // Extract headers
                if (table.getHeaders() != null) {
                    for (int headerIdx = 0; headerIdx < table.getHeaders().size(); headerIdx++) {
                        String header = table.getHeaders().get(headerIdx);
                        if (header != null && !header.isBlank() && !shouldPreserveAsIs(header)) {
                            values.put(tablePrefix + "_HEADER_" + headerIdx, header);
                        }
                    }
                }
                
                // Extract row values
                if (table.getRows() != null) {
                    for (int rowIdx = 0; rowIdx < table.getRows().size(); rowIdx++) {
                        Map<String, Object> row = table.getRows().get(rowIdx);
                        for (Map.Entry<String, Object> entry : row.entrySet()) {
                            Object value = entry.getValue();
                            if (value instanceof String) {
                                String strValue = (String) value;
                                if (!shouldPreserveAsIs(strValue)) {
                                    String key = tablePrefix + "_ROW_" + rowIdx + "_" + entry.getKey();
                                    values.put(key, strValue);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return new TranslationExtractionResult(values, balanceParts);
    }
    
    /**
     * Result class for extraction - contains translatable values and balance parts.
     */
    private static class TranslationExtractionResult {
        final Map<String, String> translatableValues;
        final Map<String, String> balanceParts;
        
        TranslationExtractionResult(Map<String, String> translatableValues, Map<String, String> balanceParts) {
            this.translatableValues = translatableValues;
            this.balanceParts = balanceParts;
        }
    }
    
    /**
     * Translates all values in a single API call.
     * Sends structured JSON and expects JSON response with same keys.
     * 
     * @param values Map of key -> English text to translate
     * @param correlationId Correlation ID for logging
     * @return Map of key -> Hebrew translation
     */
    private Map<String, String> translateAllValues(Map<String, String> values, String correlationId) {
        try {
            // Build structured JSON for translation
            String jsonInput = buildTranslationJson(values);
            
            log.info("Translation input JSON - correlationId: {}, size: {} values (including ANSWER and EXPLANATION)", 
                    correlationId, values.size());
            log.debug("Translation input JSON content - correlationId: {}, json: {}", correlationId, jsonInput);
            
            // Call translator with JSON
            String jsonOutput = outboundTranslator.translateJsonToHebrew(jsonInput, correlationId);
            
            // Parse JSON response
            return parseTranslationJson(jsonOutput, values.keySet());
            
        } catch (Exception e) {
            log.error("Error in batch translation - correlationId: {}, error: {}", correlationId, e.getMessage(), e);
            return new HashMap<>(); // Return empty map on error
        }
    }
    
    /**
     * Builds JSON string from values map for translation.
     * 
     * @param values Map of key -> value
     * @return JSON string
     */
    private String buildTranslationJson(Map<String, String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            log.error("Error building translation JSON: {}", e.getMessage(), e);
            // Fallback to simple format
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            boolean first = true;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (!first) sb.append(",\n");
                sb.append("  \"").append(entry.getKey()).append("\": \"").append(escapeJson(entry.getValue())).append("\"");
                first = false;
            }
            sb.append("\n}");
            return sb.toString();
        }
    }
    
    /**
     * Escapes JSON string value.
     */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
    
    /**
     * Parses JSON response from translator.
     * 
     * @param jsonOutput JSON string from translator
     * @param expectedKeys Set of expected keys
     * @return Map of key -> translated value
     */
    private Map<String, String> parseTranslationJson(String jsonOutput, java.util.Set<String> expectedKeys) {
        Map<String, String> translations = new HashMap<>();
        
        if (jsonOutput == null || jsonOutput.isBlank()) {
            log.warn("Empty JSON response from translator");
            return translations;
        }
        
        try {
            // Try to parse as JSON
            JsonNode root = objectMapper.readTree(jsonOutput);
            
            if (root.isObject()) {
                for (String key : expectedKeys) {
                    JsonNode node = root.get(key);
                    if (node != null && node.isTextual()) {
                        translations.put(key, node.asText());
                    }
                }
            }
            
            log.debug("Parsed {} translations from JSON response", translations.size());
            
        } catch (Exception e) {
            log.warn("Failed to parse JSON response, trying fallback parsing - error: {}", e.getMessage());
            
            // Fallback: try to extract key-value pairs from text
            translations = parseTranslationTextFallback(jsonOutput, expectedKeys);
        }
        
        return translations;
    }
    
    /**
     * Fallback parser for non-JSON responses.
     */
    private Map<String, String> parseTranslationTextFallback(String text, java.util.Set<String> expectedKeys) {
        Map<String, String> translations = new HashMap<>();
        
        // Try to find key-value pairs in format: "KEY": "value" or KEY: value
        for (String key : expectedKeys) {
            // Look for pattern: "KEY": "value" or KEY: "value"
            String pattern1 = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
            String pattern2 = key + "\\s*:\\s*\"([^\"]+)\"";
            String pattern3 = key + "\\s*:\\s*([^,\\n}]+)";
            
            java.util.regex.Pattern p1 = java.util.regex.Pattern.compile(pattern1);
            java.util.regex.Pattern p2 = java.util.regex.Pattern.compile(pattern2);
            java.util.regex.Pattern p3 = java.util.regex.Pattern.compile(pattern3);
            
            java.util.regex.Matcher m = p1.matcher(text);
            if (!m.find()) {
                m = p2.matcher(text);
            }
            if (!m.find()) {
                m = p3.matcher(text);
            }
            
            if (m.find()) {
                translations.put(key, m.group(1).trim());
            }
        }
        
        return translations;
    }
    
    /**
     * Applies translations back to ChatResponse structure.
     * 
     * @param response Original response
     * @param translations Map of key -> translated value
     * @param balanceParts Map of table prefix -> balance part (for account name reconstruction)
     * @return Translated response
     */
    private ChatResponse applyTranslations(ChatResponse response, Map<String, String> translations, Map<String, String> balanceParts) {
        ChatResponse.ChatResponseBuilder builder = ChatResponse.builder()
                .correlationId(response.getCorrelationId()); // Keep correlationId in English (technical)
        
        // Apply answer translation
        String translatedAnswer = translations.getOrDefault("ANSWER", response.getAnswer());
        if (translations.containsKey("ANSWER")) {
            log.debug("Applied ANSWER translation: {} -> {}", 
                    response.getAnswer() != null ? response.getAnswer().substring(0, Math.min(50, response.getAnswer().length())) : "null",
                    translatedAnswer.substring(0, Math.min(50, translatedAnswer.length())));
        }
        builder.answer(translatedAnswer);
        
        // Apply explanation translation (user-facing "How I got this" explanation)
        String translatedExplanation = translations.getOrDefault("EXPLANATION", response.getExplanation());
        if (translations.containsKey("EXPLANATION")) {
            log.debug("Applied EXPLANATION translation: {} -> {}", 
                    response.getExplanation() != null ? response.getExplanation().substring(0, Math.min(50, response.getExplanation().length())) : "null",
                    translatedExplanation.substring(0, Math.min(50, translatedExplanation.length())));
        }
        builder.explanation(translatedExplanation);
        
        // Apply table translations
        if (response.getTables() != null && !response.getTables().isEmpty()) {
            List<DraftResponseDTO.TableData> translatedTables = applyTableTranslations(
                    response.getTables(), translations, balanceParts);
            builder.tables(translatedTables);
        } else {
            builder.tables(response.getTables());
        }
        
        return builder.build();
    }
    
    /**
     * Applies translations to table data.
     */
    private List<DraftResponseDTO.TableData> applyTableTranslations(
            List<DraftResponseDTO.TableData> tables,
            Map<String, String> translations,
            Map<String, String> balanceParts) {
        
        List<DraftResponseDTO.TableData> translatedTables = new ArrayList<>();
        
        for (int tableIdx = 0; tableIdx < tables.size(); tableIdx++) {
            DraftResponseDTO.TableData table = tables.get(tableIdx);
            String tablePrefix = "TABLE_" + tableIdx;
            
            // Translate account name (preserve balance part from balanceParts map)
            String accountName = table.getAccountName();
            String translatedAccountName = translateAccountNameWithBalance(
                    accountName, 
                    translations.get(tablePrefix + "_ACCOUNT_NAME"),
                    balanceParts.get(tablePrefix + "_ACCOUNT_BALANCE"));
            
            // Translate headers
            List<String> translatedHeaders = new ArrayList<>();
            if (table.getHeaders() != null) {
                for (int headerIdx = 0; headerIdx < table.getHeaders().size(); headerIdx++) {
                    String header = table.getHeaders().get(headerIdx);
                    String key = tablePrefix + "_HEADER_" + headerIdx;
                    if (translations.containsKey(key)) {
                        translatedHeaders.add(translations.get(key));
                    } else {
                        translatedHeaders.add(header); // Keep original if no translation
                    }
                }
            }
            
            // Translate rows
            List<Map<String, Object>> translatedRows = new ArrayList<>();
            if (table.getRows() != null) {
                for (int rowIdx = 0; rowIdx < table.getRows().size(); rowIdx++) {
                    Map<String, Object> row = table.getRows().get(rowIdx);
                    Map<String, Object> translatedRow = new HashMap<>();
                    
                    for (Map.Entry<String, Object> entry : row.entrySet()) {
                        Object value = entry.getValue();
                        if (value instanceof String) {
                            String strValue = (String) value;
                            if (shouldPreserveAsIs(strValue)) {
                                translatedRow.put(entry.getKey(), value);
                            } else {
                                String key = tablePrefix + "_ROW_" + rowIdx + "_" + entry.getKey();
                                translatedRow.put(entry.getKey(), 
                                        translations.getOrDefault(key, strValue));
                            }
                        } else {
                            translatedRow.put(entry.getKey(), value);
                        }
                    }
                    
                    translatedRows.add(translatedRow);
                }
            }
            
            DraftResponseDTO.TableData translatedTable = DraftResponseDTO.TableData.builder()
                    .accountName(translatedAccountName)
                    .type(table.getType()) // Keep type in English (technical)
                    .headers(translatedHeaders)
                    .rows(translatedRows)
                    .metadata(table.getMetadata()) // Keep metadata in English (technical)
                    .build();
            
            translatedTables.add(translatedTable);
        }
        
        return translatedTables;
    }
    
    /**
     * Translates account name while preserving balance in parentheses.
     */
    private String translateAccountNameWithBalance(String original, String translatedName, String balancePart) {
        if (original == null || original.isBlank()) {
            return original;
        }
        
        // If we have a balance part stored separately, use it
        if (balancePart != null && !balancePart.isBlank()) {
            String name = (translatedName != null && !translatedName.isBlank()) ? translatedName : original;
            // Extract name part from original if translation failed
            if (translatedName == null || translatedName.isBlank()) {
                int balanceStart = original.indexOf(" (");
                if (balanceStart > 0) {
                    name = original.substring(0, balanceStart);
                }
            }
            return name + balancePart;
        }
        
        // No balance part, just return translated name or original
        if (translatedName != null && !translatedName.isBlank()) {
            return translatedName;
        }
        
        return original;
    }
    
    /**
     * Checks if a string value should be preserved as-is (not translated).
     */
    private boolean shouldPreserveAsIs(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        
        // Check for currency symbols with amounts
        if (value.matches(".*[₪$€£]\\s*[\\d,]+(\\.[\\d]+)?.*")) {
            return true;
        }
        
        // Check for ISO date format (YYYY-MM-DD)
        if (value.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
            return true;
        }
        
        // Check for formatted dates (MM/DD/YYYY, DD/MM/YYYY)
        if (value.matches("\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}.*")) {
            return true;
        }
        
        // Check for pure numbers (with commas, decimals)
        if (value.matches("^[\\d,]+(\\.[\\d]+)?$")) {
            return true;
        }
        
        // Check for UUIDs or long alphanumeric IDs
        if (value.matches("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$") ||
            value.matches("^[a-zA-Z0-9]{20,}$")) {
            return true;
        }
        
        // Check for masked account/card numbers
        if (value.matches(".*\\*{4,}.*\\d+.*")) {
            return true;
        }
        
        return false;
    }
}
