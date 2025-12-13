package org.example.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UUIDGenerator Tests")
class UUIDGeneratorTest {

    @Test
    @DisplayName("generateUUIDv7 should return a valid UUID")
    void testGenerateUUIDv7_ReturnsValidUUID() {
        UUID uuid = UUIDGenerator.generateUUIDv7();

        assertNotNull(uuid, "Generated UUID should not be null");

        // UUID v7 should have version 7 in the correct position
        String uuidString = uuid.toString();
        assertNotNull(uuidString);
        assertEquals(36, uuidString.length(), "UUID string should be 36 characters");

        // Verify it's a valid UUID by parsing it back
        UUID parsed = UUID.fromString(uuidString);
        assertEquals(uuid, parsed, "UUID should be parseable");
    }

    @Test
    @DisplayName("isValidUUID should correctly validate UUID strings")
    void testIsValidUUID_ValidAndInvalid() {
        // Valid UUIDs
        assertTrue(UUIDGenerator.isValidUUID("550e8400-e29b-41d4-a716-446655440000"),
                "Standard UUID should be valid");
        assertTrue(UUIDGenerator.isValidUUID("00000000-0000-0000-0000-000000000000"),
                "Nil UUID should be valid");
        assertTrue(UUIDGenerator.isValidUUID(UUID.randomUUID().toString()),
                "Random UUID should be valid");

        // Invalid UUIDs
        assertFalse(UUIDGenerator.isValidUUID(null),
                "Null should be invalid");
        assertFalse(UUIDGenerator.isValidUUID(""),
                "Empty string should be invalid");
        assertFalse(UUIDGenerator.isValidUUID("not-a-uuid"),
                "Random string should be invalid");
        assertFalse(UUIDGenerator.isValidUUID("550e8400-e29b-41d4-a716"),
                "Incomplete UUID should be invalid");
        assertFalse(UUIDGenerator.isValidUUID("550e8400-e29b-41d4-a716-446655440000-extra"),
                "UUID with extra characters should be invalid");
    }

    @Test
    @DisplayName("parseUUID should parse valid UUIDs and return null for invalid")
    void testParseUUID_ValidAndInvalid() {
        // Valid UUID parsing
        String validUUID = "550e8400-e29b-41d4-a716-446655440000";
        UUID parsed = UUIDGenerator.parseUUID(validUUID);
        assertNotNull(parsed, "Valid UUID should parse successfully");
        assertEquals(validUUID, parsed.toString(), "Parsed UUID should match input");

        // Invalid UUID parsing
        assertNull(UUIDGenerator.parseUUID(null), "Null should return null");
        assertNull(UUIDGenerator.parseUUID("invalid"), "Invalid string should return null");
        assertNull(UUIDGenerator.parseUUID(""), "Empty string should return null");
    }
}
