package org.example.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.example.exceptions.ConflictException;
import org.example.exceptions.NotFoundException;
import org.example.exceptions.UnauthorizedException;
import org.example.exceptions.ValidationException;
import org.example.models.Rating;
import org.example.services.RatingService;
import org.example.utils.JsonHelper;
import org.example.utils.UUIDGenerator;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RatingHandler implements HttpHandler {
    private final RatingService ratingService = new RatingService();
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
                UUID mediaId = parseUUID(exchange, segments[2]);
                if (mediaId == null) return;
                handleCreateRating(exchange, mediaId, userId);
            }
            // /api/ratings/{id} - Update or delete rating
            else if (segments.length == 3 && "ratings".equals(segments[1])) {
                UUID ratingId = parseUUID(exchange, segments[2]);
                if (ratingId == null) return;

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
                UUID ratingId = parseUUID(exchange, segments[2]);
                if (ratingId == null) return;
                handleConfirmComment(exchange, ratingId, userId);
            }
            // /api/ratings/{id}/like - Like rating
            else if (segments.length == 4 && "ratings".equals(segments[1]) && "like".equals(segments[3]) && "POST".equals(method)) {
                UUID ratingId = parseUUID(exchange, segments[2]);
                if (ratingId == null) return;
                handleLikeRating(exchange, ratingId, userId);
            }
            // /api/ratings/{id}/unlike - Unlike rating
            else if (segments.length == 4 && "ratings".equals(segments[1]) && "unlike".equals(segments[3]) && "DELETE".equals(method)) {
                UUID ratingId = parseUUID(exchange, segments[2]);
                if (ratingId == null) return;
                handleUnlikeRating(exchange, ratingId, userId);
            } else {
                JsonHelper.sendError(exchange, 404, "Endpoint not found");
            }
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendError(exchange, 500, "Internal server error");
        }
    }

    private void handleCreateRating(HttpExchange exchange, UUID mediaId, UUID userId) throws IOException {
        try {
            Map<String, Object> request = JsonHelper.parseRequest(exchange, HashMap.class);
            Integer stars = (Integer) request.get("stars");
            String comment = (String) request.get("comment");

            Rating rating = ratingService.createRating(mediaId, userId, stars, comment);
            JsonHelper.sendResponse(exchange, 201, rating);
        } catch (ValidationException e) {
            JsonHelper.sendError(exchange, 400, e.getMessage());
        } catch (NotFoundException e) {
            JsonHelper.sendError(exchange, 404, e.getMessage());
        } catch (ConflictException e) {
            JsonHelper.sendError(exchange, 400, e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            JsonHelper.sendError(exchange, 500, "Database error");
        }
    }

    private void handleUpdateRating(HttpExchange exchange, UUID ratingId, UUID userId) throws IOException {
        try {
            Map<String, Object> request = JsonHelper.parseRequest(exchange, HashMap.class);
            Integer stars = (Integer) request.get("stars");
            String comment = (String) request.get("comment");

            ratingService.updateRating(ratingId, userId, stars, comment);
            JsonHelper.sendSuccess(exchange, "Rating updated successfully");
        } catch (ValidationException e) {
            JsonHelper.sendError(exchange, 400, e.getMessage());
        } catch (NotFoundException e) {
            JsonHelper.sendError(exchange, 404, e.getMessage());
        } catch (UnauthorizedException e) {
            JsonHelper.sendError(exchange, 403, e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            JsonHelper.sendError(exchange, 500, "Database error");
        }
    }

    private void handleDeleteRating(HttpExchange exchange, UUID ratingId, UUID userId) throws IOException {
        try {
            ratingService.deleteRating(ratingId, userId);
            JsonHelper.sendSuccess(exchange, "Rating deleted successfully");
        } catch (ValidationException e) {
            JsonHelper.sendError(exchange, 400, e.getMessage());
        } catch (NotFoundException e) {
            JsonHelper.sendError(exchange, 404, e.getMessage());
        } catch (UnauthorizedException e) {
            JsonHelper.sendError(exchange, 403, e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            JsonHelper.sendError(exchange, 500, "Database error");
        }
    }

    private void handleConfirmComment(HttpExchange exchange, UUID ratingId, UUID userId) throws IOException {
        try {
            ratingService.confirmRating(ratingId, userId);
            JsonHelper.sendSuccess(exchange, "Comment confirmed and now public");
        } catch (ValidationException e) {
            JsonHelper.sendError(exchange, 400, e.getMessage());
        } catch (NotFoundException e) {
            JsonHelper.sendError(exchange, 404, e.getMessage());
        } catch (UnauthorizedException e) {
            JsonHelper.sendError(exchange, 403, e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            JsonHelper.sendError(exchange, 500, "Database error");
        }
    }

    private void handleLikeRating(HttpExchange exchange, UUID ratingId, UUID userId) throws IOException {
        try {
            ratingService.likeRating(ratingId, userId);
            JsonHelper.sendSuccess(exchange, "Rating liked");
        } catch (NotFoundException e) {
            JsonHelper.sendError(exchange, 404, e.getMessage());
        } catch (ConflictException e) {
            JsonHelper.sendError(exchange, 400, e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            JsonHelper.sendError(exchange, 500, "Database error");
        }
    }

    private void handleUnlikeRating(HttpExchange exchange, UUID ratingId, UUID userId) throws IOException {
        try {
            ratingService.unlikeRating(ratingId, userId);
            JsonHelper.sendSuccess(exchange, "Like removed");
        } catch (NotFoundException e) {
            JsonHelper.sendError(exchange, 404, e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            JsonHelper.sendError(exchange, 500, "Database error");
        }
    }

    private UUID parseUUID(HttpExchange exchange, String uuidString) throws IOException {
        if (!UUIDGenerator.isValidUUID(uuidString)) {
            JsonHelper.sendError(exchange, 400, "Invalid UUID format");
            return null;
        }
        return UUID.fromString(uuidString);
    }
}
