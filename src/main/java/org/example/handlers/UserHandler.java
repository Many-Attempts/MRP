package org.example.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.example.db.Database;
import org.example.utils.JsonHelper;

import java.io.IOException;
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
                    handleGetUserRatings(exchange, username, String.valueOf(userId));
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
                handleGetRecommendations(exchange, String.valueOf(userId));
            } else {
                JsonHelper.sendError(exchange, 404, "Endpoint not found");
            }
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendError(exchange, 500, "Internal server error");
        }
    }

    private void handleGetProfile(HttpExchange exchange, String username) throws IOException {
        JsonHelper.sendSuccess(exchange, "Will be implemented");
    }

    private void handleGetFavorites(HttpExchange exchange, String username) throws IOException {
        JsonHelper.sendSuccess(exchange, "Will be implemented");
    }

    private void handleGetUserRatings(HttpExchange exchange, String username, String currentUserId) throws IOException {
        JsonHelper.sendSuccess(exchange, "Will be implemented");
    }

    private void handleGetLeaderboard(HttpExchange exchange) throws IOException {
        JsonHelper.sendSuccess(exchange, "Will be implemented");
    }

    private void handleGetRecommendations(HttpExchange exchange, String userId) throws IOException {
        JsonHelper.sendSuccess(exchange, "Will be implemented");
    }
}
