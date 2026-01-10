package org.example.services;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.example.exceptions.ConflictException;
import org.example.exceptions.UnauthorizedException;
import org.example.exceptions.ValidationException;
import org.example.models.User;
import org.example.repositories.AuthTokenRepository;
import org.example.repositories.UserRepository;
import org.example.utils.UUIDGenerator;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AuthService {
    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;

    public AuthService() {
        this.userRepository = new UserRepository();
        this.authTokenRepository = new AuthTokenRepository();
    }

    public AuthService(UserRepository userRepository, AuthTokenRepository authTokenRepository) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
    }

    public Map<String, Object> register(String username, String password) throws SQLException {
        // Validation
        if (username == null || username.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            throw new ValidationException("Username and password are required");
        }

        if (username.length() < 3 || username.length() > 50) {
            throw new ValidationException("Username must be between 3 and 50 characters");
        }

        if (password.length() < 6) {
            throw new ValidationException("Password must be at least 6 characters");
        }

        // Check if username exists
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username already exists");
        }

        // Hash password
        String passwordHash = BCrypt.withDefaults().hashToString(12, password.toCharArray());

        // Create user
        User user = userRepository.create(username, passwordHash);

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("username", user.getUsername());
        response.put("message", "User registered successfully");

        return response;
    }

    public Map<String, Object> login(String username, String password) throws SQLException {
        // Validation
        if (username == null || username.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            throw new ValidationException("Username and password are required");
        }

        // Find user
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new UnauthorizedException("Invalid username or password");
        }

        // Verify password
        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), user.getPasswordHash());
        if (!result.verified) {
            throw new UnauthorizedException("Invalid username or password");
        }

        // Generate token
        String token = UUIDGenerator.generateUUIDv7().toString();

        // Store token
        authTokenRepository.saveToken(token, user.getId());

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("username", username);
        response.put("userId", user.getId());
        response.put("message", "Login successful");

        return response;
    }

    public UUID validateToken(String authHeader) throws SQLException {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }

        String token = authHeader.substring(7);
        return authTokenRepository.findUserIdByToken(token);
    }

    public User getUserFromToken(String authHeader) throws SQLException {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }

        String token = authHeader.substring(7);
        UUID userId = authTokenRepository.findUserIdByToken(token);

        if (userId == null) {
            return null;
        }

        return userRepository.findById(userId);
    }
}
