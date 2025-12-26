package org.example.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.example.exceptions.NotFoundException;
import org.example.models.LeaderboardEntry;
import org.example.models.MediaEntry;
import org.example.models.Rating;
import org.example.models.User;
import org.example.services.LeaderboardService;
import org.example.services.RecommendationService;
import org.example.services.UserService;
import org.example.utils.JsonHelper;

import org.example.exceptions.ConflictException;
import org.example.exceptions.UnauthorizedException;
import org.example.exceptions.ValidationException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UserHandler implements HttpHandler {
    private final UserService userService = new UserService();
    private final LeaderboardService leaderboardService = new LeaderboardService();
    private final RecommendationService recommendationService = new RecommendationService();
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

            // /api/leaderboard
            if (segments.length == 2 && "leaderboard".equals(segments[1]) && "GET".equals(method)) {
                handleGetLeaderboard(exchange);
            }
            // /api/recommendations
            else if (segments.length == 2 && "recommendations".equals(segments[1]) && "GET".equals(method)) {
                handleGetRecommendations(exchange, userId);
            }
            // /api/users/{username}/profile - GET
            else if (segments.length == 4 && "users".equals(segments[1]) && "profile".equals(segments[3]) && "GET".equals(method)) {
                String username = segments[2];
                handleGetProfile(exchange, username);
            }
            // /api/users/{username}/profile - PUT (edit profile)
            else if (segments.length == 4 && "users".equals(segments[1]) && "profile".equals(segments[3]) && "PUT".equals(method)) {
                String username = segments[2];
                handleUpdateProfile(exchange, username, userId);
            }
            // /api/users/{username}/favorites
            else if (segments.length == 4 && "users".equals(segments[1]) && "favorites".equals(segments[3]) && "GET".equals(method)) {
                String username = segments[2];
                handleGetUserFavorites(exchange, username);
            }
            // /api/users/{username}/ratings
            else if (segments.length == 4 && "users".equals(segments[1]) && "ratings".equals(segments[3]) && "GET".equals(method)) {
                String username = segments[2];
                handleGetUserRatings(exchange, username, userId);
            } else {
                JsonHelper.sendError(exchange, 404, "Endpoint not found");
            }
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendError(exchange, 500, "Internal server error");
        }
    }

    private void handleGetProfile(HttpExchange exchange, String username) throws IOException {
        try {
            User user = userService.getUserProfile(username);
            JsonHelper.sendResponse(exchange, 200, user);
        } catch (NotFoundException e) {
            JsonHelper.sendError(exchange, 404, e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            JsonHelper.sendError(exchange, 500, "Database error");
        }
    }

    private void handleUpdateProfile(HttpExchange exchange, String username, UUID userId) throws IOException {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> updates = JsonHelper.parseRequest(exchange, HashMap.class);
            User user = userService.updateProfile(username, userId, updates);
            JsonHelper.sendResponse(exchange, 200, user);
        } catch (ValidationException | ConflictException e) {
            JsonHelper.sendError(exchange, 400, e.getMessage());
        } catch (UnauthorizedException e) {
            JsonHelper.sendError(exchange, 403, e.getMessage());
        } catch (NotFoundException e) {
            JsonHelper.sendError(exchange, 404, e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            JsonHelper.sendError(exchange, 500, "Database error");
        }
    }

    private void handleGetUserFavorites(HttpExchange exchange, String username) throws IOException {
        try {
            List<MediaEntry> favorites = userService.getUserFavorites(username);
            JsonHelper.sendResponse(exchange, 200, favorites);
        } catch (NotFoundException e) {
            JsonHelper.sendError(exchange, 404, e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            JsonHelper.sendError(exchange, 500, "Database error");
        }
    }

    private void handleGetUserRatings(HttpExchange exchange, String username, UUID requesterId) throws IOException {
        try {
            List<Rating> ratings = userService.getUserRatings(username, requesterId);
            JsonHelper.sendResponse(exchange, 200, ratings);
        } catch (NotFoundException e) {
            JsonHelper.sendError(exchange, 404, e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            JsonHelper.sendError(exchange, 500, "Database error");
        }
    }

    private void handleGetLeaderboard(HttpExchange exchange) throws IOException {
        try {
            List<LeaderboardEntry> leaderboard = leaderboardService.getLeaderboard();
            JsonHelper.sendResponse(exchange, 200, leaderboard);
        } catch (SQLException e) {
            e.printStackTrace();
            JsonHelper.sendError(exchange, 500, "Database error");
        }
    }

    private void handleGetRecommendations(HttpExchange exchange, UUID userId) throws IOException {
        try {
            Map<String, Object> recommendations = recommendationService.getRecommendations(userId);
            JsonHelper.sendResponse(exchange, 200, recommendations);
        } catch (SQLException e) {
            e.printStackTrace();
            JsonHelper.sendError(exchange, 500, "Database error");
        }
    }
}
