package org.example.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.example.db.Database;
import org.example.models.MediaEntry;
import org.example.models.User;
import org.example.utils.JsonHelper;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UserHandler implements HttpHandler {
    private final Database db = Database.getInstance();
    private final AuthHandler authHandler = new AuthHandler();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            // Check authentication
            UUID userId = authHandler.validateToken(exchange);
            if (userId == null) {
                JsonHelper.sendError(exchange, 401, "Authentication required");
                return;
            }

            String[] segments = JsonHelper.getPathSegments(path);

            // /api/users/{username}/profile
            if (segments.length >= 3 && "profile".equals(segments[segments.length - 1])) {
                String username = segments[2];

                if ("GET".equals(method)) {
                    handleGetProfile(exchange, username);
                } else {
                    JsonHelper.sendError(exchange, 405, "Method not allowed");
                }
            }
            // /api/users/{username}/favorites
            else if (segments.length >= 3 && "favorites".equals(segments[segments.length - 1])) {
                String username = segments[2];

                if ("GET".equals(method)) {
                    handleGetFavorites(exchange, username);
                } else {
                    JsonHelper.sendError(exchange, 405, "Method not allowed");
                }
            }
            // /api/users/{username}/ratings
            else if (segments.length >= 3 && "ratings".equals(segments[segments.length - 1])) {
                String username = segments[2];

                if ("GET".equals(method)) {
                    handleGetUserRatings(exchange, username, userId.toString());
                } else {
                    JsonHelper.sendError(exchange, 405, "Method not allowed");
                }
            }
            // /api/leaderboard
            else if (path.endsWith("/leaderboard") && "GET".equals(method)) {
                handleGetLeaderboard(exchange);
            }
            // /api/recommendations
            else if (path.endsWith("/recommendations") && "GET".equals(method)) {
                handleGetRecommendations(exchange, userId.toString());
            } else {
                JsonHelper.sendError(exchange, 404, "Endpoint not found");
            }
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendError(exchange, 500, "Internal server error");
        }
    }

    private void handleGetProfile(HttpExchange exchange, String username) throws IOException, SQLException {
        // Get user info
        ResultSet rs = db.query(
            "SELECT u.*, " +
            "(SELECT COUNT(*) FROM ratings WHERE user_id = u.id) as total_ratings, " +
            "(SELECT COUNT(*) FROM favorites WHERE user_id = u.id) as total_favorites, " +
            "(SELECT COUNT(*) FROM media_entries WHERE creator_id = u.id) as total_media_created " +
            "FROM users u WHERE u.username = ?",
            username
        );

        if (!rs.next()) {
            JsonHelper.sendError(exchange, 404, "User not found");
            return;
        }

        User user = new User();
        user.setId(db.getUUID(rs, "id"));
        user.setUsername(rs.getString("username"));
        user.setCreatedAt(rs.getTimestamp("created_at"));
        user.setTotalRatings(rs.getInt("total_ratings"));
        user.setTotalFavorites(rs.getInt("total_favorites"));
        user.setTotalMediaCreated(rs.getInt("total_media_created"));

        JsonHelper.sendResponse(exchange, 200, user);
    }

    private void handleGetFavorites(HttpExchange exchange, String username) throws IOException, SQLException {
        // Get user ID
        Object userIdObj = db.getValue("SELECT id FROM users WHERE username = ?", username);

        if (userIdObj == null) {
            JsonHelper.sendError(exchange, 404, "User not found");
            return;
        }

        // Keep as UUID object (don't convert to String)
        UUID targetUserId = (UUID) userIdObj;

        // Get favorite media
        ResultSet rs = db.query(
            "SELECT m.*, u.username as creator_username, " +
            "COALESCE(AVG(r.stars), 0) as avg_rating, " +
            "COUNT(DISTINCT r.id) as total_ratings " +
            "FROM media_entries m " +
            "JOIN favorites f ON m.id = f.media_id " +
            "JOIN users u ON m.creator_id = u.id " +
            "LEFT JOIN ratings r ON m.id = r.media_id " +
            "WHERE f.user_id = ? " +
            "GROUP BY m.id, u.username, f.created_at " +
            "ORDER BY f.created_at DESC",
            targetUserId
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

        JsonHelper.sendResponse(exchange, 200, favorites);
    }

    private void handleGetUserRatings(HttpExchange exchange, String username, String currentUserId) throws IOException, SQLException {
        // Get user ID
        Object userIdObj = db.getValue("SELECT id FROM users WHERE username = ?", username);

        if (userIdObj == null) {
            JsonHelper.sendError(exchange, 404, "User not found");
            return;
        }

        // Keep as UUID object
        UUID targetUserId = (UUID) userIdObj;

        // Convert currentUserId from String to UUID
        UUID currentUserUuid = UUID.fromString(currentUserId);

        // Get user's ratings
        ResultSet rs = db.query(
            "SELECT r.*, m.title as media_title, " +
            "(SELECT COUNT(*) FROM rating_likes WHERE rating_id = r.id) as like_count " +
            "FROM ratings r " +
            "JOIN media_entries m ON r.media_id = m.id " +
            "WHERE r.user_id = ? AND (r.is_confirmed = true OR r.user_id = ?) " +
            "ORDER BY r.created_at DESC",
            targetUserId, currentUserUuid
        );

        List<Map<String, Object>> ratingHistory = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> rating = new HashMap<>();
            rating.put("id", db.getUUID(rs, "id"));
            rating.put("mediaId", db.getUUID(rs, "media_id"));
            rating.put("mediaTitle", rs.getString("media_title"));
            rating.put("stars", rs.getInt("stars"));
            rating.put("comment", rs.getString("comment"));
            rating.put("isConfirmed", rs.getBoolean("is_confirmed"));
            rating.put("createdAt", rs.getTimestamp("created_at"));
            rating.put("likeCount", rs.getInt("like_count"));
            ratingHistory.add(rating);
        }

        JsonHelper.sendResponse(exchange, 200, ratingHistory);
    }

    private void handleGetLeaderboard(HttpExchange exchange) throws IOException, SQLException {
        // Get most active users
        ResultSet rs = db.query(
            "SELECT u.username, " +
            "COUNT(DISTINCT r.id) as rating_count, " +
            "COUNT(DISTINCT m.id) as media_created, " +
            "COUNT(DISTINCT rl.rating_id) as likes_given " +
            "FROM users u " +
            "LEFT JOIN ratings r ON u.id = r.user_id " +
            "LEFT JOIN media_entries m ON u.id = m.creator_id " +
            "LEFT JOIN rating_likes rl ON u.id = rl.user_id " +
            "GROUP BY u.id, u.username " +
            "ORDER BY (COUNT(DISTINCT r.id) + COUNT(DISTINCT m.id) + COUNT(DISTINCT rl.rating_id)) DESC " +
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
            entry.put("totalActivity", rs.getInt("rating_count") +
                     rs.getInt("media_created") +
                     rs.getInt("likes_given"));
            leaderboard.add(entry);
        }

        JsonHelper.sendResponse(exchange, 200, leaderboard);
    }

    private void handleGetRecommendations(HttpExchange exchange, String userId) throws IOException, SQLException {
        // Convert userId from String to UUID
        UUID userUuid = UUID.fromString(userId);

        // Get user's highly rated genres (4+ stars)
        ResultSet genresRs = db.query(
            "SELECT m.genres, AVG(r.stars) as avg_rating " +
            "FROM ratings r " +
            "JOIN media_entries m ON r.media_id = m.id " +
            "WHERE r.user_id = ? AND r.stars >= 4 " +
            "GROUP BY m.genres " +
            "ORDER BY avg_rating DESC",
            userUuid
        );

        List<String> favoriteGenres = new ArrayList<>();
        while (genresRs.next() && favoriteGenres.size() < 5) {
            String genres = genresRs.getString("genres");
            if (genres != null) {
                for (String genre : genres.split(",")) {
                    if (!favoriteGenres.contains(genre.trim())) {
                        favoriteGenres.add(genre.trim());
                    }
                }
            }
        }

        // Build recommendation query
        StringBuilder sql = new StringBuilder(
            "SELECT DISTINCT m.*, u.username as creator_username, " +
            "COALESCE(AVG(r.stars), 0) as avg_rating, " +
            "COUNT(DISTINCT r.id) as total_ratings " +
            "FROM media_entries m " +
            "JOIN users u ON m.creator_id = u.id " +
            "LEFT JOIN ratings r ON m.id = r.media_id " +
            "WHERE m.id NOT IN (SELECT media_id FROM ratings WHERE user_id = ?) "
        );

        List<Object> params = new ArrayList<>();
        params.add(userUuid);

        // Add genre filters if we have favorite genres
        if (!favoriteGenres.isEmpty()) {
            sql.append("AND (");
            for (int i = 0; i < favoriteGenres.size(); i++) {
                if (i > 0) sql.append(" OR ");
                sql.append("LOWER(m.genres) LIKE LOWER(?)");
                params.add("%" + favoriteGenres.get(i) + "%");
            }
            sql.append(") ");
        }

        sql.append("GROUP BY m.id, u.username ");
        sql.append("HAVING COALESCE(AVG(r.stars), 0) >= 3.5 ");
        sql.append("ORDER BY avg_rating DESC, total_ratings DESC ");
        sql.append("LIMIT 10");

        ResultSet rs = db.query(sql.toString(), params.toArray());

        List<MediaEntry> recommendations = new ArrayList<>();
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

        Map<String, Object> response = new HashMap<>();
        response.put("recommendations", recommendations);
        response.put("basedOnGenres", favoriteGenres);

        JsonHelper.sendResponse(exchange, 200, response);
    }
}
