package com.demoBank.chatDemo.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Utility class for loading JSON files from the classpath.
 * Provides methods to load JSON files as String, JsonNode, typed objects, or lists of objects.
 * Supports JSON files that contain a single object or an array of objects.
 */
public class JsonFileLoader {
    
    private static final Logger log = LoggerFactory.getLogger(JsonFileLoader.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Loads a JSON file from the classpath as a String.
     * 
     * @param resourcePath The path to the JSON file (e.g., "context/APIs/creditCards/creditCardsOutput.json")
     * @return The JSON content as a String
     * @throws IOException if the file cannot be read or doesn't exist
     */
    public static String loadAsString(String resourcePath) throws IOException {
        try (InputStream inputStream = JsonFileLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(inputStream.readAllBytes());
        }
    }
    
    /**
     * Loads a JSON file from the classpath as a JsonNode.
     * 
     * @param resourcePath The path to the JSON file
     * @return The JSON content as a JsonNode
     * @throws IOException if the file cannot be read, doesn't exist, or is not valid JSON
     */
    public static JsonNode loadAsJsonNode(String resourcePath) throws IOException {
        String jsonString = loadAsString(resourcePath);
        return objectMapper.readTree(jsonString);
    }
    
    /**
     * Loads a JSON file from the classpath and deserializes it to the specified type.
     * 
     * @param resourcePath The path to the JSON file
     * @param clazz The class to deserialize the JSON into
     * @param <T> The type to deserialize to
     * @return An instance of the specified type
     * @throws IOException if the file cannot be read, doesn't exist, or cannot be deserialized
     */
    public static <T> T loadAsObject(String resourcePath, Class<T> clazz) throws IOException {
        String jsonString = loadAsString(resourcePath);
        return objectMapper.readValue(jsonString, clazz);
    }
    
    /**
     * Loads a JSON file from the classpath as a JsonNode, returning null if the file doesn't exist.
     * Useful for optional resources.
     * 
     * @param resourcePath The path to the JSON file
     * @return The JSON content as a JsonNode, or null if the file doesn't exist
     */
    public static JsonNode loadAsJsonNodeOrNull(String resourcePath) {
        try {
            return loadAsJsonNode(resourcePath);
        } catch (IOException e) {
            log.warn("Failed to load JSON file from classpath: {}", resourcePath, e);
            return null;
        }
    }
    
    /**
     * Loads a JSON file from the classpath and deserializes it to the specified type,
     * returning null if the file doesn't exist or cannot be parsed.
     * Useful for optional resources.
     * 
     * @param resourcePath The path to the JSON file
     * @param clazz The class to deserialize the JSON into
     * @param <T> The type to deserialize to
     * @return An instance of the specified type, or null if the file doesn't exist or cannot be parsed
     */
    public static <T> T loadAsObjectOrNull(String resourcePath, Class<T> clazz) {
        try {
            return loadAsObject(resourcePath, clazz);
        } catch (IOException e) {
            log.warn("Failed to load JSON file from classpath: {}", resourcePath, e);
            return null;
        }
    }
    
    /**
     * Loads a JSON file containing an array of JSON objects from the classpath
     * and deserializes it to a List of the specified type.
     * 
     * @param resourcePath The path to the JSON file containing a JSON array
     * @param clazz The class to deserialize each JSON object into
     * @param <T> The type of objects in the list
     * @return A List of objects of the specified type
     * @throws IOException if the file cannot be read, doesn't exist, or cannot be deserialized
     */
    public static <T> List<T> loadAsList(String resourcePath, Class<T> clazz) throws IOException {
        String jsonString = loadAsString(resourcePath);
        return objectMapper.readValue(jsonString, 
            objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
    }
    
    /**
     * Loads a JSON file containing an array of JSON objects from the classpath
     * and deserializes it to a List of the specified type, returning null if the file doesn't exist.
     * Useful for optional resources.
     * 
     * @param resourcePath The path to the JSON file containing a JSON array
     * @param clazz The class to deserialize each JSON object into
     * @param <T> The type of objects in the list
     * @return A List of objects of the specified type, or null if the file doesn't exist or cannot be parsed
     */
    public static <T> List<T> loadAsListOrNull(String resourcePath, Class<T> clazz) {
        try {
            return loadAsList(resourcePath, clazz);
        } catch (IOException e) {
            log.warn("Failed to load JSON file from classpath: {}", resourcePath, e);
            return null;
        }
    }
}
