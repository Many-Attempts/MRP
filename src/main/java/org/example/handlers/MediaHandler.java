package org.example.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.example.exceptions.ConflictException;
import org.example.exceptions.NotFoundException;
import org.example.exceptions.UnauthorizedException;
import org.example.exceptions.ValidationException;
import org.example.models.MediaEntry;
import org.example.services.FavoriteService;
import org.example.services.MediaService;
import org.example.utils.JsonHelper;
import org.example.utils.UUIDGenerator;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MediaHandler implements HttpHandler {
    private final MediaService mediaService = new MediaService();
    private final FavoriteService favoriteService = new FavoriteService();
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
                    handleGetMediaList(exchange, query);
                } else if ("POST".equals(method)) {
                    handleCreateMedia(exchange, userId);
                } else {
                    JsonHelper.sendError(exchange, 405, "Method not allowed");
                }
            }
            // /api/media/{id}
            else if (segments.length == 3) {
                String mediaId = segments[2];
                UUID mediaUUID = parseUUID(exchange, mediaId);
                if (mediaUUID == null) return;

                if ("GET".equals(method)) {
                    handleGetMedia(exchange, mediaUUID, userId);
                } else if ("PUT".equals(method)) {
                    handleUpdateMedia(exchange, mediaUUID, userId);
                } else if ("DELETE".equals(method)) {
                    handleDeleteMedia(exchange, mediaUUID, userId);
                } else {
                    JsonHelper.sendError(exchange, 405, "Method not allowed");
                }
            }
            // /api/media/{id}/favorite
            else if (segments.length == 4 && "favorite".equals(segments[3])) {
                String mediaId = segments[2];
                UUID mediaUUID = parseUUID(exchange, mediaId);
                if (mediaUUID == null) return;

                if ("POST".equals(method)) {
                    handleAddFavorite(exchange, mediaUUID, userId);
                } else if ("DELETE".equals(method)) {
                    handleRemoveFavorite(exchange, mediaUUID, userId);
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

    private void handleGetMediaList(HttpExchange exchange, String query) throws IOException {
        Map<String, String> params = JsonHelper.parseQueryParams(query);

        try {
            String sortBy = params.getOrDefault("sort", "title");
            List<MediaEntry> mediaList = mediaService.getAllMedia(params, sortBy);
            JsonHelper.sendResponse(exchange, 200, mediaList);
        } catch (ValidationException e) {
            JsonHelper.sendError(exchange, 400, e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            JsonHelper.sendError(exchange, 500, "Database error");
        }
    }

    private void handleGetMedia(HttpExchange exchange, UUID mediaId, UUID userId) throws IOException {
        try {
            MediaEntry media = mediaService.getMediaById(mediaId, userId);
            JsonHelper.sendResponse(exchange, 200, media);
        } catch (NotFoundException e) {
            JsonHelper.sendError(exchange, 404, e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            JsonHelper.sendError(exchange, 500, "Database error");
        }
    }

    private void handleCreateMedia(HttpExchange exchange, UUID userId) throws IOException {
        try {
            MediaEntry media = JsonHelper.parseRequest(exchange, MediaEntry.class);
            MediaEntry created = mediaService.createMedia(media, userId);
            JsonHelper.sendResponse(exchange, 201, created);
        } catch (ValidationException e) {
            JsonHelper.sendError(exchange, 400, e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            JsonHelper.sendError(exchange, 500, "Database error");
        }
    }

    private void handleUpdateMedia(HttpExchange exchange, UUID mediaId, UUID userId) throws IOException {
        try {
            MediaEntry media = JsonHelper.parseRequest(exchange, MediaEntry.class);
            MediaEntry updated = mediaService.updateMedia(mediaId, media, userId);
            JsonHelper.sendResponse(exchange, 200, updated);
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

    private void handleDeleteMedia(HttpExchange exchange, UUID mediaId, UUID userId) throws IOException {
        try {
            mediaService.deleteMedia(mediaId, userId);
            JsonHelper.sendSuccess(exchange, "Media deleted successfully");
        } catch (NotFoundException e) {
            JsonHelper.sendError(exchange, 404, e.getMessage());
        } catch (UnauthorizedException e) {
            JsonHelper.sendError(exchange, 403, e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            JsonHelper.sendError(exchange, 500, "Database error");
        }
    }

    private void handleAddFavorite(HttpExchange exchange, UUID mediaId, UUID userId) throws IOException {
        try {
            favoriteService.addFavorite(userId, mediaId);
            JsonHelper.sendSuccess(exchange, "Added to favorites");
        } catch (NotFoundException e) {
            JsonHelper.sendError(exchange, 404, e.getMessage());
        } catch (ConflictException e) {
            JsonHelper.sendError(exchange, 400, e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            JsonHelper.sendError(exchange, 500, "Database error");
        }
    }

    private void handleRemoveFavorite(HttpExchange exchange, UUID mediaId, UUID userId) throws IOException {
        try {
            favoriteService.removeFavorite(userId, mediaId);
            JsonHelper.sendSuccess(exchange, "Removed from favorites");
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
