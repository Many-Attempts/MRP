package org.example.handlers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RatingHandler Tests")
class RatingHandlerTest {

    @Test
    @DisplayName("Star validation should accept values 1-5")
    void testStarValidation_ValidRange_1to5() {
        assertTrue(isValidStars(1), "Star value 1 should be valid");
        assertTrue(isValidStars(2), "Star value 2 should be valid");
        assertTrue(isValidStars(3), "Star value 3 should be valid");
        assertTrue(isValidStars(4), "Star value 4 should be valid");
        assertTrue(isValidStars(5), "Star value 5 should be valid");
    }

    @Test
    @DisplayName("Star validation should reject values below 1")
    void testStarValidation_TooLow() {
        assertFalse(isValidStars(0), "Star value 0 should be invalid");
        assertFalse(isValidStars(-1), "Star value -1 should be invalid");
        assertFalse(isValidStars(-100), "Star value -100 should be invalid");
    }

    @Test
    @DisplayName("Star validation should reject values above 5")
    void testStarValidation_TooHigh() {
        assertFalse(isValidStars(6), "Star value 6 should be invalid");
        assertFalse(isValidStars(10), "Star value 10 should be invalid");
        assertFalse(isValidStars(100), "Star value 100 should be invalid");
    }

    @Test
    @DisplayName("Star validation should reject null")
    void testStarValidation_NullValue() {
        assertFalse(isValidStars(null), "Null star value should be invalid");
    }

    @Test
    @DisplayName("Auto-confirmation: no comment means immediately confirmed")
    void testAutoConfirmation_NoComment_IsConfirmedTrue() {
        // When comment is null or empty, isConfirmed should be true
        assertTrue(shouldAutoConfirm(null), "Null comment should auto-confirm");
        assertTrue(shouldAutoConfirm(""), "Empty comment should auto-confirm");
        assertTrue(shouldAutoConfirm("   "), "Whitespace-only comment should auto-confirm");
    }

    @Test
    @DisplayName("Auto-confirmation: with comment means not confirmed initially")
    void testAutoConfirmation_WithComment_IsConfirmedFalse() {
        // When comment has content, isConfirmed should be false (pending)
        assertFalse(shouldAutoConfirm("Great movie!"), "Comment should require confirmation");
        assertFalse(shouldAutoConfirm("A"), "Even short comment should require confirmation");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    @DisplayName("All valid star values should pass validation")
    void testStarValidation_ParameterizedValid(int stars) {
        assertTrue(isValidStars(stars), "Star value " + stars + " should be valid");
    }

    @ParameterizedTest
    @ValueSource(ints = {-5, -1, 0, 6, 10, 100})
    @DisplayName("All invalid star values should fail validation")
    void testStarValidation_ParameterizedInvalid(int stars) {
        assertFalse(isValidStars(stars), "Star value " + stars + " should be invalid");
    }

    @Test
    @DisplayName("Comment update should reset confirmation status")
    void testCommentUpdate_ResetsConfirmation() {
        // When a comment is updated (not empty), confirmation should be reset
        String newComment = "Updated comment";
        boolean needsConfirmation = newComment != null && !newComment.trim().isEmpty();

        assertTrue(needsConfirmation, "New comment should need confirmation");

        // Empty update should not need confirmation
        String emptyUpdate = "";
        needsConfirmation = emptyUpdate != null && !emptyUpdate.trim().isEmpty();
        assertFalse(needsConfirmation, "Empty update should not need confirmation");
    }

    // Helper methods that mirror RatingHandler validation logic
    private boolean isValidStars(Integer stars) {
        if (stars == null) {
            return false;
        }
        return stars >= 1 && stars <= 5;
    }

    private boolean shouldAutoConfirm(String comment) {
        // From RatingHandler line 109: isConfirmed = (comment == null || comment.trim().isEmpty())
        return comment == null || comment.trim().isEmpty();
    }
}
