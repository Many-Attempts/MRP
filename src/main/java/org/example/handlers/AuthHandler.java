package org.example.handlers;

import com.fasterxml.jackson.core.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.example.exceptions.ConflictException;
import org.example.exceptions.UnauthorizedException;
import org.example.exceptions.ValidationException;
import org.example.models.User;
import org.example.services.AuthService;
import org.example.utils.JsonHelper;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AuthHandler implements HttpHandler {
    private final AuthService authService = new AuthService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            if (path.endsWith("/register") && "POST".equals(method)) {
                handleRegister(exchange);
            } else if (path.endsWith("/login") && "POST".equals(method)) {
                handleLogin(exchange);
            } else {
                JsonHelper.sendError(exchange, 404, "Endpoint not found");
            }
        } catch (Exception e) {
            e.printStackTrace();
            JsonHelper.sendError(exchange, 500, "Internal server error");
        }
    }

    private void handleRegister(HttpExchange exchange) throws IOException {
        Map<String, String> request;
        try {
            request = JsonHelper.parseRequest(exchange, HashMap.class);
        } catch (JsonParseException e) {
            JsonHelper.sendError(exchange, 400, "Invalid JSON format");
            return;
        }

        try {
            Map<String, Object> response = authService.register(
                request.get("username"),
                request.get("password")
            );
            JsonHelper.sendResponse(exchange, 201, response);
        } catch (ValidationException e) {
            JsonHelper.sendError(exchange, 400, e.getMessage());
        } catch (ConflictException e) {
            JsonHelper.sendError(exchange, 400, e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            JsonHelper.sendError(exchange, 500, "Database error");
        }
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        Map<String, String> request;
        try {
            request = JsonHelper.parseRequest(exchange, HashMap.class);
        } catch (JsonParseException e) {
            JsonHelper.sendError(exchange, 400, "Invalid JSON format");
            return;
        }

        try {
            Map<String, Object> response = authService.login(
                request.get("username"),
                request.get("password")
            );
            JsonHelper.sendResponse(exchange, 200, response);
        } catch (ValidationException e) {
            JsonHelper.sendError(exchange, 400, e.getMessage());
        } catch (UnauthorizedException e) {
            JsonHelper.sendError(exchange, 401, e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            JsonHelper.sendError(exchange, 500, "Database error");
        }
    }

    // Public method for other handlers to validate tokens
    public UUID validateToken(HttpExchange exchange) throws SQLException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        return authService.validateToken(authHeader);
    }

    // Public method to get user from token
    public User getUserFromToken(String authHeader) throws SQLException {
        return authService.getUserFromToken(authHeader);
    }
}
