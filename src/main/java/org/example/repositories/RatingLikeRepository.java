package org.example.repositories;

import org.example.db.Database;

import java.sql.SQLException;
import java.util.UUID;

public class RatingLikeRepository {
    private final Database db = Database.getInstance();

    public boolean exists(UUID ratingId, UUID userId) throws SQLException {
        return db.exists("SELECT 1 FROM rating_likes WHERE rating_id = ? AND user_id = ?", ratingId, userId);
    }

    public void add(UUID ratingId, UUID userId) throws SQLException {
        db.update("INSERT INTO rating_likes (rating_id, user_id) VALUES (?, ?)", ratingId, userId);
    }

    public int remove(UUID ratingId, UUID userId) throws SQLException {
        return db.update("DELETE FROM rating_likes WHERE rating_id = ? AND user_id = ?", ratingId, userId);
    }

    public int countByRatingId(UUID ratingId) throws SQLException {
        Object count = db.getValue("SELECT COUNT(*) FROM rating_likes WHERE rating_id = ?", ratingId);
        return count != null ? ((Number) count).intValue() : 0;
    }

    public int countByUserId(UUID userId) throws SQLException {
        Object count = db.getValue("SELECT COUNT(*) FROM rating_likes WHERE user_id = ?", userId);
        return count != null ? ((Number) count).intValue() : 0;
    }
}
