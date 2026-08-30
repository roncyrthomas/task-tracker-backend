package com.airtribe.tasktracker.auth;

import com.airtribe.tasktracker.auth.dto.AuthResponse;
import com.airtribe.tasktracker.common.exception.NotFoundException;
import com.airtribe.tasktracker.common.exception.UnauthorizedException;
import com.airtribe.tasktracker.security.JwtService;
import com.airtribe.tasktracker.user.User;
import com.airtribe.tasktracker.user.UserService;
import com.airtribe.tasktracker.user.dto.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password.";

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserService userService, PasswordEncoder passwordEncoder,
                        JwtService jwtService, RefreshTokenService refreshTokenService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    public AuthResponse register(String name, String email, String rawPassword) {
        User user = userService.createUser(name, email, passwordEncoder.encode(rawPassword));
        return issueTokens(user);
    }

    public AuthResponse login(String email, String rawPassword) {
        User user;
        try {
            user = userService.findByEmail(email);
        } catch (NotFoundException ex) {
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }
        return issueTokens(user);
    }

    public AuthResponse refresh(String rawRefreshToken) {
        RefreshTokenService.RotationResult result = refreshTokenService.rotate(rawRefreshToken);
        String accessToken = jwtService.generateAccessToken(result.user());
        return new AuthResponse(accessToken, result.newRawToken(), UserResponse.from(result.user()));
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.issue(user);
        return new AuthResponse(accessToken, refreshToken, UserResponse.from(user));
    }
}
