package org.example.services;

import org.example.exceptions.ConflictException;
import org.example.exceptions.NotFoundException;
import org.example.exceptions.UnauthorizedException;
import org.example.exceptions.ValidationException;
import org.example.models.User;
import org.example.repositories.FavoriteRepository;
import org.example.repositories.RatingRepository;
import org.example.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private RatingRepository ratingRepository;

    private UserService userService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, favoriteRepository, ratingRepository);
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("updateProfile() by non-owner throws UnauthorizedException")
    void updateProfile_NotOwner_ThrowsUnauthorizedException() throws SQLException {
        UUID ownerId = UUID.randomUUID();
        UUID differentUserId = UUID.randomUUID();

        User mockUser = new User();
        mockUser.setId(ownerId);
        mockUser.setUsername("existinguser");

        when(userRepository.findByUsername("existinguser")).thenReturn(mockUser);

        Map<String, Object> updates = new HashMap<>();
        updates.put("username", "newname");

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> userService.updateProfile("existinguser", differentUserId, updates));
        assertEquals("You can only edit your own profile", ex.getMessage());
    }

    @Test
    @DisplayName("updateProfile() with duplicate username throws ConflictException")
    void updateProfile_DuplicateUsername_ThrowsConflictException() throws SQLException {
        User mockUser = new User();
        mockUser.setId(userId);
        mockUser.setUsername("currentuser");

        when(userRepository.findByUsername("currentuser")).thenReturn(mockUser);
        when(userRepository.existsByUsername("takenname")).thenReturn(true);

        Map<String, Object> updates = new HashMap<>();
        updates.put("username", "takenname");

        ConflictException ex = assertThrows(ConflictException.class,
                () -> userService.updateProfile("currentuser", userId, updates));
        assertEquals("Username already taken", ex.getMessage());
    }

    @Test
    @DisplayName("updateProfile() with empty username throws ValidationException")
    void updateProfile_EmptyUsername_ThrowsValidationException() throws SQLException {
        User mockUser = new User();
        mockUser.setId(userId);
        mockUser.setUsername("currentuser");

        when(userRepository.findByUsername("currentuser")).thenReturn(mockUser);

        Map<String, Object> updates = new HashMap<>();
        updates.put("username", "");

        ValidationException ex = assertThrows(ValidationException.class,
                () -> userService.updateProfile("currentuser", userId, updates));
        assertEquals("Username cannot be empty", ex.getMessage());
    }

    @Test
    @DisplayName("getUserProfile() with non-existent user throws NotFoundException")
    void getUserProfile_NotFound_ThrowsNotFoundException() throws SQLException {
        when(userRepository.findByUsername("unknownuser")).thenReturn(null);

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> userService.getUserProfile("unknownuser"));
        assertEquals("User not found", ex.getMessage());
    }
}
