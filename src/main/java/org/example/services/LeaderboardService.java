package org.example.services;

import org.example.db.Database;
import org.example.repositories.RatingLikeRepository;
import org.example.repositories.UserRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LeaderboardService {
    private final Database db = Database.getInstance();

    public List<Map<String, Object>> getLeaderboard() throws SQLException {
        ResultSet rs = db.query(
            "SELECT u.username, " +
            "(SELECT COUNT(*) FROM ratings WHERE user_id = u.id) as rating_count, " +
            "(SELECT COUNT(*) FROM media_entries WHERE creator_id = u.id) as media_created, " +
            "(SELECT COUNT(*) FROM rating_likes WHERE user_id = u.id) as likes_given " +
            "FROM users u " +
            "ORDER BY (SELECT COUNT(*) FROM ratings WHERE user_id = u.id) + " +
            "(SELECT COUNT(*) FROM media_entries WHERE creator_id = u.id) + " +
            "(SELECT COUNT(*) FROM rating_likes WHERE user_id = u.id) DESC " +
            "LIMIT 10"
        );

        List<Map<String, Object>> leaderboard = new ArrayList<>();
        int rank = 1;

        while (rs.next()) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("rank", rank++);
            entry.put("username", rs.getString("username"));
            entry.put("ratingCount", rs.getInt("rating_count"));
            entry.put("mediaCreated", rs.getInt("media_created"));
            entry.put("likesGiven", rs.getInt("likes_given"));
            entry.put("totalActivity", rs.getInt("rating_count") + rs.getInt("media_created") + rs.getInt("likes_given"));
            leaderboard.add(entry);
        }

        return leaderboard;
    }
}
