package com.bif.server.features.auth.services;

import com.bif.server.features.auth.dto.rest.AuthResponse;
import com.bif.server.features.auth.dto.rest.AuthUserResponse;
import com.bif.server.features.auth.dto.rest.ForgotPasswordOtpRequest;
import com.bif.server.features.auth.dto.rest.ForgotPasswordOtpResponse;
import com.bif.server.features.auth.dto.rest.ForgotPasswordResetRequest;
import com.bif.server.features.auth.dto.rest.ForgotPasswordResetResponse;
import com.bif.server.features.auth.dto.rest.ForgotPasswordVerifyOtpRequest;
import com.bif.server.features.auth.dto.rest.ForgotPasswordVerifyOtpResponse;
import com.bif.server.features.auth.dto.rest.ChangePasswordRequest;
import com.bif.server.features.auth.dto.rest.ChangePasswordResponse;
import com.bif.server.features.auth.dto.rest.LoginRequest;
import com.bif.server.features.auth.dto.rest.RefreshTokenRequest;
import com.bif.server.features.auth.dto.rest.RegisterRequest;
import com.bif.server.features.auth.exceptions.EmailAlreadyUsedException;
import com.bif.server.features.auth.exceptions.InvalidCredentialsException;
import com.bif.server.features.auth.exceptions.InvalidRefreshTokenException;
import com.bif.server.features.auth.exceptions.InvalidRegistrationException;
import com.bif.server.features.auth.models.PasswordResetOtp;
import com.bif.server.features.auth.models.RefreshToken;
import com.bif.server.features.auth.repositories.PasswordResetOtpRepository;
import com.bif.server.features.auth.repositories.RefreshTokenRepository;
import com.bif.server.features.auth.security.AccessTokenBlacklistService;
import com.bif.server.features.auth.security.JwtService;
import com.bif.server.features.user.models.User;
import com.bif.server.features.user.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Pattern SIMPLE_EMAIL_PATTERN =
            Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final long OTP_EXPIRATION_MINUTES = 5L;
        private static final long RESET_TOKEN_EXPIRATION_MINUTES = 10L;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetOtpRepository passwordResetOtpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AccessTokenBlacklistService accessTokenBlacklistService;
    private final EmailService emailService;
    private final long refreshTokenExpirationSeconds;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetOtpRepository passwordResetOtpRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AccessTokenBlacklistService accessTokenBlacklistService,
            EmailService emailService,
            @Value("${security.jwt.refresh-token-expiration-seconds:2592000}") long refreshTokenExpirationSeconds
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetOtpRepository = passwordResetOtpRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.accessTokenBlacklistService = accessTokenBlacklistService;
        this.emailService = emailService;
        this.refreshTokenExpirationSeconds = refreshTokenExpirationSeconds;
    }

    public ForgotPasswordOtpResponse requestForgotPasswordOtp(ForgotPasswordOtpRequest request) {
        String email = normalizedEmail(request.email());

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            return new ForgotPasswordOtpResponse(false, "Email does not exist");
        }

        String otp = generateOtp();
        Instant now = Instant.now();
        PasswordResetOtp passwordResetOtp = new PasswordResetOtp();
        passwordResetOtp.setEmail(email);
        passwordResetOtp.setOtp(otp);
        passwordResetOtp.setCreatedAt(now);
        passwordResetOtp.setExpiresAt(now.plus(OTP_EXPIRATION_MINUTES, ChronoUnit.MINUTES));
        passwordResetOtp.setResetToken(null);
        passwordResetOtp.setResetTokenExpiresAt(null);
        passwordResetOtpRepository.save(passwordResetOtp);

        emailService.sendOtpEmail(email, otp);
        return new ForgotPasswordOtpResponse(true, "OTP has been sent to your email");
    }

    public ForgotPasswordVerifyOtpResponse verifyForgotPasswordOtp(ForgotPasswordVerifyOtpRequest request) {
        String email = normalizedEmail(request.email());
        String otp = requiredTrimmed(request.otp(), "otp");

        PasswordResetOtp passwordResetOtp = passwordResetOtpRepository.findById(email)
                .orElse(null);
        if (passwordResetOtp == null || passwordResetOtp.getOtp() == null || !passwordResetOtp.getOtp().equals(otp)) {
            return new ForgotPasswordVerifyOtpResponse(false, null);
        }
        if (passwordResetOtp.getExpiresAt() == null || passwordResetOtp.getExpiresAt().isBefore(Instant.now())) {
            return new ForgotPasswordVerifyOtpResponse(false, null);
        }

        String resetToken = UUID.randomUUID().toString();
        passwordResetOtp.setResetToken(resetToken);
        passwordResetOtp.setResetTokenExpiresAt(Instant.now().plus(RESET_TOKEN_EXPIRATION_MINUTES, ChronoUnit.MINUTES));
        passwordResetOtpRepository.save(passwordResetOtp);

        return new ForgotPasswordVerifyOtpResponse(true, resetToken);
    }

    public ForgotPasswordResetResponse resetForgotPassword(ForgotPasswordResetRequest request) {
        String resetToken = requiredTrimmed(request.resetToken(), "resetToken");
        String newPassword = required(request.newPassword(), "newPassword");

        if (newPassword.isBlank()) {
            throw new InvalidRegistrationException("newPassword must not be blank");
        }
        if (newPassword.length() < 8) {
            throw new InvalidRegistrationException("newPassword must have at least 8 characters");
        }

        PasswordResetOtp passwordResetOtp = passwordResetOtpRepository.findByResetToken(resetToken)
                .orElse(null);
        if (passwordResetOtp == null || passwordResetOtp.getResetTokenExpiresAt() == null
                || passwordResetOtp.getResetTokenExpiresAt().isBefore(Instant.now())) {
            return new ForgotPasswordResetResponse(false, "Reset token is invalid or expired");
        }

        User user = userRepository.findByEmailIgnoreCase(passwordResetOtp.getEmail())
                .orElse(null);
        if (user == null) {
            return new ForgotPasswordResetResponse(false, "User not found");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        passwordResetOtp.setResetToken(null);
        passwordResetOtp.setResetTokenExpiresAt(null);
        passwordResetOtp.setOtp(null);
        passwordResetOtp.setExpiresAt(null);
        passwordResetOtpRepository.save(passwordResetOtp);

        return new ForgotPasswordResetResponse(true, "Password has been reset successfully");
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

    public void logout(RefreshTokenRequest request, String accessToken) {
        String tokenValue = requiredTrimmed(request.refreshToken(), "refreshToken");
        refreshTokenRepository.findByToken(tokenValue).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });

        if (accessToken != null && !accessToken.isBlank()) {
            accessTokenBlacklistService.revoke(accessToken);
        }
    }

    public ChangePasswordResponse changePassword(String userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        return new ChangePasswordResponse(true, "Password changed successfully");
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

    private String generateOtp() {
        int value = SECURE_RANDOM.nextInt(1_000_000);
        return String.format("%06d", value);
    }
}
