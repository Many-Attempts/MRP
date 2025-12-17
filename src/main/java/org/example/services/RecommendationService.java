package org.example.services;

import org.example.db.Database;
import org.example.models.MediaEntry;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class RecommendationService {
    private final Database db = Database.getInstance();

    public Map<String, Object> getRecommendations(UUID userId) throws SQLException {
        // Get user's preferences from highly rated media (4+ stars)
        ResultSet prefsRs = db.query(
            "SELECT m.genres, m.media_type, m.age_restriction FROM ratings r " +
            "JOIN media_entries m ON r.media_id = m.id " +
            "WHERE r.user_id = ? AND r.stars >= 4",
            userId
        );

        // Count genre occurrences
        Map<String, Integer> genreCounts = new HashMap<>();
        // Count media type occurrences
        Map<String, Integer> typeCounts = new HashMap<>();
        // Count age restriction occurrences
        Map<String, Integer> ageCounts = new HashMap<>();

        while (prefsRs.next()) {
            // Genres
            String genres = prefsRs.getString("genres");
            if (genres != null && !genres.isEmpty()) {
                for (String genre : genres.split(",")) {
                    genre = genre.trim().toLowerCase();
                    if (!genre.isEmpty()) {
                        genreCounts.put(genre, genreCounts.getOrDefault(genre, 0) + 1);
                    }
                }
            }

            // Media type
            String mediaType = prefsRs.getString("media_type");
            if (mediaType != null && !mediaType.isEmpty()) {
                typeCounts.put(mediaType, typeCounts.getOrDefault(mediaType, 0) + 1);
            }

            // Age restriction
            String ageRestriction = prefsRs.getString("age_restriction");
            if (ageRestriction != null && !ageRestriction.isEmpty()) {
                ageCounts.put(ageRestriction, ageCounts.getOrDefault(ageRestriction, 0) + 1);
            }
        }

        // Get top 5 favorite genres
        List<String> favoriteGenres = genreCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(5)
            .map(Map.Entry::getKey)
            .toList();

        // Get preferred media types (top 2)
        List<String> preferredTypes = typeCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(2)
            .map(Map.Entry::getKey)
            .toList();

        // Get preferred age restrictions (top 2)
        List<String> preferredAges = ageCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(2)
            .map(Map.Entry::getKey)
            .toList();

        List<MediaEntry> recommendations = new ArrayList<>();

        if (!favoriteGenres.isEmpty() || !preferredTypes.isEmpty()) {
            // Build query for recommendations with content similarity
            StringBuilder conditions = new StringBuilder();
            List<Object> params = new ArrayList<>();
            params.add(userId); // For excluding already rated

            // Genre conditions
            if (!favoriteGenres.isEmpty()) {
                conditions.append("(");
                for (int i = 0; i < favoriteGenres.size(); i++) {
                    if (i > 0) conditions.append(" OR ");
                    conditions.append("LOWER(m.genres) LIKE ?");
                    params.add("%" + favoriteGenres.get(i) + "%");
                }
                conditions.append(")");
            }

            // Media type conditions (content similarity)
            if (!preferredTypes.isEmpty()) {
                if (conditions.length() > 0) conditions.append(" OR ");
                conditions.append("m.media_type IN (");
                for (int i = 0; i < preferredTypes.size(); i++) {
                    if (i > 0) conditions.append(", ");
                    conditions.append("?");
                    params.add(preferredTypes.get(i));
                }
                conditions.append(")");
            }

            // Age restriction conditions (content similarity)
            if (!preferredAges.isEmpty()) {
                if (conditions.length() > 0) conditions.append(" OR ");
                conditions.append("m.age_restriction IN (");
                for (int i = 0; i < preferredAges.size(); i++) {
                    if (i > 0) conditions.append(", ");
                    conditions.append("?");
                    params.add(preferredAges.get(i));
                }
                conditions.append(")");
            }

            ResultSet rs = db.query(
                "SELECT m.*, u.username as creator_username, " +
                "COALESCE(AVG(r.stars), 0) as avg_rating, " +
                "COUNT(DISTINCT r.id) as total_ratings " +
                "FROM media_entries m " +
                "JOIN users u ON m.creator_id = u.id " +
                "LEFT JOIN ratings r ON m.id = r.media_id " +
                "WHERE m.id NOT IN (SELECT media_id FROM ratings WHERE user_id = ?) " +
                "AND (" + conditions + ") " +
                "GROUP BY m.id, u.username " +
                "HAVING COALESCE(AVG(r.stars), 0) >= 3.5 OR COUNT(r.id) = 0 " +
                "ORDER BY avg_rating DESC, total_ratings DESC " +
                "LIMIT 10",
                params.toArray()
            );

            while (rs.next()) {
                MediaEntry media = new MediaEntry();
                media.setId(db.getUUID(rs, "id"));
                media.setTitle(rs.getString("title"));
                media.setDescription(rs.getString("description"));
                media.setMediaType(rs.getString("media_type"));
                media.setReleaseYear((Integer) rs.getObject("release_year"));
                media.setGenres(rs.getString("genres"));
                media.setAgeRestriction(rs.getString("age_restriction"));
                media.setCreatorId(db.getUUID(rs, "creator_id"));
                media.setCreatedAt(rs.getTimestamp("created_at"));
                media.setCreatorUsername(rs.getString("creator_username"));
                media.setAverageRating(rs.getDouble("avg_rating"));
                media.setTotalRatings(rs.getInt("total_ratings"));
                recommendations.add(media);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("recommendations", recommendations);
        result.put("basedOnGenres", favoriteGenres);
        result.put("basedOnTypes", preferredTypes);
        result.put("basedOnAgeRestrictions", preferredAges);

        return result;
    }
}
