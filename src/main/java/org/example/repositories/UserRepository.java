package org.example.repositories;

import org.example.db.Database;
import org.example.models.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class UserRepository {
    private final Database db = Database.getInstance();

    public User findById(UUID id) throws SQLException {
        ResultSet rs = db.query("SELECT * FROM users WHERE id = ?", id);
        if (rs.next()) {
            return mapResultSetToUser(rs);
        }
        return null;
    }

    public User findByUsername(String username) throws SQLException {
        ResultSet rs = db.query("SELECT * FROM users WHERE username = ?", username);
        if (rs.next()) {
            return mapResultSetToUser(rs);
        }
        return null;
    }

    public boolean existsByUsername(String username) throws SQLException {
        return db.exists("SELECT 1 FROM users WHERE username = ?", username);
    }

    public User create(String username, String passwordHash) throws SQLException {
        UUID userId = db.insert(
            "INSERT INTO users (id, username, password_hash) VALUES (?, ?, ?)",
            username, passwordHash
        );
        return findById(userId);
    }

    public int getTotalRatings(UUID userId) throws SQLException {
        Object count = db.getValue(
            "SELECT COUNT(*) FROM ratings WHERE user_id = ?",
            userId
        );
        return count != null ? ((Number) count).intValue() : 0;
    }

    public int getTotalFavorites(UUID userId) throws SQLException {
        Object count = db.getValue(
            "SELECT COUNT(*) FROM favorites WHERE user_id = ?",
            userId
        );
        return count != null ? ((Number) count).intValue() : 0;
    }

    public int getTotalMediaCreated(UUID userId) throws SQLException {
        Object count = db.getValue(
            "SELECT COUNT(*) FROM media_entries WHERE creator_id = ?",
            userId
        );
        return count != null ? ((Number) count).intValue() : 0;
    }

    public double getAverageScore(UUID userId) throws SQLException {
        Object avg = db.getValue(
            "SELECT COALESCE(AVG(stars), 0) FROM ratings WHERE user_id = ?",
            userId
        );
        return avg != null ? ((Number) avg).doubleValue() : 0.0;
    }

    public int updateUsername(UUID userId, String newUsername) throws SQLException {
        return db.update(
            "UPDATE users SET username = ? WHERE id = ?",
            newUsername, userId
        );
    }

    public String getFavoriteGenre(UUID userId) throws SQLException {
        ResultSet rs = db.query(
            "SELECT m.genres FROM ratings r " +
            "JOIN media_entries m ON r.media_id = m.id " +
            "WHERE r.user_id = ? AND m.genres IS NOT NULL",
            userId
        );

        java.util.Map<String, Integer> genreCounts = new java.util.HashMap<>();
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
        }

        return genreCounts.entrySet().stream()
            .max(java.util.Map.Entry.comparingByValue())
            .map(java.util.Map.Entry::getKey)
            .orElse(null);
    }

    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(db.getUUID(rs, "id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setCreatedAt(rs.getTimestamp("created_at"));
        return user;
    }
}
