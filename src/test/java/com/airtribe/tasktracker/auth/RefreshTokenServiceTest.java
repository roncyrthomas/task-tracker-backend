package com.airtribe.tasktracker.auth;

import com.airtribe.tasktracker.common.exception.UnauthorizedException;
import com.airtribe.tasktracker.security.JwtProperties;
import com.airtribe.tasktracker.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService service() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-key-at-least-32-bytes-long!!");
        props.setRefreshTokenTtlDays(14);
        return new RefreshTokenService(refreshTokenRepository, props);
    }

    private User sampleUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("refresh-test@example.com");
        return user;
    }

    @Test
    void issueSavesHashedTokenAndReturnsRawToken() {
        RefreshTokenService service = service();
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        String raw = service.issue(sampleUser());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isNotEqualTo(raw);
        assertThat(captor.getValue().isRevoked()).isFalse();
        assertThat(raw).isNotBlank();
    }

    @Test
    void rotateRejectsUnknownToken() {
        RefreshTokenService service = service();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("does-not-exist"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rotateRejectsExpiredToken() {
        RefreshTokenService service = service();
        User user = sampleUser();
        RefreshToken stored = new RefreshToken();
        stored.setUser(user);
        stored.setTokenHash("irrelevant-because-mocked-lookup");
        stored.setExpiresAt(Instant.now().minusSeconds(60));
        stored.setRevoked(false);
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.rotate("some-raw-token"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rotateRejectsRevokedToken() {
        RefreshTokenService service = service();
        User user = sampleUser();
        RefreshToken stored = new RefreshToken();
        stored.setUser(user);
        stored.setTokenHash("irrelevant-because-mocked-lookup");
        stored.setExpiresAt(Instant.now().plusSeconds(3600));
        stored.setRevoked(true);
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.rotate("some-raw-token"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rotateRevokesOldTokenAndIssuesNewOne() {
        RefreshTokenService service = service();
        User user = sampleUser();
        RefreshToken stored = new RefreshToken();
        stored.setUser(user);
        stored.setTokenHash("irrelevant-because-mocked-lookup");
        stored.setExpiresAt(Instant.now().plusSeconds(3600));
        stored.setRevoked(false);
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stored));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshTokenService.RotationResult result = service.rotate("some-raw-token");

        assertThat(stored.isRevoked()).isTrue();
        assertThat(result.user()).isSameAs(user);
        assertThat(result.newRawToken()).isNotBlank();
    }
}
