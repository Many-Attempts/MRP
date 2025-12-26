package org.example.repositories;

import org.example.db.Database;
import org.example.models.LeaderboardEntry;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class LeaderboardRepository {
    private final Database db = Database.getInstance();

    public List<LeaderboardEntry> findTopUsersByActivity(int limit) throws SQLException {
        ResultSet rs = db.query(
            "SELECT u.username, " +
            "(SELECT COUNT(*) FROM ratings WHERE user_id = u.id) as rating_count, " +
            "(SELECT COUNT(*) FROM media_entries WHERE creator_id = u.id) as media_created, " +
            "(SELECT COUNT(*) FROM rating_likes WHERE user_id = u.id) as likes_given " +
            "FROM users u " +
            "ORDER BY (SELECT COUNT(*) FROM ratings WHERE user_id = u.id) + " +
            "(SELECT COUNT(*) FROM media_entries WHERE creator_id = u.id) + " +
            "(SELECT COUNT(*) FROM rating_likes WHERE user_id = u.id) DESC " +
            "LIMIT ?",
            limit
        );

        List<LeaderboardEntry> entries = new ArrayList<>();
        while (rs.next()) {
            entries.add(mapResultSetToLeaderboardEntry(rs));
        }
        return entries;
    }

    private LeaderboardEntry mapResultSetToLeaderboardEntry(ResultSet rs) throws SQLException {
        return new LeaderboardEntry(
            rs.getString("username"),
            rs.getInt("rating_count"),
            rs.getInt("media_created"),
            rs.getInt("likes_given")
        );
    }
}
