package org.example.utils;

import com.github.f4b6a3.uuid.UuidCreator;

import java.util.UUID;

public class UUIDGenerator {

    /**
     * Generate a UUID v7 (time-ordered UUID)
     * @return UUID v7 object
     */
    public static UUID generateUUIDv7() {
        return UuidCreator.getTimeOrderedEpoch();
    }

    /**
     * Validate if a string is a valid UUID
     * @param uuid String to validate
     * @return true if valid UUID format
     */
    public static boolean isValidUUID(String uuid) {
        if (uuid == null) {
            return false;
        }
        try {
            UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Parse a string to UUID
     * @param uuid String to parse
     * @return UUID object or null if invalid
     */
    public static UUID parseUUID(String uuid) {
        if (uuid == null) {
            return null;
        }
        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}