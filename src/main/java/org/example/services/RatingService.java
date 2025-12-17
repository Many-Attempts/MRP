package org.example.services;

import org.example.exceptions.ConflictException;
import org.example.exceptions.NotFoundException;
import org.example.exceptions.UnauthorizedException;
import org.example.exceptions.ValidationException;
import org.example.models.Rating;
import org.example.repositories.MediaRepository;
import org.example.repositories.RatingLikeRepository;
import org.example.repositories.RatingRepository;

import java.sql.SQLException;
import java.util.UUID;

public class RatingService {
    private final RatingRepository ratingRepository = new RatingRepository();
    private final MediaRepository mediaRepository = new MediaRepository();
    private final RatingLikeRepository ratingLikeRepository = new RatingLikeRepository();

    public Rating createRating(UUID mediaId, UUID userId, Integer stars, String comment) throws SQLException {
        // Check if media exists
        if (!mediaRepository.existsById(mediaId)) {
            throw new NotFoundException("Media not found");
        }

        // Check if user already rated
        if (ratingRepository.existsByMediaAndUser(mediaId, userId)) {
            throw new ConflictException("You have already rated this media");
        }

        // Validate stars
        if (stars == null || stars < 1 || stars > 5) {
            throw new ValidationException("Stars must be between 1 and 5");
        }

        // Auto-confirm if no comment
        boolean isConfirmed = (comment == null || comment.trim().isEmpty());

        return ratingRepository.create(mediaId, userId, stars, comment, isConfirmed);
    }

    public void updateRating(UUID ratingId, UUID userId, Integer stars, String comment) throws SQLException {
        // Validate ratingId
        if (ratingId == null) {
            throw new ValidationException("Rating ID is required");
        }

        // Check ownership
        UUID ownerId = ratingRepository.getUserIdByRatingId(ratingId);

        if (ownerId == null) {
            throw new NotFoundException("Rating not found");
        }

        if (!ownerId.equals(userId)) {
            throw new UnauthorizedException("You can only edit your own ratings");
        }

        // Validate stars if provided
        if (stars != null && (stars < 1 || stars > 5)) {
            throw new ValidationException("Stars must be between 1 and 5");
        }

        // If comment is updated, reset confirmation
        boolean needsConfirmation = comment != null && !comment.trim().isEmpty();

        ratingRepository.updateStarsAndComment(ratingId, stars, comment, !needsConfirmation);
    }

    public void deleteRating(UUID ratingId, UUID userId) throws SQLException {
        // Validate
        if (ratingId == null) {
            throw new ValidationException("Rating ID is required");
        }

        // Check ownership
        UUID ownerId = ratingRepository.getUserIdByRatingId(ratingId);

        if (ownerId == null) {
            throw new NotFoundException("Rating not found");
        }

        if (!ownerId.equals(userId)) {
            throw new UnauthorizedException("You can only delete your own ratings");
        }

        ratingRepository.delete(ratingId);
    }

    public void confirmRating(UUID ratingId, UUID userId) throws SQLException {
        // Validate
        if (ratingId == null) {
            throw new ValidationException("Rating ID is required");
        }

        // Check ownership
        UUID ownerId = ratingRepository.getUserIdByRatingId(ratingId);

        if (ownerId == null) {
            throw new NotFoundException("Rating not found");
        }

        if (!ownerId.equals(userId)) {
            throw new UnauthorizedException("You can only confirm your own comments");
        }

        ratingRepository.confirmRating(ratingId);
    }

    public void likeRating(UUID ratingId, UUID userId) throws SQLException {
        // Check if rating exists
        if (!ratingRepository.existsById(ratingId)) {
            throw new NotFoundException("Rating not found");
        }

        // Check if already liked
        if (ratingLikeRepository.exists(ratingId, userId)) {
            throw new ConflictException("Already liked this rating");
        }

        ratingLikeRepository.add(ratingId, userId);
    }

    public void unlikeRating(UUID ratingId, UUID userId) throws SQLException {
        int deleted = ratingLikeRepository.remove(ratingId, userId);

        if (deleted == 0) {
            throw new NotFoundException("Like not found");
        }
    }
}
