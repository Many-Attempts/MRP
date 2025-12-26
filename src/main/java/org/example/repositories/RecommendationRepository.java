package org.example.repositories;

import org.example.db.Database;
import org.example.models.MediaEntry;
import org.example.models.UserPreferences;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class RecommendationRepository {
    private final Database db = Database.getInstance();

    public UserPreferences findUserPreferences(UUID userId, int topGenres, int topTypes, int topAges) throws SQLException {
        ResultSet rs = db.query(
            "SELECT m.genres, m.media_type, m.age_restriction FROM ratings r " +
            "JOIN media_entries m ON r.media_id = m.id " +
            "WHERE r.user_id = ? AND r.stars >= 4",
            userId
        );

        Map<String, Integer> genreCounts = new HashMap<>();
        Map<String, Integer> typeCounts = new HashMap<>();
        Map<String, Integer> ageCounts = new HashMap<>();

        while (rs.next()) {
            String genres = rs.getString("genres");
            if (genres != null && !genres.isEmpty()) {
                for (String genre : genres.split(",")) {
                    genre = genre.trim().toLowerCase();
                    if (!genre.isEmpty()) {
                        genreCounts.merge(genre, 1, Integer::sum);
                    }
                }
            }

            String mediaType = rs.getString("media_type");
            if (mediaType != null && !mediaType.isEmpty()) {
                typeCounts.merge(mediaType, 1, Integer::sum);
            }

            String ageRestriction = rs.getString("age_restriction");
            if (ageRestriction != null && !ageRestriction.isEmpty()) {
                ageCounts.merge(ageRestriction, 1, Integer::sum);
            }
        }

        List<String> topGenresList = getTopN(genreCounts, topGenres);
        List<String> topTypesList = getTopN(typeCounts, topTypes);
        List<String> topAgesList = getTopN(ageCounts, topAges);

        return new UserPreferences(topGenresList, topTypesList, topAgesList);
    }

    public List<MediaEntry> findRecommendedMedia(
            UUID userId,
            List<String> genres,
            List<String> types,
            List<String> ages,
            double minRating,
            int limit
    ) throws SQLException {
        if ((genres == null || genres.isEmpty())
            && (types == null || types.isEmpty())
            && (ages == null || ages.isEmpty())) {
            return new ArrayList<>();
        }

        StringBuilder conditions = new StringBuilder();
        List<Object> params = new ArrayList<>();
        params.add(userId);

        if (genres != null && !genres.isEmpty()) {
            conditions.append("(");
            for (int i = 0; i < genres.size(); i++) {
                if (i > 0) conditions.append(" OR ");
                conditions.append("LOWER(m.genres) LIKE ?");
                params.add("%" + genres.get(i) + "%");
            }
            conditions.append(")");
        }

        if (types != null && !types.isEmpty()) {
            if (conditions.length() > 0) conditions.append(" OR ");
            conditions.append("m.media_type IN (");
            for (int i = 0; i < types.size(); i++) {
                if (i > 0) conditions.append(", ");
                conditions.append("?");
                params.add(types.get(i));
            }
            conditions.append(")");
        }

        if (ages != null && !ages.isEmpty()) {
            if (conditions.length() > 0) conditions.append(" OR ");
            conditions.append("m.age_restriction IN (");
            for (int i = 0; i < ages.size(); i++) {
                if (i > 0) conditions.append(", ");
                conditions.append("?");
                params.add(ages.get(i));
            }
            conditions.append(")");
        }

        params.add(minRating);
        params.add(limit);

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
            "HAVING COALESCE(AVG(r.stars), 0) >= ? OR COUNT(r.id) = 0 " +
            "ORDER BY avg_rating DESC, total_ratings DESC " +
            "LIMIT ?",
            params.toArray()
        );

        List<MediaEntry> recommendations = new ArrayList<>();
        while (rs.next()) {
            recommendations.add(mapResultSetToMedia(rs));
        }
        return recommendations;
    }

    private List<String> getTopN(Map<String, Integer> counts, int n) {
        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(n)
            .map(Map.Entry::getKey)
            .toList();
    }

    private MediaEntry mapResultSetToMedia(ResultSet rs) throws SQLException {
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
        return media;
    }
}
