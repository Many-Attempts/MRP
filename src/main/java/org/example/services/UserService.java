package org.example.services;

import org.example.db.Database;
import org.example.exceptions.ConflictException;
import org.example.exceptions.NotFoundException;
import org.example.exceptions.UnauthorizedException;
import org.example.exceptions.ValidationException;
import org.example.models.MediaEntry;
import org.example.models.Rating;
import org.example.models.User;
import org.example.repositories.FavoriteRepository;
import org.example.repositories.RatingRepository;
import org.example.repositories.UserRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UserService {
    private final UserRepository userRepository = new UserRepository();
    private final FavoriteRepository favoriteRepository = new FavoriteRepository();
    private final RatingRepository ratingRepository = new RatingRepository();
    private final Database db = Database.getInstance();

    public User getUserProfile(String username) throws SQLException {
        User user = userRepository.findByUsername(username);

        if (user == null) {
            throw new NotFoundException("User not found");
        }

        // Load statistics
        user.setTotalRatings(userRepository.getTotalRatings(user.getId()));
        user.setTotalFavorites(userRepository.getTotalFavorites(user.getId()));
        user.setTotalMediaCreated(userRepository.getTotalMediaCreated(user.getId()));
        user.setAverageScore(userRepository.getAverageScore(user.getId()));
        user.setFavoriteGenre(calculateFavoriteGenre(user.getId()));

        return user;
    }

    private String calculateFavoriteGenre(UUID userId) throws SQLException {
        // Get all genres from media the user has rated
        ResultSet rs = db.query(
            "SELECT m.genres FROM ratings r " +
            "JOIN media_entries m ON r.media_id = m.id " +
            "WHERE r.user_id = ? AND m.genres IS NOT NULL",
            userId
        );

        // Count genre occurrences
        Map<String, Integer> genreCounts = new HashMap<>();
        while (rs.next()) {
            String genres = rs.getString("genres");
            if (genres != null && !genres.isEmpty()) {
                for (String genre : genres.split(",")) {
                    genre = genre.trim().toLowerCase();
                    if (!genre.isEmpty()) {
                        genreCounts.put(genre, genreCounts.getOrDefault(genre, 0) + 1);
                    }
                }
            }
        }

        // Find the most common genre
        return genreCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    public User updateProfile(String username, UUID requesterId, Map<String, Object> updates) throws SQLException {
        User user = userRepository.findByUsername(username);

        if (user == null) {
            throw new NotFoundException("User not found");
        }

        // Only the user can edit their own profile
        if (!user.getId().equals(requesterId)) {
            throw new UnauthorizedException("You can only edit your own profile");
        }

        // Update username if provided
        if (updates.containsKey("username")) {
            String newUsername = (String) updates.get("username");

            if (newUsername == null || newUsername.trim().isEmpty()) {
                throw new ValidationException("Username cannot be empty");
            }

            if (newUsername.length() < 3 || newUsername.length() > 50) {
                throw new ValidationException("Username must be between 3 and 50 characters");
            }

            // Check if new username is already taken (by another user)
            if (!newUsername.equals(username) && userRepository.existsByUsername(newUsername)) {
                throw new ConflictException("Username already taken");
            }

            userRepository.updateUsername(user.getId(), newUsername);
        }

        // Return updated profile
        String updatedUsername = updates.containsKey("username") ? (String) updates.get("username") : username;
        return getUserProfile(updatedUsername);
    }

    public User getUserById(UUID userId) throws SQLException {
        User user = userRepository.findById(userId);

        if (user == null) {
            throw new NotFoundException("User not found");
        }

        return user;
    }

    public List<MediaEntry> getUserFavorites(String username) throws SQLException {
        User user = userRepository.findByUsername(username);

        if (user == null) {
            throw new NotFoundException("User not found");
        }

        return favoriteRepository.findByUserId(user.getId());
    }

    public List<Rating> getUserRatings(String username, UUID requesterId) throws SQLException {
        User user = userRepository.findByUsername(username);

        if (user == null) {
            throw new NotFoundException("User not found");
        }

        return ratingRepository.findByUserId(user.getId(), requesterId);
    }
}
