package org.example.repositories;

import org.example.db.Database;
import org.example.models.Rating;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RatingRepository {
    private final Database db = Database.getInstance();

    public Rating findById(UUID id) throws SQLException {
        ResultSet rs = db.query("SELECT * FROM ratings WHERE id = ?", id);
        if (rs.next()) {
            return mapResultSetToRating(rs);
        }
        return null;
    }

    public boolean existsById(UUID id) throws SQLException {
        return db.exists("SELECT 1 FROM ratings WHERE id = ?", id);
    }

    public UUID getUserIdByRatingId(UUID ratingId) throws SQLException {
        Object userId = db.getValue("SELECT user_id FROM ratings WHERE id = ?", ratingId);
        return userId != null ? (UUID) userId : null;
    }

    public boolean existsByMediaAndUser(UUID mediaId, UUID userId) throws SQLException {
        return db.exists("SELECT 1 FROM ratings WHERE media_id = ? AND user_id = ?", mediaId, userId);
    }

    public Rating create(UUID mediaId, UUID userId, int stars, String comment, boolean isConfirmed) throws SQLException {
        UUID ratingId = db.insert(
            "INSERT INTO ratings (id, media_id, user_id, stars, comment, is_confirmed) VALUES (?, ?, ?, ?, ?, ?)",
            mediaId, userId, stars, comment, isConfirmed
        );

        // Query back to get created_at from database
        return findById(ratingId);
    }

    public void updateStarsAndComment(UUID ratingId, Integer stars, String comment, boolean isConfirmed) throws SQLException {
        if (stars != null && comment != null) {
            db.update(
                "UPDATE ratings SET stars = ?, comment = ?, is_confirmed = ? WHERE id = ?",
                stars, comment, isConfirmed, ratingId
            );
        } else if (stars != null) {
            db.update("UPDATE ratings SET stars = ? WHERE id = ?", stars, ratingId);
        } else if (comment != null) {
            db.update(
                "UPDATE ratings SET comment = ?, is_confirmed = ? WHERE id = ?",
                comment, isConfirmed, ratingId
            );
        }
    }

    public void confirmRating(UUID ratingId) throws SQLException {
        db.update("UPDATE ratings SET is_confirmed = true WHERE id = ?", ratingId);
    }

    public int delete(UUID id) throws SQLException {
        return db.update("DELETE FROM ratings WHERE id = ?", id);
    }

    public List<Rating> findByMediaId(UUID mediaId, UUID currentUserId) throws SQLException {
        ResultSet rs = db.query(
            "SELECT r.*, u.username, " +
            "(SELECT COUNT(*) FROM rating_likes WHERE rating_id = r.id) as like_count, " +
            "EXISTS(SELECT 1 FROM rating_likes WHERE rating_id = r.id AND user_id = ?) as liked_by_user " +
            "FROM ratings r " +
            "JOIN users u ON r.user_id = u.id " +
            "WHERE r.media_id = ? AND (r.is_confirmed = true OR r.user_id = ?) " +
            "ORDER BY r.created_at DESC",
            currentUserId, mediaId, currentUserId
        );

        List<Rating> ratings = new ArrayList<>();
        while (rs.next()) {
            Rating rating = mapResultSetToRating(rs);
            rating.setUsername(rs.getString("username"));
            rating.setLikeCount(rs.getInt("like_count"));
            rating.setLikedByCurrentUser(rs.getBoolean("liked_by_user"));
            ratings.add(rating);
        }
        return ratings;
    }

    public List<Rating> findByUserId(UUID userId, UUID requesterId) throws SQLException {
        ResultSet rs = db.query(
            "SELECT r.*, m.title as media_title, " +
            "(SELECT COUNT(*) FROM rating_likes WHERE rating_id = r.id) as like_count " +
            "FROM ratings r " +
            "JOIN media_entries m ON r.media_id = m.id " +
            "WHERE r.user_id = ? AND (r.is_confirmed = true OR r.user_id = ?) " +
            "ORDER BY r.created_at DESC",
            userId, requesterId
        );

        List<Rating> ratings = new ArrayList<>();
        while (rs.next()) {
            Rating rating = mapResultSetToRating(rs);
            rating.setMediaTitle(rs.getString("media_title"));
            rating.setLikeCount(rs.getInt("like_count"));
            ratings.add(rating);
        }
        return ratings;
    }

    private Rating mapResultSetToRating(ResultSet rs) throws SQLException {
        Rating rating = new Rating();
        rating.setId(db.getUUID(rs, "id"));
        rating.setMediaId(db.getUUID(rs, "media_id"));
        rating.setUserId(db.getUUID(rs, "user_id"));
        rating.setStars(rs.getInt("stars"));
        rating.setComment(rs.getString("comment"));
        rating.setConfirmed(rs.getBoolean("is_confirmed"));
        rating.setCreatedAt(rs.getTimestamp("created_at"));
        return rating;
    }
}
