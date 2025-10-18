package org.example.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.example.db.Database;
import org.example.models.MediaEntry;
import org.example.models.Rating;
import org.example.utils.JsonHelper;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MediaHandler implements HttpHandler {
    private final Database db = Database.getInstance();
    private final AuthHandler authHandler = new AuthHandler();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();

        try {
            // Check authentication
            UUID userId = authHandler.validateToken(exchange);
            if (userId == null) {
                JsonHelper.sendError(exchange, 401, "Authentication required");
                return;
            }

            String[] segments = JsonHelper.getPathSegments(path);

            // /api/media
            if (segments.length == 2) {
                if ("GET".equals(method)) {
                    handleGetMediaList(exchange, query, userId);
                } else if ("POST".equals(method)) {
                    handleCreateMedia(exchange, userId);
                } else {
                    JsonHelper.sendError(exchange, 405, "Method not allowed");
                }
            }
            // /api/media/{id}
            else if (segments.length == 3) {
                String mediaId = segments[2];

                if ("GET".equals(method)) {
                    handleGetMedia(exchange, mediaId, userId);
                } else if ("PUT".equals(method)) {
                    handleUpdateMedia(exchange, mediaId, userId);
                } else if ("DELETE".equals(method)) {
                    handleDeleteMedia(exchange, mediaId, userId);
                } else {
                    JsonHelper.sendError(exchange, 405, "Method not allowed");
                }
            }
            // /api/media/{id}/favorite
            else if (segments.length == 4 && "favorite".equals(segments[3])) {
                String mediaId = segments[2];

                if ("POST".equals(method)) {
                    handleAddFavorite(exchange, mediaId, userId);
                } else if ("DELETE".equals(method)) {
                    handleRemoveFavorite(exchange, mediaId, userId);
                } else {
                    JsonHelper.sendError(exchange, 405, "Method not allowed");
                }
            } else {
                JsonHelper.sendError(exchange, 404, "Endpoint not found");
            }
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendError(exchange, 500, "Internal server error");
        }
    }

    private void handleGetMediaList(HttpExchange exchange, String query, UUID userId) throws IOException, SQLException {
        Map<String, String> params = JsonHelper.parseQueryParams(query);

        // Build SQL query with filters
        StringBuilder sql = new StringBuilder(
            "SELECT m.*, u.username as creator_username, " +
            "COALESCE(AVG(r.stars), 0) as avg_rating, " +
            "COUNT(DISTINCT r.id) as total_ratings " +
            "FROM media_entries m " +
            "JOIN users u ON m.creator_id = u.id " +
            "LEFT JOIN ratings r ON m.id = r.media_id " +
            "WHERE 1=1 "
        );

        List<Object> queryParams = new ArrayList<>();

        // Apply filters
        if (params.containsKey("search")) {
            sql.append("AND LOWER(m.title) LIKE LOWER(?) ");
            queryParams.add("%" + params.get("search") + "%");
        }

        if (params.containsKey("type")) {
            sql.append("AND m.media_type = ? ");
            queryParams.add(params.get("type"));
        }

        if (params.containsKey("genre")) {
            sql.append("AND LOWER(m.genres) LIKE LOWER(?) ");
            queryParams.add("%" + params.get("genre") + "%");
        }

        if (params.containsKey("year")) {
            sql.append("AND m.release_year = ? ");
            queryParams.add(Integer.parseInt(params.get("year")));
        }

        if (params.containsKey("age")) {
            sql.append("AND m.age_restriction = ? ");
            queryParams.add(params.get("age"));
        }

        sql.append("GROUP BY m.id, u.username ");

        // Apply sorting
        String sortBy = params.getOrDefault("sort", "title");
        switch (sortBy) {
            case "year":
                sql.append("ORDER BY m.release_year DESC");
                break;
            case "rating":
                sql.append("ORDER BY avg_rating DESC");
                break;
            default:
                sql.append("ORDER BY m.title ASC");
        }

        ResultSet rs = db.query(sql.toString(), queryParams.toArray());
        List<MediaEntry> mediaList = new ArrayList<>();

        while (rs.next()) {
            MediaEntry media = mapResultSetToMedia(rs);
            mediaList.add(media);
        }

        JsonHelper.sendResponse(exchange, 200, mediaList);
    }

    private void handleGetMedia(HttpExchange exchange, String mediaId, UUID userId) throws IOException, SQLException {
        ResultSet rs = db.query(
            "SELECT m.*, u.username as creator_username, " +
            "COALESCE(AVG(r.stars), 0) as avg_rating, " +
            "COUNT(DISTINCT r.id) as total_ratings " +
            "FROM media_entries m " +
            "JOIN users u ON m.creator_id = u.id " +
            "LEFT JOIN ratings r ON m.id = r.media_id " +
            "WHERE m.id = ? " +
            "GROUP BY m.id, u.username",
            mediaId
        );

        if (!rs.next()) {
            JsonHelper.sendError(exchange, 404, "Media not found");
            return;
        }

        MediaEntry media = mapResultSetToMedia(rs);

        // Get ratings for this media
        ResultSet ratingsRs = db.query(
            "SELECT r.*, u.username, " +
            "(SELECT COUNT(*) FROM rating_likes WHERE rating_id = r.id) as like_count, " +
            "EXISTS(SELECT 1 FROM rating_likes WHERE rating_id = r.id AND user_id = ?) as liked_by_user " +
            "FROM ratings r " +
            "JOIN users u ON r.user_id = u.id " +
            "WHERE r.media_id = ? AND (r.is_confirmed = true OR r.user_id = ?) " +
            "ORDER BY r.created_at DESC",
            userId, mediaId, userId
        );

        List<Rating> ratings = new ArrayList<>();
        while (ratingsRs.next()) {
            Rating rating = new Rating();
            rating.setId(db.getUUID(ratingsRs, "id"));
            rating.setMediaId(db.getUUID(ratingsRs, "media_id"));
            rating.setUserId(db.getUUID(ratingsRs, "user_id"));
            rating.setStars(ratingsRs.getInt("stars"));
            rating.setComment(ratingsRs.getString("comment"));
            rating.setConfirmed(ratingsRs.getBoolean("is_confirmed"));
            rating.setCreatedAt(ratingsRs.getTimestamp("created_at"));
            rating.setUsername(ratingsRs.getString("username"));
            rating.setLikeCount(ratingsRs.getInt("like_count"));
            rating.setLikedByCurrentUser(ratingsRs.getBoolean("liked_by_user"));
            ratings.add(rating);
        }

        media.setRatings(ratings);
        JsonHelper.sendResponse(exchange, 200, media);
    }

    private void handleCreateMedia(HttpExchange exchange, UUID userId) throws IOException, SQLException {
        MediaEntry media = JsonHelper.parseRequest(exchange, MediaEntry.class);

        // Validate input
        if (media.getTitle() == null || media.getTitle().trim().isEmpty()) {
            JsonHelper.sendError(exchange, 400, "Title is required");
            return;
        }

        if (media.getMediaType() == null ||
            (!media.getMediaType().equals("movie") &&
             !media.getMediaType().equals("series") &&
             !media.getMediaType().equals("game"))) {
            JsonHelper.sendError(exchange, 400, "Media type must be 'movie', 'series', or 'game'");
            return;
        }

        // Insert media with UUID
        UUID mediaId = db.insert(
            "INSERT INTO media_entries (id, title, description, media_type, release_year, genres, age_restriction, creator_id) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            media.getTitle(),
            media.getDescription(),
            media.getMediaType(),
            media.getReleaseYear(),
            media.getGenres(),
            media.getAgeRestriction(),
            userId
        );

        media.setId(mediaId);
        media.setCreatorId(userId);

        JsonHelper.sendResponse(exchange, 201, media);
    }

    private void handleUpdateMedia(HttpExchange exchange, String mediaId, UUID userId) throws IOException, SQLException {
        // Check if user is the creator
        Object creatorIdObj = db.getValue("SELECT creator_id FROM media_entries WHERE id = ?", mediaId);

        if (creatorIdObj == null) {
            JsonHelper.sendError(exchange, 404, "Media not found");
            return;
        }

        UUID creatorId = UUID.fromString((String) creatorIdObj);
        if (!creatorId.equals(userId)) {
            JsonHelper.sendError(exchange, 403, "Only the creator can edit this media");
            return;
        }

        MediaEntry media = JsonHelper.parseRequest(exchange, MediaEntry.class);

        // Validate input
        if (media.getTitle() == null || media.getTitle().trim().isEmpty()) {
            JsonHelper.sendError(exchange, 400, "Title is required");
            return;
        }

        if (media.getMediaType() == null ||
            (!media.getMediaType().equals("movie") &&
             !media.getMediaType().equals("series") &&
             !media.getMediaType().equals("game"))) {
            JsonHelper.sendError(exchange, 400, "Media type must be 'movie', 'series', or 'game'");
            return;
        }

        // Update media
        int updated = db.update(
            "UPDATE media_entries SET title = ?, description = ?, media_type = ?, " +
            "release_year = ?, genres = ?, age_restriction = ? WHERE id = ?",
            media.getTitle(),
            media.getDescription(),
            media.getMediaType(),
            media.getReleaseYear(),
            media.getGenres(),
            media.getAgeRestriction(),
            mediaId
        );

        if (updated > 0) {
            media.setId(UUID.fromString(mediaId));
            JsonHelper.sendResponse(exchange, 200, media);
        } else {
            JsonHelper.sendError(exchange, 500, "Failed to update media");
        }
    }

    private void handleDeleteMedia(HttpExchange exchange, String mediaId, UUID userId) throws IOException, SQLException {
        // Check if user is the creator
        Object creatorIdObj = db.getValue("SELECT creator_id FROM media_entries WHERE id = ?", mediaId);

        if (creatorIdObj == null) {
            JsonHelper.sendError(exchange, 404, "Media not found");
            return;
        }

        UUID creatorId = UUID.fromString((String) creatorIdObj);
        if (!creatorId.equals(userId)) {
            JsonHelper.sendError(exchange, 403, "Only the creator can delete this media");
            return;
        }

        // Delete media (cascades to ratings, favorites, etc.)
        int deleted = db.update("DELETE FROM media_entries WHERE id = ?", mediaId);

        if (deleted > 0) {
            JsonHelper.sendSuccess(exchange, "Media deleted successfully");
        } else {
            JsonHelper.sendError(exchange, 500, "Failed to delete media");
        }
    }

    private void handleAddFavorite(HttpExchange exchange, String mediaId, UUID userId) throws IOException, SQLException {
        // Check if media exists
        if (!db.exists("SELECT 1 FROM media_entries WHERE id = ?", mediaId)) {
            JsonHelper.sendError(exchange, 404, "Media not found");
            return;
        }

        // Check if already favorited
        if (db.exists("SELECT 1 FROM favorites WHERE user_id = ? AND media_id = ?", userId, mediaId)) {
            JsonHelper.sendError(exchange, 400, "Already in favorites");
            return;
        }

        // Add to favorites
        db.update("INSERT INTO favorites (user_id, media_id) VALUES (?, ?)", userId, mediaId);
        JsonHelper.sendSuccess(exchange, "Added to favorites");
    }

    private void handleRemoveFavorite(HttpExchange exchange, String mediaId, UUID userId) throws IOException, SQLException {
        int deleted = db.update("DELETE FROM favorites WHERE user_id = ? AND media_id = ?", userId, mediaId);

        if (deleted > 0) {
            JsonHelper.sendSuccess(exchange, "Removed from favorites");
        } else {
            JsonHelper.sendError(exchange, 404, "Not in favorites");
        }
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