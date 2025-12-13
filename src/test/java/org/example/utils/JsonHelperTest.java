package org.example.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonHelper Tests")
class JsonHelperTest {

    @Test
    @DisplayName("parseJson should parse valid JSON to Map")
    void testParseJson_ValidJson_ReturnsMap() throws IOException {
        String json = "{\"username\":\"testuser\",\"password\":\"secret123\"}";

        @SuppressWarnings("unchecked")
        Map<String, Object> result = JsonHelper.parseJson(json, Map.class);

        assertNotNull(result, "Parsed result should not be null");
        assertEquals("testuser", result.get("username"), "Username should match");
        assertEquals("secret123", result.get("password"), "Password should match");
    }

    @Test
    @DisplayName("parseJson should throw exception for invalid JSON")
    void testParseJson_InvalidJson_ThrowsException() {
        String invalidJson = "{ invalid json }";

        assertThrows(IOException.class, () -> {
            JsonHelper.parseJson(invalidJson, Map.class);
        }, "Invalid JSON should throw IOException");
    }

    @Test
    @DisplayName("toJson should convert object to JSON string")
    void testToJson_ObjectToString() {
        Map<String, String> data = Map.of("key", "value", "name", "test");

        String json = JsonHelper.toJson(data);

        assertNotNull(json, "JSON output should not be null");
        assertTrue(json.contains("\"key\""), "JSON should contain key");
        assertTrue(json.contains("\"value\""), "JSON should contain value");
        assertTrue(json.contains("\"name\""), "JSON should contain name");
        assertTrue(json.contains("\"test\""), "JSON should contain test");
    }

    @Test
    @DisplayName("parseQueryParams should parse URL query string")
    void testParseQueryParams_ValidQueryString() {
        String query = "search=matrix&type=movie&year=1999";

        Map<String, String> params = JsonHelper.parseQueryParams(query);

        assertNotNull(params, "Params should not be null");
        assertEquals(3, params.size(), "Should have 3 parameters");
        assertEquals("matrix", params.get("search"), "Search param should match");
        assertEquals("movie", params.get("type"), "Type param should match");
        assertEquals("1999", params.get("year"), "Year param should match");
    }

    @Test
    @DisplayName("parseQueryParams should handle empty and null query strings")
    void testParseQueryParams_EmptyAndNull() {
        // Empty string
        Map<String, String> emptyParams = JsonHelper.parseQueryParams("");
        assertNotNull(emptyParams, "Empty query should return empty map");
        assertTrue(emptyParams.isEmpty(), "Empty query should have no params");

        // Null string
        Map<String, String> nullParams = JsonHelper.parseQueryParams(null);
        assertNotNull(nullParams, "Null query should return empty map");
        assertTrue(nullParams.isEmpty(), "Null query should have no params");
    }

    @Test
    @DisplayName("getPathSegments should split path correctly")
    void testGetPathSegments_ValidPath() {
        String path = "/api/media/123/ratings";

        String[] segments = JsonHelper.getPathSegments(path);

        assertNotNull(segments, "Segments should not be null");
        assertEquals(4, segments.length, "Should have 4 segments");
        assertEquals("api", segments[0], "First segment should be 'api'");
        assertEquals("media", segments[1], "Second segment should be 'media'");
        assertEquals("123", segments[2], "Third segment should be '123'");
        assertEquals("ratings", segments[3], "Fourth segment should be 'ratings'");
    }
}
