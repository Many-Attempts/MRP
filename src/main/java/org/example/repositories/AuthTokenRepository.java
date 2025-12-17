package org.example.repositories;

import org.example.db.Database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class AuthTokenRepository {
    private final Database db = Database.getInstance();

    public void saveToken(String token, UUID userId) throws SQLException {
        db.update(
            "INSERT INTO auth_tokens (token, user_id) VALUES (?, ?) ON CONFLICT (user_id) DO UPDATE SET token = ?",
            token, userId, token
        );
    }

    public UUID findUserIdByToken(String token) throws SQLException {
        ResultSet rs = db.query("SELECT user_id FROM auth_tokens WHERE token = ?", token);
        if (rs.next()) {
            return db.getUUID(rs, "user_id");
        }
        return null;
    }

    public void deleteToken(String token) throws SQLException {
        db.update("DELETE FROM auth_tokens WHERE token = ?", token);
    }

    public void deleteByUserId(UUID userId) throws SQLException {
        db.update("DELETE FROM auth_tokens WHERE user_id = ?", userId);
    }
}
