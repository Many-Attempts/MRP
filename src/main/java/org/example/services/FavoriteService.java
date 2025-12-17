package org.example.services;

import org.example.exceptions.ConflictException;
import org.example.exceptions.NotFoundException;
import org.example.models.MediaEntry;
import org.example.repositories.FavoriteRepository;
import org.example.repositories.MediaRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class FavoriteService {
    private final FavoriteRepository favoriteRepository = new FavoriteRepository();
    private final MediaRepository mediaRepository = new MediaRepository();

    public void addFavorite(UUID userId, UUID mediaId) throws SQLException {
        // Check if media exists
        if (!mediaRepository.existsById(mediaId)) {
            throw new NotFoundException("Media not found");
        }

        // Check if already favorited
        if (favoriteRepository.exists(userId, mediaId)) {
            throw new ConflictException("Already in favorites");
        }

        favoriteRepository.add(userId, mediaId);
    }

    public void removeFavorite(UUID userId, UUID mediaId) throws SQLException {
        int deleted = favoriteRepository.remove(userId, mediaId);

        if (deleted == 0) {
            throw new NotFoundException("Not in favorites");
        }
    }

    public List<MediaEntry> getUserFavorites(UUID userId) throws SQLException {
        return favoriteRepository.findByUserId(userId);
    }
}
