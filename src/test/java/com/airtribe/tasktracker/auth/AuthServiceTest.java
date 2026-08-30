package com.airtribe.tasktracker.auth;

import com.airtribe.tasktracker.auth.dto.AuthResponse;
import com.airtribe.tasktracker.common.exception.UnauthorizedException;
import com.airtribe.tasktracker.security.JwtService;
import com.airtribe.tasktracker.user.User;
import com.airtribe.tasktracker.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserService userService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    private User sampleUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Alice");
        user.setEmail("alice@example.com");
        user.setPasswordHash("hashed-pw");
        return user;
    }

    @Test
    void registerHashesPasswordAndIssuesTokens() {
        User created = sampleUser();
        when(passwordEncoder.encode("plaintext")).thenReturn("hashed-pw");
        when(userService.createUser("Alice", "alice@example.com", "hashed-pw")).thenReturn(created);
        when(jwtService.generateAccessToken(created)).thenReturn("access-token");
        when(refreshTokenService.issue(created)).thenReturn("refresh-token");

        AuthResponse response = authService.register("Alice", "alice@example.com", "plaintext");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().email()).isEqualTo("alice@example.com");
    }

    @Test
    void loginSucceedsWithCorrectPassword() {
        User user = sampleUser();
        when(userService.findByEmail("alice@example.com")).thenReturn(user);
        when(passwordEncoder.matches("plaintext", "hashed-pw")).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(refreshTokenService.issue(user)).thenReturn("refresh-token");

        AuthResponse response = authService.login("alice@example.com", "plaintext");

        assertThat(response.accessToken()).isEqualTo("access-token");
    }

    @Test
    void loginFailsWithWrongPassword() {
        User user = sampleUser();
        when(userService.findByEmail("alice@example.com")).thenReturn(user);
        when(passwordEncoder.matches("wrong", "hashed-pw")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("alice@example.com", "wrong"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void loginFailsWithUnknownEmailUsingSameMessageAsWrongPassword() {
        when(userService.findByEmail("nobody@example.com"))
                .thenThrow(new com.airtribe.tasktracker.common.exception.NotFoundException("User not found."));

        assertThatThrownBy(() -> authService.login("nobody@example.com", "whatever"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password.");
    }

    @Test
    void refreshDelegatesToRefreshTokenServiceAndIssuesNewAccessToken() {
        User user = sampleUser();
        when(refreshTokenService.rotate("old-raw"))
                .thenReturn(new RefreshTokenService.RotationResult(user, "new-raw"));
        when(jwtService.generateAccessToken(user)).thenReturn("new-access");

        AuthResponse response = authService.refresh("old-raw");

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-raw");
    }

    @Test
    void logoutRevokesTheGivenRefreshToken() {
        authService.logout("some-raw");

        verify(refreshTokenService).revoke("some-raw");
    }
}
