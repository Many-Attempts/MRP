package org.example.services;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.example.exceptions.ConflictException;
import org.example.exceptions.UnauthorizedException;
import org.example.exceptions.ValidationException;
import org.example.models.User;
import org.example.repositories.AuthTokenRepository;
import org.example.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthTokenRepository authTokenRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, authTokenRepository);
    }

    @Test
    @DisplayName("register() with valid credentials creates user successfully")
    void register_WithValidCredentials_CreatesUser() throws SQLException {
        User mockUser = new User();
        mockUser.setId(UUID.randomUUID());
        mockUser.setUsername("validuser");

        when(userRepository.existsByUsername("validuser")).thenReturn(false);
        when(userRepository.create(eq("validuser"), anyString())).thenReturn(mockUser);

        Map<String, Object> result = authService.register("validuser", "validpassword");

        assertNotNull(result);
        assertEquals("validuser", result.get("username"));
        assertEquals("User registered successfully", result.get("message"));
        verify(userRepository).create(eq("validuser"), anyString());
    }

    @Test
    @DisplayName("register() with empty username throws ValidationException")
    void register_WithEmptyUsername_ThrowsValidationException() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> authService.register("", "validpassword"));
        assertEquals("Username and password are required", ex.getMessage());
    }

    @Test
    @DisplayName("register() with short username throws ValidationException")
    void register_WithShortUsername_ThrowsValidationException() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> authService.register("ab", "validpassword"));
        assertEquals("Username must be between 3 and 50 characters", ex.getMessage());
    }

    @Test
    @DisplayName("register() with short password throws ValidationException")
    void register_WithShortPassword_ThrowsValidationException() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> authService.register("validuser", "12345"));
        assertEquals("Password must be at least 6 characters", ex.getMessage());
    }

    @Test
    @DisplayName("register() with existing username throws ConflictException")
    void register_WithExistingUsername_ThrowsConflictException() throws SQLException {
        when(userRepository.existsByUsername("takenuser")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> authService.register("takenuser", "validpassword"));
        assertEquals("Username already exists", ex.getMessage());
    }

    @Test
    @DisplayName("login() with wrong password throws UnauthorizedException")
    void login_WithWrongPassword_ThrowsUnauthorizedException() throws SQLException {
        User mockUser = new User();
        mockUser.setId(UUID.randomUUID());
        mockUser.setUsername("testuser");
        mockUser.setPasswordHash(BCrypt.withDefaults().hashToString(12, "correctpassword".toCharArray()));

        when(userRepository.findByUsername("testuser")).thenReturn(mockUser);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> authService.login("testuser", "wrongpassword"));
        assertEquals("Invalid username or password", ex.getMessage());
    }
}
