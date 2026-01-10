package org.example.services;

import org.example.exceptions.NotFoundException;
import org.example.exceptions.UnauthorizedException;
import org.example.exceptions.ValidationException;
import org.example.models.MediaEntry;
import org.example.models.Rating;
import org.example.repositories.MediaRepository;
import org.example.repositories.RatingRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MediaService {
    private final MediaRepository mediaRepository;
    private final RatingRepository ratingRepository;

    public MediaService() {
        this.mediaRepository = new MediaRepository();
        this.ratingRepository = new RatingRepository();
    }

    public MediaService(MediaRepository mediaRepository, RatingRepository ratingRepository) {
        this.mediaRepository = mediaRepository;
        this.ratingRepository = ratingRepository;
    }

    public List<MediaEntry> getAllMedia(Map<String, String> filters, String sortBy) throws SQLException {
        // Validate year if present
        if (filters.containsKey("year")) {
            try {
                Integer.parseInt(filters.get("year"));
            } catch (NumberFormatException e) {
                throw new ValidationException("Invalid year parameter");
            }
        }

        return mediaRepository.findAll(filters, sortBy != null ? sortBy : "title");
    }

    public MediaEntry getMediaById(UUID mediaId, UUID currentUserId) throws SQLException {
        MediaEntry media = mediaRepository.findById(mediaId);

        if (media == null) {
            throw new NotFoundException("Media not found");
        }

        // Load ratings
        List<Rating> ratings = ratingRepository.findByMediaId(mediaId, currentUserId);
        media.setRatings(ratings);

        return media;
    }

    public MediaEntry createMedia(MediaEntry media, UUID creatorId) throws SQLException {
        // Validate
        validateMedia(media);

        return mediaRepository.create(media, creatorId);
    }

    public MediaEntry updateMedia(UUID mediaId, MediaEntry media, UUID userId) throws SQLException {
        // Check if media exists and user is creator
        UUID creatorId = mediaRepository.getCreatorId(mediaId);

        if (creatorId == null) {
            throw new NotFoundException("Media not found");
        }

        if (!creatorId.equals(userId)) {
            throw new UnauthorizedException("Only the creator can edit this media");
        }

        // Validate
        validateMedia(media);

        media.setId(mediaId);
        int updated = mediaRepository.update(media);

        if (updated == 0) {
            throw new RuntimeException("Failed to update media");
        }

        return media;
    }

    public void deleteMedia(UUID mediaId, UUID userId) throws SQLException {
        // Check if media exists and user is creator
        UUID creatorId = mediaRepository.getCreatorId(mediaId);

        if (creatorId == null) {
            throw new NotFoundException("Media not found");
        }

        if (!creatorId.equals(userId)) {
            throw new UnauthorizedException("Only the creator can delete this media");
        }

        int deleted = mediaRepository.delete(mediaId);

        if (deleted == 0) {
            throw new RuntimeException("Failed to delete media");
        }
    }

    private void validateMedia(MediaEntry media) {
        if (media.getTitle() == null || media.getTitle().trim().isEmpty()) {
            throw new ValidationException("Title is required");
        }

        if (media.getMediaType() == null ||
            (!media.getMediaType().equals("movie") &&
             !media.getMediaType().equals("series") &&
             !media.getMediaType().equals("game"))) {
            throw new ValidationException("Media type must be 'movie', 'series', or 'game'");
        }
    }
}
