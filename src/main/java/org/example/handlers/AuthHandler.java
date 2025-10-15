package org.example.handlers;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.fasterxml.jackson.core.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.example.db.Database;
import org.example.models.User;
import org.example.utils.JsonHelper;
import org.example.utils.UUIDGenerator;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AuthHandler implements HttpHandler {
    private final Database db = Database.getInstance();

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

    private void handleRegister(HttpExchange exchange) throws IOException, SQLException {
        // Parse JSON request body
        Map<String, String> request;
        try {
            request = JsonHelper.parseRequest(exchange, HashMap.class);
        } catch (JsonParseException e) {
            JsonHelper.sendError(exchange, 400, "Invalid JSON format");
            return;
        }

        String username = request.get("username");
        String password = request.get("password");

        // Validate input
        if (username == null || username.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            JsonHelper.sendError(exchange, 400, "Username and password are required");
            return;
        }

        if (username.length() < 3 || username.length() > 50) {
            JsonHelper.sendError(exchange, 400, "Username must be between 3 and 50 characters");
            return;
        }

        if (password.length() < 6) {
            JsonHelper.sendError(exchange, 400, "Password must be at least 6 characters");
            return;
        }

        // Check if username already exists
        if (db.exists("SELECT 1 FROM users WHERE username = ?", username)) {
            JsonHelper.sendError(exchange, 400, "Username already exists");
            return;
        }

        // Hash password
        String passwordHash = BCrypt.withDefaults().hashToString(12, password.toCharArray());

        // Insert user with UUID
        UUID userId = db.insert(
            "INSERT INTO users (id, username, password_hash) VALUES (?, ?, ?)",
            username, passwordHash
        );

        // Create response
        Map<String, Object> response = new HashMap<>();
        response.put("id", userId);  // Jackson will serialize UUID properly
        response.put("username", username);
        response.put("message", "User registered successfully");

        JsonHelper.sendResponse(exchange, 201, response);
    }

    private void handleLogin(HttpExchange exchange) throws IOException, SQLException {
        // Parse JSON request body
        Map<String, String> request;
        try {
            request = JsonHelper.parseRequest(exchange, HashMap.class);
        } catch (JsonParseException e) {
            JsonHelper.sendError(exchange, 400, "Invalid JSON format");
            return;
        }

        String username = request.get("username");
        String password = request.get("password");

        // Validate input
        if (username == null || username.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            JsonHelper.sendError(exchange, 400, "Username and password are required");
            return;
        }

        // Find user
        ResultSet rs = db.query(
            "SELECT id, password_hash FROM users WHERE username = ?",
            username
        );

        if (!rs.next()) {
            JsonHelper.sendError(exchange, 401, "Invalid username or password");
            return;
        }

        UUID userId = db.getUUID(rs, "id");
        String passwordHash = rs.getString("password_hash");

        // Verify password
        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), passwordHash);
        if (!result.verified) {
            JsonHelper.sendError(exchange, 401, "Invalid username or password");
            return;
        }

        // Generate token
        String token = username + "-" + UUIDGenerator.generateUUIDv7().toString();

        // Store token
        db.update(
            "INSERT INTO auth_tokens (token, user_id) VALUES (?, ?) ON CONFLICT (user_id) DO UPDATE SET token = (?)",
            token, userId, token
        );

        // Create response
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("username", username);
        response.put("userId", userId);  // Jackson will serialize UUID
        response.put("message", "Login successful");

        JsonHelper.sendResponse(exchange, 200, response);
    }

    // Method to validate token
    public UUID validateToken(HttpExchange exchange) throws SQLException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }

        String token = authHeader.substring(7); // Remove "Bearer "

        ResultSet rs = db.query(
            "SELECT user_id FROM auth_tokens WHERE token = ?",
            token
        );

        if (rs.next()) {
            return db.getUUID(rs, "user_id");
        }

        return null;
    }

    // Helper method to get user from token
    public User getUserFromToken(String token) throws SQLException {
        if (token == null || !token.startsWith("Bearer ")) {
            return null;
        }

        token = token.substring(7); // Remove "Bearer "

        ResultSet rs = db.query(
            "SELECT u.* FROM users u " +
            "JOIN auth_tokens t ON t.user_id = u.id " +
            "WHERE t.token = ?",
            token
        );

        if (rs.next()) {
            User user = new User();
            user.setId(db.getUUID(rs, "id"));
            user.setUsername(rs.getString("username"));
            user.setPasswordHash(rs.getString("password_hash"));
            user.setCreatedAt(rs.getTimestamp("created_at"));
            return user;
        }

        return null;
    }
}