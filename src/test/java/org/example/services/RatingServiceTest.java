package org.example.services;

import org.example.exceptions.ConflictException;
import org.example.exceptions.NotFoundException;
import org.example.exceptions.UnauthorizedException;
import org.example.exceptions.ValidationException;
import org.example.models.Rating;
import org.example.repositories.MediaRepository;
import org.example.repositories.RatingLikeRepository;
import org.example.repositories.RatingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RatingService Tests")
class RatingServiceTest {

    @Mock
    private RatingRepository ratingRepository;

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private RatingLikeRepository ratingLikeRepository;

    private RatingService ratingService;

    private UUID mediaId;
    private UUID userId;
    private UUID ratingId;

    @BeforeEach
    void setUp() {
        ratingService = new RatingService(ratingRepository, mediaRepository, ratingLikeRepository);
        mediaId = UUID.randomUUID();
        userId = UUID.randomUUID();
        ratingId = UUID.randomUUID();
    }

    @Test
    @DisplayName("createRating() with valid data returns rating")
    void createRating_WithValidData_ReturnsRating() throws SQLException {
        Rating mockRating = new Rating();
        mockRating.setId(ratingId);
        mockRating.setStars(5);

        when(mediaRepository.existsById(mediaId)).thenReturn(true);
        when(ratingRepository.existsByMediaAndUser(mediaId, userId)).thenReturn(false);
        when(ratingRepository.create(eq(mediaId), eq(userId), eq(5), eq("Great!"), eq(false))).thenReturn(mockRating);

        Rating result = ratingService.createRating(mediaId, userId, 5, "Great!");

        assertNotNull(result);
        assertEquals(5, result.getStars());
        verify(ratingRepository).create(mediaId, userId, 5, "Great!", false);
    }

    @Test
    @DisplayName("createRating() for non-existent media throws NotFoundException")
    void createRating_MediaNotFound_ThrowsNotFoundException() throws SQLException {
        when(mediaRepository.existsById(mediaId)).thenReturn(false);

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> ratingService.createRating(mediaId, userId, 5, null));
        assertEquals("Media not found", ex.getMessage());
    }

    @Test
    @DisplayName("createRating() when already rated throws ConflictException")
    void createRating_AlreadyRated_ThrowsConflictException() throws SQLException {
        when(mediaRepository.existsById(mediaId)).thenReturn(true);
        when(ratingRepository.existsByMediaAndUser(mediaId, userId)).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> ratingService.createRating(mediaId, userId, 5, null));
        assertEquals("You have already rated this media", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 6, 10, 100})
    @DisplayName("createRating() with invalid stars throws ValidationException")
    void createRating_InvalidStars_ThrowsValidationException(int invalidStars) throws SQLException {
        when(mediaRepository.existsById(mediaId)).thenReturn(true);
        when(ratingRepository.existsByMediaAndUser(mediaId, userId)).thenReturn(false);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> ratingService.createRating(mediaId, userId, invalidStars, null));
        assertEquals("Stars must be between 1 and 5", ex.getMessage());
    }

    @Test
    @DisplayName("createRating() without comment sets isConfirmed to true")
    void createRating_WithoutComment_IsAutoConfirmed() throws SQLException {
        Rating mockRating = new Rating();
        mockRating.setId(ratingId);

        when(mediaRepository.existsById(mediaId)).thenReturn(true);
        when(ratingRepository.existsByMediaAndUser(mediaId, userId)).thenReturn(false);
        when(ratingRepository.create(eq(mediaId), eq(userId), eq(4), isNull(), eq(true))).thenReturn(mockRating);

        ratingService.createRating(mediaId, userId, 4, null);

        verify(ratingRepository).create(mediaId, userId, 4, null, true);
    }

    @Test
    @DisplayName("updateRating() by non-owner throws UnauthorizedException")
    void updateRating_NotOwner_ThrowsUnauthorizedException() throws SQLException {
        UUID ownerId = UUID.randomUUID();
        UUID differentUserId = UUID.randomUUID();

        when(ratingRepository.getUserIdByRatingId(ratingId)).thenReturn(ownerId);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> ratingService.updateRating(ratingId, differentUserId, 4, null));
        assertEquals("You can only edit your own ratings", ex.getMessage());
    }

    @Test
    @DisplayName("likeRating() when already liked throws ConflictException")
    void likeRating_AlreadyLiked_ThrowsConflictException() throws SQLException {
        when(ratingRepository.existsById(ratingId)).thenReturn(true);
        when(ratingLikeRepository.exists(ratingId, userId)).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> ratingService.likeRating(ratingId, userId));
        assertEquals("Already liked this rating", ex.getMessage());
    }
}
