package com.bif.server.features.auth.services;

import com.bif.server.features.auth.dto.rest.AuthResponse;
import com.bif.server.features.auth.dto.rest.AuthUserResponse;
import com.bif.server.features.auth.dto.rest.LoginRequest;
import com.bif.server.features.auth.dto.rest.RefreshTokenRequest;
import com.bif.server.features.auth.dto.rest.RegisterRequest;
import com.bif.server.features.auth.exceptions.EmailAlreadyUsedException;
import com.bif.server.features.auth.exceptions.InvalidCredentialsException;
import com.bif.server.features.auth.exceptions.InvalidRefreshTokenException;
import com.bif.server.features.auth.exceptions.InvalidRegistrationException;
import com.bif.server.features.auth.models.RefreshToken;
import com.bif.server.features.auth.repositories.RefreshTokenRepository;
import com.bif.server.features.auth.security.JwtService;
import com.bif.server.features.user.models.User;
import com.bif.server.features.user.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuthService {
    private static final Pattern SIMPLE_EMAIL_PATTERN =
            Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshTokenExpirationSeconds;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${security.jwt.refresh-token-expiration-seconds:2592000}") long refreshTokenExpirationSeconds
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenExpirationSeconds = refreshTokenExpirationSeconds;
    }

    public AuthResponse register(RegisterRequest request) {
        String username = requiredTrimmed(request.username(), "username");
        String email = normalizedEmail(request.email());
        String password = required(request.password(), "password");
        String confirmPassword = required(request.confirmPassword(), "confirmPassword");

        if (!password.equals(confirmPassword)) {
            throw new InvalidRegistrationException("password and confirmPassword must match");
        }
        if (password.length() < 8) {
            throw new InvalidRegistrationException("password must have at least 8 characters");
        }
        if (!SIMPLE_EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidRegistrationException("email format is invalid");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyUsedException("email is already in use");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setAvatarLetter(defaultAvatarLetter(username));
        user.setAvatarColor(0xFF1E88E5);
        user.setOnline(true);

        User saved = userRepository.save(user);
        return toAuthResponse(saved);
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalizedEmail(request.email());
        String password = required(request.password(), "password");

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new InvalidCredentialsException("invalid credentials"));

        String passwordHash = user.getPasswordHash();
        if (passwordHash == null || passwordHash.isBlank() || !passwordEncoder.matches(password, passwordHash)) {
            throw new InvalidCredentialsException("invalid credentials");
        }

        user.setOnline(true);
        User saved = userRepository.save(user);
        return toAuthResponse(saved);
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        String tokenValue = requiredTrimmed(request.refreshToken(), "refreshToken");
        RefreshToken refreshToken = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new InvalidRefreshTokenException("refresh token is invalid"));

        if (refreshToken.isRevoked()) {
            throw new InvalidRefreshTokenException("refresh token is revoked");
        }
        if (refreshToken.getExpiresAt() == null || refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException("refresh token is expired");
        }

        User user = userRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new InvalidRefreshTokenException("user not found"));

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
        return toAuthResponse(user);
    }

    public void logout(RefreshTokenRequest request) {
        String tokenValue = requiredTrimmed(request.refreshToken(), "refreshToken");
        refreshTokenRepository.findByToken(tokenValue).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    private AuthResponse toAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = issueRefreshToken(user.getId());
        AuthUserResponse userResponse = new AuthUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail()
        );
        return new AuthResponse(accessToken, refreshToken, "Bearer", jwtService.getAccessTokenExpirationSeconds(), userResponse);
    }

    private String issueRefreshToken(String userId) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setUserId(userId);
        refreshToken.setCreatedAt(Instant.now());
        refreshToken.setExpiresAt(Instant.now().plusSeconds(refreshTokenExpirationSeconds));
        refreshToken.setRevoked(false);
        return refreshTokenRepository.save(refreshToken).getToken();
    }

    private String normalizedEmail(String value) {
        String email = requiredTrimmed(value, "email");
        return email.toLowerCase();
    }

    private String requiredTrimmed(String value, String fieldName) {
        String resolved = required(value, fieldName).trim();
        if (resolved.isBlank()) {
            throw new InvalidRegistrationException(fieldName + " must not be blank");
        }
        return resolved;
    }

    private String required(String value, String fieldName) {
        if (value == null) {
            throw new InvalidRegistrationException(fieldName + " is required");
        }
        return value;
    }

    private String defaultAvatarLetter(String username) {
        return username.substring(0, 1).toUpperCase();
    }
}
