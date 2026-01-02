package com.demoBank.chatDemo.repository;

import com.demoBank.chatDemo.util.JsonFileLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.Map;

@Repository
public class GenericMongoRepository {
    
    private static final Logger log = LoggerFactory.getLogger(GenericMongoRepository.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Map collection names to JSON file paths in resources/data
    private static final Map<String, String> COLLECTION_TO_FILE_MAP = Map.of(
        "creditCards", "data/creditCards.json",
        "securities", "data/securities.json",
        "mortgages", "data/mortgages.json",
        "deposits", "data/deposits.json",
        "loans", "data/loans.json",
        "foreignCurrentAccountTransactions", "data/foreignCurrentAccountTransactions.json",
        "currentAccountTransactions", "data/currentAccountTransactions.json"
    );

    public GenericMongoRepository() {
        // No dependencies needed - loads from classpath resources
    }

    /**
     * Finds a document by ID from a JSON file in the classpath.
     * The JSON file should contain an array of objects, each with an "_id" field.
     * 
     * @param collection The collection name (maps to a JSON file)
     * @param id The ID to search for (matches the "_id" field)
     * @return The matching Document, or null if not found
     */
    public Document findById(String collection, Object id) {
        String filePath = COLLECTION_TO_FILE_MAP.get(collection);
        if (filePath == null) {
            log.warn("Unknown collection: {}", collection);
            return null;
        }

        try {
            // Load JSON array from classpath
            JsonNode jsonArray = JsonFileLoader.loadAsJsonNode(filePath);
            
            if (!jsonArray.isArray()) {
                log.error("JSON file {} does not contain an array", filePath);
                return null;
            }

            // Find the object with matching _id
            String idString = id.toString();
            for (JsonNode jsonObject : jsonArray) {
                JsonNode idNode = jsonObject.get("_id");
                if (idNode != null && idString.equals(idNode.asText())) {
                    // Convert JsonNode to BSON Document
                    return convertJsonNodeToDocument(jsonObject);
                }
            }

            log.debug("No document found with _id: {} in collection: {}", id, collection);
            return null;

        } catch (IOException e) {
            log.error("Failed to load JSON file: {}", filePath, e);
            return null;
        }
    }

    /**
     * Converts a JsonNode to a BSON Document.
     * Recursively converts all nested maps and lists to Documents and Document lists.
     * 
     * @param jsonNode The JsonNode to convert
     * @return A BSON Document
     */
    private Document convertJsonNodeToDocument(JsonNode jsonNode) {
        try {
            // Use Document.parse() which properly handles nested structures
            String jsonString = objectMapper.writeValueAsString(jsonNode);
            return Document.parse(jsonString);
        } catch (Exception e) {
            log.error("Failed to convert JsonNode to Document", e);
            // Fallback: manual recursive conversion
            return convertJsonNodeToDocumentRecursive(jsonNode);
        }
    }
    
    /**
     * Recursively converts a JsonNode to a Document, handling nested objects and arrays.
     * 
     * @param jsonNode The JsonNode to convert
     * @return A BSON Document
     */
    private Document convertJsonNodeToDocumentRecursive(JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isNull()) {
            return null;
        }
        
        if (!jsonNode.isObject()) {
            log.warn("JsonNode is not an object, cannot convert to Document");
            return new Document();
        }
        
        Document doc = new Document();
        jsonNode.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            
            if (value.isObject()) {
                doc.put(key, convertJsonNodeToDocumentRecursive(value));
            } else if (value.isArray()) {
                doc.put(key, convertJsonArrayToList(value));
            } else if (value.isNull()) {
                doc.put(key, null);
            } else if (value.isBoolean()) {
                doc.put(key, value.asBoolean());
            } else if (value.isInt()) {
                doc.put(key, value.asInt());
            } else if (value.isLong()) {
                doc.put(key, value.asLong());
            } else if (value.isDouble()) {
                doc.put(key, value.asDouble());
            } else {
                doc.put(key, value.asText());
            }
        });
        
        return doc;
    }
    
    /**
     * Converts a JSON array to a List, recursively converting nested objects to Documents.
     * 
     * @param jsonArray The JsonNode array to convert
     * @return A List containing Documents and primitives
     */
    private java.util.List<Object> convertJsonArrayToList(JsonNode jsonArray) {
        java.util.List<Object> list = new java.util.ArrayList<>();
        jsonArray.forEach(element -> {
            if (element.isObject()) {
                list.add(convertJsonNodeToDocumentRecursive(element));
            } else if (element.isArray()) {
                list.add(convertJsonArrayToList(element));
            } else if (element.isNull()) {
                list.add(null);
            } else if (element.isBoolean()) {
                list.add(element.asBoolean());
            } else if (element.isInt()) {
                list.add(element.asInt());
            } else if (element.isLong()) {
                list.add(element.asLong());
            } else if (element.isDouble()) {
                list.add(element.asDouble());
            } else {
                list.add(element.asText());
            }
        });
        return list;
    }
}
