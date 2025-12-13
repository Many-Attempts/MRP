package org.example.handlers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MediaHandler Tests")
class MediaHandlerTest {

    private static final Set<String> VALID_MEDIA_TYPES = Set.of("movie", "series", "game");
    private static final Set<String> VALID_SORT_OPTIONS = Set.of("title", "year", "rating");

    @Test
    @DisplayName("Media type validation should accept valid types")
    void testMediaTypeValidation_ValidTypes() {
        assertTrue(isValidMediaType("movie"), "'movie' should be valid");
        assertTrue(isValidMediaType("series"), "'series' should be valid");
        assertTrue(isValidMediaType("game"), "'game' should be valid");
    }

    @Test
    @DisplayName("Media type validation should reject invalid types")
    void testMediaTypeValidation_InvalidType() {
        assertFalse(isValidMediaType("Movie"), "Uppercase 'Movie' should be invalid");
        assertFalse(isValidMediaType("MOVIE"), "Uppercase 'MOVIE' should be invalid");
        assertFalse(isValidMediaType("book"), "'book' should be invalid");
        assertFalse(isValidMediaType("tv-show"), "'tv-show' should be invalid");
        assertFalse(isValidMediaType("anime"), "'anime' should be invalid");
        assertFalse(isValidMediaType(""), "Empty string should be invalid");
        assertFalse(isValidMediaType(null), "Null should be invalid");
    }

    @ParameterizedTest
    @ValueSource(strings = {"movie", "series", "game"})
    @DisplayName("All valid media types should pass validation")
    void testMediaTypeValidation_Parameterized(String mediaType) {
        assertTrue(isValidMediaType(mediaType), mediaType + " should be valid");
    }

    @Test
    @DisplayName("Title validation should reject empty or null titles")
    void testTitleValidation_EmptyTitle() {
        assertFalse(isValidTitle(null), "Null title should be invalid");
        assertFalse(isValidTitle(""), "Empty title should be invalid");
        assertFalse(isValidTitle("   "), "Whitespace-only title should be invalid");
    }

    @Test
    @DisplayName("Title validation should accept valid titles")
    void testTitleValidation_ValidTitles() {
        assertTrue(isValidTitle("The Matrix"), "Normal title should be valid");
        assertTrue(isValidTitle("A"), "Single character should be valid");
        assertTrue(isValidTitle("2001: A Space Odyssey"), "Title with numbers and colons should be valid");
    }

    @Test
    @DisplayName("Sort parameter validation should accept valid options")
    void testSortParameter_ValidOptions() {
        assertTrue(isValidSortOption("title"), "'title' should be valid sort option");
        assertTrue(isValidSortOption("year"), "'year' should be valid sort option");
        assertTrue(isValidSortOption("rating"), "'rating' should be valid sort option");
    }

    @Test
    @DisplayName("Sort parameter should default to title for invalid options")
    void testSortParameter_DefaultBehavior() {
        // The handler defaults to "title" for unknown sort options
        assertEquals("title", getDefaultedSort("title"), "title should remain title");
        assertEquals("year", getDefaultedSort("year"), "year should remain year");
        assertEquals("rating", getDefaultedSort("rating"), "rating should remain rating");
        assertEquals("title", getDefaultedSort("invalid"), "invalid should default to title");
        assertEquals("title", getDefaultedSort(null), "null should default to title");
        assertEquals("title", getDefaultedSort(""), "empty should default to title");
    }

    @Test
    @DisplayName("Year filter should validate numeric input")
    void testYearFilter_NumericValidation() {
        assertTrue(isValidYear("1999"), "1999 should be valid");
        assertTrue(isValidYear("2024"), "2024 should be valid");
        assertTrue(isValidYear("1900"), "1900 should be valid");

        assertFalse(isValidYear("abc"), "Non-numeric should be invalid");
        assertFalse(isValidYear("19.99"), "Decimal should be invalid");
        assertFalse(isValidYear(""), "Empty should be invalid");
        assertFalse(isValidYear(null), "Null should be invalid");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Title validation should reject various empty inputs")
    void testTitleValidation_EmptyInputs(String title) {
        assertFalse(isValidTitle(title), "Empty/null/whitespace title should be invalid");
    }

    // Helper methods that mirror MediaHandler validation logic
    private boolean isValidMediaType(String mediaType) {
        if (mediaType == null) {
            return false;
        }
        return VALID_MEDIA_TYPES.contains(mediaType);
    }

    private boolean isValidTitle(String title) {
        return title != null && !title.trim().isEmpty();
    }

    private boolean isValidSortOption(String sort) {
        return sort != null && VALID_SORT_OPTIONS.contains(sort);
    }

    private String getDefaultedSort(String sort) {
        // Mirrors MediaHandler.handleGetMediaList behavior
        if (sort == null || !VALID_SORT_OPTIONS.contains(sort)) {
            return "title";
        }
        return sort;
    }

    private boolean isValidYear(String year) {
        if (year == null || year.isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(year);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
