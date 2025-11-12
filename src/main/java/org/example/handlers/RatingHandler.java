package org.example.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.example.db.Database;
import org.example.models.Rating;
import org.example.utils.JsonHelper;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RatingHandler implements HttpHandler {
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

            // /api/media/{id}/ratings - Create rating
            if (segments.length == 4 && "ratings".equals(segments[3]) && "POST".equals(method)) {
                String mediaId = segments[2];
                handleCreateRating(exchange, mediaId, userId);
            }
            // /api/ratings/{id} - Update or delete rating
            else if (segments.length == 3 && "ratings".equals(segments[1])) {
                String ratingId = segments[2];

                if ("PUT".equals(method)) {
                    handleUpdateRating(exchange, ratingId, userId);
                } else if ("DELETE".equals(method)) {
                    handleDeleteRating(exchange, ratingId, userId);
                } else {
                    JsonHelper.sendError(exchange, 405, "Method not allowed");
                }
            }
            // /api/ratings/{id}/confirm - Confirm comment
            else if (segments.length == 4 && "ratings".equals(segments[1]) && "confirm".equals(segments[3]) && "PUT".equals(method)) {
                String ratingId = segments[2];
                handleConfirmComment(exchange, ratingId, userId);
            }
            // /api/ratings/{id}/like - Like rating
            else if (segments.length == 4 && "ratings".equals(segments[1]) && "like".equals(segments[3]) && "POST".equals(method)) {
                String ratingId = segments[2];
                handleLikeRating(exchange, ratingId, userId);
            }
            // /api/ratings/{id}/unlike - Unlike rating
            else if (segments.length == 4 && "ratings".equals(segments[1]) && "unlike".equals(segments[3]) && "DELETE".equals(method)) {
                String ratingId = segments[2];
                handleUnlikeRating(exchange, ratingId, userId);
            } else {
                JsonHelper.sendError(exchange, 404, "Endpoint not found");
            }
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendError(exchange, 500, "Internal server error");
        }
    }

    private void handleCreateRating(HttpExchange exchange, String mediaId, UUID userId) throws IOException, SQLException {
        // Convert mediaId string to UUID
        UUID mediaUuid;
        try {
            mediaUuid = UUID.fromString(mediaId);
        } catch (IllegalArgumentException e) {
            JsonHelper.sendError(exchange, 400, "Invalid media ID format");
            return;
        }

        // Check if media exists
        if (!db.exists("SELECT 1 FROM media_entries WHERE id = ?", mediaUuid)) {
            JsonHelper.sendError(exchange, 404, "Media not found");
            return;
        }

        // Check if user already rated this media
        if (db.exists("SELECT 1 FROM ratings WHERE media_id = ? AND user_id = ?", mediaUuid, userId)) {
            JsonHelper.sendError(exchange, 400, "You have already rated this media");
            return;
        }

        // Parse request
        Map<String, Object> request = JsonHelper.parseRequest(exchange, HashMap.class);
        Integer stars = (Integer) request.get("stars");
        String comment = (String) request.get("comment");

        // Validate stars
        if (stars == null || stars < 1 || stars > 5) {
            JsonHelper.sendError(exchange, 400, "Stars must be between 1 and 5");
            return;
        }

        // Comments start unconfirmed if present
        boolean isConfirmed = (comment == null || comment.trim().isEmpty());

        // Insert rating with UUID
        UUID ratingId = db.insert(
            "INSERT INTO ratings (id, media_id, user_id, stars, comment, is_confirmed) VALUES (?, ?, ?, ?, ?, ?)",
            mediaUuid, userId, stars, comment, isConfirmed
        );

        // Create response
        Rating rating = new Rating();
        rating.setId(ratingId);
        rating.setMediaId(mediaUuid);
        rating.setUserId(userId);
        rating.setStars(stars);
        rating.setComment(comment);
        rating.setConfirmed(isConfirmed);

        JsonHelper.sendResponse(exchange, 201, rating);
    }

    private void handleUpdateRating(HttpExchange exchange, String ratingId, UUID userId) throws IOException, SQLException {
        // Validate ratingId
        if (ratingId == null || ratingId.trim().isEmpty()) {
            JsonHelper.sendError(exchange, 400, "Rating ID is required");
            return;
        }

        // Convert ratingId string to UUID
        UUID ratingUuid;
        try {
            ratingUuid = UUID.fromString(ratingId);
        } catch (IllegalArgumentException e) {
            JsonHelper.sendError(exchange, 400, "Invalid rating ID format");
            return;
        }

        // Check if rating exists and belongs to user
        ResultSet rs = db.query("SELECT user_id, media_id FROM ratings WHERE id = ?", ratingUuid);

        if (!rs.next()) {
            JsonHelper.sendError(exchange, 404, "Rating not found");
            return;
        }

        if (!db.getUUID(rs, "user_id").equals(userId)) {
            JsonHelper.sendError(exchange, 403, "You can only edit your own ratings");
            return;
        }

        // Parse request
        Map<String, Object> request = JsonHelper.parseRequest(exchange, HashMap.class);
        Integer stars = (Integer) request.get("stars");
        String comment = (String) request.get("comment");

        // Validate stars
        if (stars != null && (stars < 1 || stars > 5)) {
            JsonHelper.sendError(exchange, 400, "Stars must be between 1 and 5");
            return;
        }

        // Update rating - if comment changes, reset confirmation
        boolean needsConfirmation = comment != null && !comment.trim().isEmpty();

        if (stars != null && comment != null) {
            db.update(
                "UPDATE ratings SET stars = ?, comment = ?, is_confirmed = ? WHERE id = ?",
                stars, comment, !needsConfirmation, ratingUuid
            );
        } else if (stars != null) {
            db.update("UPDATE ratings SET stars = ? WHERE id = ?", stars, ratingUuid);
        } else if (comment != null) {
            db.update(
                "UPDATE ratings SET comment = ?, is_confirmed = ? WHERE id = ?",
                comment, !needsConfirmation, ratingUuid
            );
        }

        JsonHelper.sendSuccess(exchange, "Rating updated successfully");
    }

    private void handleDeleteRating(HttpExchange exchange, String ratingId, UUID userId) throws IOException, SQLException {
        // Validate ratingId
        if (ratingId == null || ratingId.trim().isEmpty()) {
            JsonHelper.sendError(exchange, 400, "Rating ID is required");
            return;
        }

        // Convert ratingId string to UUID
        UUID ratingUuid;
        try {
            ratingUuid = UUID.fromString(ratingId);
        } catch (IllegalArgumentException e) {
            JsonHelper.sendError(exchange, 400, "Invalid rating ID format");
            return;
        }

        // Check if rating exists and belongs to user
        Object ratingUserId = db.getValue("SELECT user_id FROM ratings WHERE id = ?", ratingUuid);

        if (ratingUserId == null) {
            JsonHelper.sendError(exchange, 404, "Rating not found");
            return;
        }

        if (!ratingUserId.equals(userId)) {
            JsonHelper.sendError(exchange, 403, "You can only delete your own ratings");
            return;
        }

        // Delete rating
        db.update("DELETE FROM ratings WHERE id = ?", ratingUuid);
        JsonHelper.sendSuccess(exchange, "Rating deleted successfully");
    }

    private void handleConfirmComment(HttpExchange exchange, String ratingId, UUID userId) throws IOException, SQLException {
        // Validate ratingId
        if (ratingId == null || ratingId.trim().isEmpty()) {
            JsonHelper.sendError(exchange, 400, "Rating ID is required");
            return;
        }

        // Convert ratingId string to UUID
        UUID ratingUuid;
        try {
            ratingUuid = UUID.fromString(ratingId);
        } catch (IllegalArgumentException e) {
            JsonHelper.sendError(exchange, 400, "Invalid rating ID format");
            return;
        }

        // Check if rating exists and belongs to user
        Object ratingUserId = db.getValue("SELECT user_id FROM ratings WHERE id = ?", ratingUuid);

        if (ratingUserId == null) {
            JsonHelper.sendError(exchange, 404, "Rating not found");
            return;
        }

        if (!ratingUserId.equals(userId)) {
            JsonHelper.sendError(exchange, 403, "You can only confirm your own comments");
            return;
        }

        // Confirm comment
        db.update("UPDATE ratings SET is_confirmed = true WHERE id = ?", ratingUuid);
        JsonHelper.sendSuccess(exchange, "Comment confirmed and now public");
    }

    private void handleLikeRating(HttpExchange exchange, String ratingId, UUID userId) throws IOException, SQLException {
        // Convert ratingId string to UUID
        UUID ratingUuid;
        try {
            ratingUuid = UUID.fromString(ratingId);
        } catch (IllegalArgumentException e) {
            JsonHelper.sendError(exchange, 400, "Invalid rating ID format");
            return;
        }

        // Check if rating exists
        if (!db.exists("SELECT 1 FROM ratings WHERE id = ?", ratingUuid)) {
            JsonHelper.sendError(exchange, 404, "Rating not found");
            return;
        }

        // Check if already liked
        if (db.exists("SELECT 1 FROM rating_likes WHERE rating_id = ? AND user_id = ?", ratingUuid, userId)) {
            JsonHelper.sendError(exchange, 400, "Already liked this rating");
            return;
        }

        // Add like
        db.update("INSERT INTO rating_likes (rating_id, user_id) VALUES (?, ?)", ratingUuid, userId);
        JsonHelper.sendSuccess(exchange, "Rating liked");
    }

    private void handleUnlikeRating(HttpExchange exchange, String ratingId, UUID userId) throws IOException, SQLException {
        // Convert ratingId string to UUID
        UUID ratingUuid;
        try {
            ratingUuid = UUID.fromString(ratingId);
        } catch (IllegalArgumentException e) {
            JsonHelper.sendError(exchange, 400, "Invalid rating ID format");
            return;
        }

        int deleted = db.update("DELETE FROM rating_likes WHERE rating_id = ? AND user_id = ?", ratingUuid, userId);

        if (deleted > 0) {
            JsonHelper.sendSuccess(exchange, "Like removed");
        } else {
            JsonHelper.sendError(exchange, 404, "Like not found");
        }
    }
}
