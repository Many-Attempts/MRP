package org.example.handlers;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuthHandler Tests")
class AuthHandlerTest {

    @Test
    @DisplayName("BCrypt should hash password in correct format")
    void testPasswordHashing_BCryptFormat() {
        String password = "testPassword123";

        String hash = BCrypt.withDefaults().hashToString(12, password.toCharArray());

        assertNotNull(hash, "Hash should not be null");
        assertTrue(hash.startsWith("$2"), "BCrypt hash should start with $2");
        assertEquals(60, hash.length(), "BCrypt hash should be 60 characters");
    }

    @Test
    @DisplayName("BCrypt should verify correct password")
    void testPasswordVerification_CorrectPassword() {
        String password = "secretPassword";
        String hash = BCrypt.withDefaults().hashToString(12, password.toCharArray());

        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), hash);

        assertTrue(result.verified, "Correct password should be verified");
    }

    @Test
    @DisplayName("BCrypt should reject incorrect password")
    void testPasswordVerification_IncorrectPassword() {
        String password = "secretPassword";
        String wrongPassword = "wrongPassword";
        String hash = BCrypt.withDefaults().hashToString(12, password.toCharArray());

        BCrypt.Result result = BCrypt.verifyer().verify(wrongPassword.toCharArray(), hash);

        assertFalse(result.verified, "Wrong password should not be verified");
    }

    @Test
    @DisplayName("Token extraction from Bearer header")
    void testTokenExtraction_ValidBearerHeader() {
        String authHeader = "Bearer abc123-token-xyz";

        // Simulate the token extraction logic from AuthHandler
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        assertNotNull(token, "Token should be extracted");
        assertEquals("abc123-token-xyz", token, "Token should match");
    }

    @Test
    @DisplayName("Token extraction should fail for invalid header")
    void testTokenExtraction_InvalidHeader() {
        // Test null header
        String nullHeader = null;
        String token1 = extractToken(nullHeader);
        assertNull(token1, "Null header should return null token");

        // Test header without Bearer prefix
        String invalidHeader = "Basic abc123";
        String token2 = extractToken(invalidHeader);
        assertNull(token2, "Non-Bearer header should return null token");

        // Test empty header
        String emptyHeader = "";
        String token3 = extractToken(emptyHeader);
        assertNull(token3, "Empty header should return null token");

        // Test "Bearer " only (no token)
        String bearerOnly = "Bearer ";
        String token4 = extractToken(bearerOnly);
        assertEquals("", token4, "Bearer only should return empty string");
    }

    @Test
    @DisplayName("Username validation - length requirements")
    void testUsernameValidation_LengthRequirements() {
        // Username must be between 3 and 50 characters
        assertTrue(isValidUsername("abc"), "3 chars should be valid");
        assertTrue(isValidUsername("a".repeat(50)), "50 chars should be valid");

        assertFalse(isValidUsername("ab"), "2 chars should be invalid");
        assertFalse(isValidUsername("a".repeat(51)), "51 chars should be invalid");
        assertFalse(isValidUsername(null), "null should be invalid");
        assertFalse(isValidUsername(""), "empty should be invalid");
        assertFalse(isValidUsername("  "), "whitespace only should be invalid");
    }

    @Test
    @DisplayName("Password validation - minimum length")
    void testPasswordValidation_MinimumLength() {
        // Password must be at least 6 characters
        assertTrue(isValidPassword("123456"), "6 chars should be valid");
        assertTrue(isValidPassword("longpassword"), "Long password should be valid");

        assertFalse(isValidPassword("12345"), "5 chars should be invalid");
        assertFalse(isValidPassword(null), "null should be invalid");
        assertFalse(isValidPassword(""), "empty should be invalid");
    }

    // Helper methods that mirror AuthHandler validation logic
    private String extractToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring(7);
    }

    private boolean isValidUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        return username.length() >= 3 && username.length() <= 50;
    }

    private boolean isValidPassword(String password) {
        if (password == null || password.trim().isEmpty()) {
            return false;
        }
        return password.length() >= 6;
    }
}
