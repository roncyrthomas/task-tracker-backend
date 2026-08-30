package com.airtribe.tasktracker.security;

import com.airtribe.tasktracker.user.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private User sampleUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("jwt-test@example.com");
        user.setName("JWT Test");
        return user;
    }

    @Test
    void generatesAndParsesRoundTrip() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-key-at-least-32-bytes-long!!");
        props.setAccessTokenTtlMinutes(15);
        JwtService jwtService = new JwtService(props);

        User user = sampleUser();
        String token = jwtService.generateAccessToken(user);
        JwtPrincipal principal = jwtService.parseAccessToken(token);

        assertThat(principal.userId()).isEqualTo(user.getId());
        assertThat(principal.email()).isEqualTo(user.getEmail());
    }

    @Test
    void rejectsExpiredToken() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-key-at-least-32-bytes-long!!");
        props.setAccessTokenTtlMinutes(-1);
        JwtService jwtService = new JwtService(props);

        String token = jwtService.generateAccessToken(sampleUser());

        assertThatThrownBy(() -> jwtService.parseAccessToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtProperties issuerProps = new JwtProperties();
        issuerProps.setSecret("first-secret-key-at-least-32-bytes-long!");
        issuerProps.setAccessTokenTtlMinutes(15);
        String token = new JwtService(issuerProps).generateAccessToken(sampleUser());

        JwtProperties verifierProps = new JwtProperties();
        verifierProps.setSecret("second-secret-key-at-least-32-bytes-lon");
        verifierProps.setAccessTokenTtlMinutes(15);
        JwtService verifier = new JwtService(verifierProps);

        assertThatThrownBy(() -> verifier.parseAccessToken(token))
                .isInstanceOf(JwtException.class);
    }
}
