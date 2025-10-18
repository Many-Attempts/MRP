package org.example.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.example.db.Database;
import org.example.utils.JsonHelper;

import java.io.IOException;
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

    private void handleCreateRating(HttpExchange exchange, String mediaId, UUID userId) throws IOException {
        JsonHelper.sendSuccess(exchange, "Will be implemented");
    }

    private void handleUpdateRating(HttpExchange exchange, String ratingId, UUID userId) throws IOException {
        JsonHelper.sendSuccess(exchange, "Will be implemented");
    }

    private void handleDeleteRating(HttpExchange exchange, String ratingId, UUID userId) throws IOException {
        JsonHelper.sendSuccess(exchange, "Will be implemented");
    }

    private void handleConfirmComment(HttpExchange exchange, String ratingId, UUID userId) throws IOException {
        JsonHelper.sendSuccess(exchange, "Will be implemented");
    }

    private void handleLikeRating(HttpExchange exchange, String ratingId, UUID userId) throws IOException {
        JsonHelper.sendSuccess(exchange, "Will be implemented");
    }

    private void handleUnlikeRating(HttpExchange exchange, String ratingId, UUID userId) throws IOException {
        JsonHelper.sendSuccess(exchange, "Will be implemented");
    }
}
