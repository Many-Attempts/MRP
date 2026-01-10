package org.example.services;

import org.example.exceptions.ConflictException;
import org.example.exceptions.NotFoundException;
import org.example.repositories.FavoriteRepository;
import org.example.repositories.MediaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FavoriteService Tests")
class FavoriteServiceTest {

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private MediaRepository mediaRepository;

    private FavoriteService favoriteService;

    private UUID userId;
    private UUID mediaId;

    @BeforeEach
    void setUp() {
        favoriteService = new FavoriteService(favoriteRepository, mediaRepository);
        userId = UUID.randomUUID();
        mediaId = UUID.randomUUID();
    }

    @Test
    @DisplayName("addFavorite() for non-existent media throws NotFoundException")
    void addFavorite_MediaNotFound_ThrowsNotFoundException() throws SQLException {
        when(mediaRepository.existsById(mediaId)).thenReturn(false);

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> favoriteService.addFavorite(userId, mediaId));
        assertEquals("Media not found", ex.getMessage());
    }

    @Test
    @DisplayName("addFavorite() when already favorited throws ConflictException")
    void addFavorite_AlreadyFavorited_ThrowsConflictException() throws SQLException {
        when(mediaRepository.existsById(mediaId)).thenReturn(true);
        when(favoriteRepository.exists(userId, mediaId)).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> favoriteService.addFavorite(userId, mediaId));
        assertEquals("Already in favorites", ex.getMessage());
    }

    @Test
    @DisplayName("removeFavorite() when not in favorites throws NotFoundException")
    void removeFavorite_NotInFavorites_ThrowsNotFoundException() throws SQLException {
        when(favoriteRepository.remove(userId, mediaId)).thenReturn(0);

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> favoriteService.removeFavorite(userId, mediaId));
        assertEquals("Not in favorites", ex.getMessage());
    }
}
