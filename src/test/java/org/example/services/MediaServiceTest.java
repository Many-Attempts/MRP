package org.example.services;

import org.example.exceptions.NotFoundException;
import org.example.exceptions.UnauthorizedException;
import org.example.exceptions.ValidationException;
import org.example.models.MediaEntry;
import org.example.repositories.MediaRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MediaService Tests")
class MediaServiceTest {

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private RatingRepository ratingRepository;

    private MediaService mediaService;

    private UUID mediaId;
    private UUID userId;
    private UUID creatorId;

    @BeforeEach
    void setUp() {
        mediaService = new MediaService(mediaRepository, ratingRepository);
        mediaId = UUID.randomUUID();
        userId = UUID.randomUUID();
        creatorId = UUID.randomUUID();
    }

    @Test
    @DisplayName("createMedia() with valid data returns media")
    void createMedia_WithValidData_ReturnsMedia() throws SQLException {
        MediaEntry media = new MediaEntry();
        media.setTitle("Test Movie");
        media.setMediaType("movie");

        MediaEntry createdMedia = new MediaEntry();
        createdMedia.setId(mediaId);
        createdMedia.setTitle("Test Movie");
        createdMedia.setMediaType("movie");

        when(mediaRepository.create(any(MediaEntry.class), eq(userId))).thenReturn(createdMedia);

        MediaEntry result = mediaService.createMedia(media, userId);

        assertNotNull(result);
        assertEquals("Test Movie", result.getTitle());
        verify(mediaRepository).create(media, userId);
    }

    @Test
    @DisplayName("createMedia() with missing title throws ValidationException")
    void createMedia_MissingTitle_ThrowsValidationException() {
        MediaEntry media = new MediaEntry();
        media.setMediaType("movie");

        ValidationException ex = assertThrows(ValidationException.class,
                () -> mediaService.createMedia(media, userId));
        assertEquals("Title is required", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "book", "music", ""})
    @DisplayName("createMedia() with invalid type throws ValidationException")
    void createMedia_InvalidType_ThrowsValidationException(String invalidType) {
        MediaEntry media = new MediaEntry();
        media.setTitle("Test");
        media.setMediaType(invalidType);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> mediaService.createMedia(media, userId));
        assertEquals("Media type must be 'movie', 'series', or 'game'", ex.getMessage());
    }

    @Test
    @DisplayName("updateMedia() by non-creator throws UnauthorizedException")
    void updateMedia_NotCreator_ThrowsUnauthorizedException() throws SQLException {
        UUID differentUserId = UUID.randomUUID();

        when(mediaRepository.getCreatorId(mediaId)).thenReturn(creatorId);

        MediaEntry media = new MediaEntry();
        media.setTitle("Updated Title");
        media.setMediaType("movie");

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> mediaService.updateMedia(mediaId, media, differentUserId));
        assertEquals("Only the creator can edit this media", ex.getMessage());
    }

    @Test
    @DisplayName("deleteMedia() by non-creator throws UnauthorizedException")
    void deleteMedia_NotCreator_ThrowsUnauthorizedException() throws SQLException {
        UUID differentUserId = UUID.randomUUID();

        when(mediaRepository.getCreatorId(mediaId)).thenReturn(creatorId);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> mediaService.deleteMedia(mediaId, differentUserId));
        assertEquals("Only the creator can delete this media", ex.getMessage());
    }
}
