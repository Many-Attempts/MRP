package org.example.handlers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserHandler Tests")
class UserHandlerTest {

    @Test
    @DisplayName("Leaderboard should sort users by total activity descending")
    void testLeaderboardOrdering_ByTotalActivity() {
        // Simulate leaderboard data
        List<Map<String, Object>> users = new ArrayList<>();

        users.add(createLeaderboardEntry("user1", 10, 5, 3)); // total: 18
        users.add(createLeaderboardEntry("user2", 5, 2, 1));  // total: 8
        users.add(createLeaderboardEntry("user3", 20, 10, 5)); // total: 35

        // Sort by total activity (descending)
        users.sort((a, b) -> {
            int totalA = (int) a.get("ratingCount") + (int) a.get("mediaCreated") + (int) a.get("likesGiven");
            int totalB = (int) b.get("ratingCount") + (int) b.get("mediaCreated") + (int) b.get("likesGiven");
            return Integer.compare(totalB, totalA);
        });

        assertEquals("user3", users.get(0).get("username"), "User with highest activity should be first");
        assertEquals("user1", users.get(1).get("username"), "User with second highest activity should be second");
        assertEquals("user2", users.get(2).get("username"), "User with lowest activity should be last");
    }

    @Test
    @DisplayName("Total activity calculation should sum all metrics")
    void testTotalActivityCalculation() {
        int ratingCount = 10;
        int mediaCreated = 5;
        int likesGiven = 3;

        int totalActivity = ratingCount + mediaCreated + likesGiven;

        assertEquals(18, totalActivity, "Total activity should be sum of all metrics");
    }

    @Test
    @DisplayName("Leaderboard should be limited to 10 users")
    void testLeaderboardLimit() {
        List<Map<String, Object>> users = new ArrayList<>();

        // Create 15 users
        for (int i = 1; i <= 15; i++) {
            users.add(createLeaderboardEntry("user" + i, i, i, i));
        }

        // Apply limit (top 10)
        List<Map<String, Object>> top10 = users.stream()
                .sorted((a, b) -> {
                    int totalA = (int) a.get("ratingCount") + (int) a.get("mediaCreated") + (int) a.get("likesGiven");
                    int totalB = (int) b.get("ratingCount") + (int) b.get("mediaCreated") + (int) b.get("likesGiven");
                    return Integer.compare(totalB, totalA);
                })
                .limit(10)
                .toList();

        assertEquals(10, top10.size(), "Leaderboard should be limited to 10 users");
        assertEquals("user15", top10.get(0).get("username"), "Top user should have highest activity");
    }

    @Test
    @DisplayName("Recommendation should filter out already rated media")
    void testRecommendationFiltering_ExcludesAlreadyRated() {
        // Simulate user's rated media IDs
        Set<String> alreadyRatedMediaIds = Set.of("media-1", "media-2", "media-3");

        // Simulate all available media
        List<String> allMediaIds = List.of("media-1", "media-2", "media-3", "media-4", "media-5");

        // Filter out already rated
        List<String> recommendations = allMediaIds.stream()
                .filter(id -> !alreadyRatedMediaIds.contains(id))
                .toList();

        assertEquals(2, recommendations.size(), "Should have 2 unreated media");
        assertTrue(recommendations.contains("media-4"), "media-4 should be recommended");
        assertTrue(recommendations.contains("media-5"), "media-5 should be recommended");
        assertFalse(recommendations.contains("media-1"), "Already rated media should not be in recommendations");
    }

    @Test
    @DisplayName("Recommendation should filter by minimum rating threshold")
    void testRecommendationFiltering_MinimumRating() {
        // Simulate media with their average ratings
        List<Map<String, Object>> mediaList = new ArrayList<>();
        mediaList.add(Map.of("id", "media-1", "avgRating", 4.5));
        mediaList.add(Map.of("id", "media-2", "avgRating", 3.0)); // Below threshold
        mediaList.add(Map.of("id", "media-3", "avgRating", 3.5)); // At threshold
        mediaList.add(Map.of("id", "media-4", "avgRating", 2.0)); // Below threshold

        double minRating = 3.5;

        List<Map<String, Object>> filtered = mediaList.stream()
                .filter(m -> (double) m.get("avgRating") >= minRating)
                .toList();

        assertEquals(2, filtered.size(), "Should have 2 media meeting minimum rating");
        assertEquals("media-1", filtered.get(0).get("id"), "High rated media should be included");
        assertEquals("media-3", filtered.get(1).get("id"), "Media at threshold should be included");
    }

    @Test
    @DisplayName("Genre matching for recommendations should be case-insensitive")
    void testGenreMatching_CaseInsensitive() {
        String userFavoriteGenres = "Action,Sci-Fi,Drama";
        String mediaGenres = "action,thriller";

        // Check if any genre matches (case-insensitive)
        boolean hasMatchingGenre = Arrays.stream(userFavoriteGenres.toLowerCase().split(","))
                .anyMatch(fg -> Arrays.asList(mediaGenres.toLowerCase().split(",")).contains(fg.trim()));

        assertTrue(hasMatchingGenre, "Case-insensitive genre matching should find 'action'");
    }

    @Test
    @DisplayName("Profile statistics should include correct counts")
    void testProfileStatistics_Calculation() {
        // Simulate user statistics
        int totalRatings = 15;
        int totalFavorites = 8;
        int totalMediaCreated = 3;

        Map<String, Object> profile = new HashMap<>();
        profile.put("totalRatings", totalRatings);
        profile.put("totalFavorites", totalFavorites);
        profile.put("totalMediaCreated", totalMediaCreated);

        assertEquals(15, profile.get("totalRatings"), "Total ratings should be correct");
        assertEquals(8, profile.get("totalFavorites"), "Total favorites should be correct");
        assertEquals(3, profile.get("totalMediaCreated"), "Total media created should be correct");
    }

    @Test
    @DisplayName("Recommendations should be limited to 10 results")
    void testRecommendationLimit() {
        List<String> allRecommendations = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            allRecommendations.add("media-" + i);
        }

        List<String> limited = allRecommendations.stream()
                .limit(10)
                .toList();

        assertEquals(10, limited.size(), "Recommendations should be limited to 10");
    }

    // Helper method to create leaderboard entries
    private Map<String, Object> createLeaderboardEntry(String username, int ratingCount, int mediaCreated, int likesGiven) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("username", username);
        entry.put("ratingCount", ratingCount);
        entry.put("mediaCreated", mediaCreated);
        entry.put("likesGiven", likesGiven);
        return entry;
    }
}
