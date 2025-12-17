package org.example.repositories;

import org.example.db.Database;
import org.example.models.MediaEntry;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FavoriteRepository {
    private final Database db = Database.getInstance();

    public boolean exists(UUID userId, UUID mediaId) throws SQLException {
        return db.exists("SELECT 1 FROM favorites WHERE user_id = ? AND media_id = ?", userId, mediaId);
    }

    public void add(UUID userId, UUID mediaId) throws SQLException {
        db.update("INSERT INTO favorites (user_id, media_id) VALUES (?, ?)", userId, mediaId);
    }

    public int remove(UUID userId, UUID mediaId) throws SQLException {
        return db.update("DELETE FROM favorites WHERE user_id = ? AND media_id = ?", userId, mediaId);
    }

    public List<MediaEntry> findByUserId(UUID userId) throws SQLException {
        ResultSet rs = db.query(
            "SELECT m.*, u.username as creator_username, " +
            "COALESCE(AVG(r.stars), 0) as avg_rating, " +
            "COUNT(DISTINCT r.id) as total_ratings " +
            "FROM favorites f " +
            "JOIN media_entries m ON f.media_id = m.id " +
            "JOIN users u ON m.creator_id = u.id " +
            "LEFT JOIN ratings r ON m.id = r.media_id " +
            "WHERE f.user_id = ? " +
            "GROUP BY m.id, u.username, f.created_at " +
            "ORDER BY f.created_at DESC",
            userId
        );

        List<MediaEntry> favorites = new ArrayList<>();
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
            favorites.add(media);
        }
        return favorites;
    }
}
