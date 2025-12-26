package org.example.services;

import org.example.models.MediaEntry;
import org.example.models.UserPreferences;
import org.example.repositories.RecommendationRepository;

import java.sql.SQLException;
import java.util.*;

public class RecommendationService {
    private final RecommendationRepository recommendationRepository = new RecommendationRepository();

    private static final int TOP_GENRES_COUNT = 5;
    private static final int TOP_TYPES_COUNT = 2;
    private static final int TOP_AGES_COUNT = 2;
    private static final double MIN_RATING_THRESHOLD = 3.5;
    private static final int RECOMMENDATION_LIMIT = 10;

    public Map<String, Object> getRecommendations(UUID userId) throws SQLException {
        UserPreferences preferences = recommendationRepository.findUserPreferences(
            userId, TOP_GENRES_COUNT, TOP_TYPES_COUNT, TOP_AGES_COUNT
        );

        List<MediaEntry> recommendations = new ArrayList<>();
        if (!preferences.isEmpty()) {
            recommendations = recommendationRepository.findRecommendedMedia(
                userId,
                preferences.getGenres(),
                preferences.getMediaTypes(),
                preferences.getAgeRestrictions(),
                MIN_RATING_THRESHOLD,
                RECOMMENDATION_LIMIT
            );
        }

        Map<String, Object> result = new HashMap<>();
        result.put("recommendations", recommendations);
        result.put("basedOnGenres", preferences.getGenres() != null ? preferences.getGenres() : new ArrayList<>());
        result.put("basedOnTypes", preferences.getMediaTypes() != null ? preferences.getMediaTypes() : new ArrayList<>());
        result.put("basedOnAgeRestrictions", preferences.getAgeRestrictions() != null ? preferences.getAgeRestrictions() : new ArrayList<>());

        return result;
    }
}
