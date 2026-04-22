package com.bif.server.features.auth.services;

import com.bif.server.features.auth.dto.rest.AuthResponse;
import com.bif.server.features.auth.dto.rest.ForgotPasswordOtpRequest;
import com.bif.server.features.auth.dto.rest.ForgotPasswordOtpResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordResetOtpRepository passwordResetOtpRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private AccessTokenBlacklistService accessTokenBlacklistService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                refreshTokenRepository,
                passwordResetOtpRepository,
                passwordEncoder,
                jwtService,
                accessTokenBlacklistService,
                2592000L
        );
    }

    @Test
    void register_WhenPasswordMismatch_ThrowsInvalidRegistrationException() {
        RegisterRequest request = new RegisterRequest("alex", "alex@bif.local", "Password123!", "Password123");

        assertThrows(InvalidRegistrationException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_WhenEmailUsed_ThrowsEmailAlreadyUsedException() {
        RegisterRequest request = new RegisterRequest("alex", "alex@bif.local", "Password123!", "Password123!");
        when(userRepository.existsByEmailIgnoreCase("alex@bif.local")).thenReturn(true);

        assertThrows(EmailAlreadyUsedException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_WhenValid_ReturnsTokenAndUser() {
        RegisterRequest request = new RegisterRequest("alex", "alex@bif.local", "Password123!", "Password123!");
        when(userRepository.existsByEmailIgnoreCase("alex@bif.local")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("hashed");

        User saved = new User();
        saved.setId("u1");
        saved.setUsername("alex");
        saved.setEmail("alex@bif.local");
        saved.setPasswordHash("hashed");

        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateAccessToken("u1", "alex@bif.local")).thenReturn("jwt-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(3600L);

        AuthResponse result = authService.register(request);

        assertEquals("jwt-token", result.accessToken());
        assertNotNull(result.refreshToken());
        assertEquals("Bearer", result.tokenType());
        assertEquals(3600L, result.expiresIn());
        assertNotNull(result.user());
        assertEquals("u1", result.user().id());
        assertEquals("alex", result.user().username());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void login_WhenUnknownEmail_ThrowsInvalidCredentialsException() {
        LoginRequest request = new LoginRequest("unknown@bif.local", "Password123!");
        when(userRepository.findByEmailIgnoreCase("unknown@bif.local")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_WhenPasswordWrong_ThrowsInvalidCredentialsException() {
        LoginRequest request = new LoginRequest("alex@bif.local", "WrongPass");

        User user = new User();
        user.setId("u1");
        user.setEmail("alex@bif.local");
        user.setPasswordHash("hashed");

        when(userRepository.findByEmailIgnoreCase("alex@bif.local")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass", "hashed")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_WhenValid_ReturnsAuthResponse() {
        LoginRequest request = new LoginRequest("alex@bif.local", "Password123!");

        User user = new User();
        user.setId("u1");
        user.setUsername("alex");
        user.setEmail("alex@bif.local");
        user.setPasswordHash("hashed");

        when(userRepository.findByEmailIgnoreCase("alex@bif.local")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "hashed")).thenReturn(true);
        when(userRepository.save(user)).thenReturn(user);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateAccessToken("u1", "alex@bif.local")).thenReturn("jwt-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(3600L);

        AuthResponse result = authService.login(request);

        assertEquals("jwt-token", result.accessToken());
        assertNotNull(result.refreshToken());
        assertEquals("u1", result.user().id());
        verify(userRepository).save(user);
    }

    @Test
    void refresh_WhenTokenInvalid_ThrowsInvalidRefreshTokenException() {
        when(refreshTokenRepository.findByToken("rt")).thenReturn(Optional.empty());

        assertThrows(InvalidRefreshTokenException.class, () -> authService.refresh(new RefreshTokenRequest("rt")));
    }

    @Test
    void refresh_WhenValid_ReturnsNewTokens() {
        RefreshToken existing = new RefreshToken();
        existing.setToken("rt");
        existing.setUserId("u1");
        existing.setRevoked(false);
        existing.setExpiresAt(java.time.Instant.now().plusSeconds(1000));

        User user = new User();
        user.setId("u1");
        user.setUsername("alex");
        user.setEmail("alex@bif.local");

        when(refreshTokenRepository.findByToken("rt")).thenReturn(Optional.of(existing));
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateAccessToken("u1", "alex@bif.local")).thenReturn("new-jwt");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(3600L);

        AuthResponse result = authService.refresh(new RefreshTokenRequest("rt"));

        assertEquals("new-jwt", result.accessToken());
        assertNotNull(result.refreshToken());
        assertTrue(existing.isRevoked());
    }

    @Test
    void logout_WhenRefreshTokenExists_RevokesToken() {
        RefreshToken existing = new RefreshToken();
        existing.setToken("rt");
        existing.setRevoked(false);

        when(refreshTokenRepository.findByToken("rt")).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.logout(new RefreshTokenRequest("rt"), null);

        assertTrue(existing.isRevoked());
        verify(refreshTokenRepository).save(existing);
    }

    @Test
    void logout_WhenAccessTokenProvided_RevokesAccessToken() {
        RefreshToken existing = new RefreshToken();
        existing.setToken("rt");
        existing.setRevoked(false);

        when(refreshTokenRepository.findByToken("rt")).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.logout(new RefreshTokenRequest("rt"), "access-token-value");

        assertTrue(existing.isRevoked());
        verify(refreshTokenRepository).save(existing);
        verify(accessTokenBlacklistService).revoke("access-token-value");
    }

    @Test
    void requestForgotPasswordOtp_WhenEmailMissing_ThrowsInvalidRegistrationException() {
        assertThrows(InvalidRegistrationException.class, () -> authService.requestForgotPasswordOtp(new ForgotPasswordOtpRequest(null)));
        verify(passwordResetOtpRepository, never()).save(any(PasswordResetOtp.class));
    }

    @Test
    void requestForgotPasswordOtp_WhenEmailNotFound_ReturnsFailure() {
        when(userRepository.findByEmailIgnoreCase("unknown@bif.local")).thenReturn(Optional.empty());

        ForgotPasswordOtpResponse result = authService.requestForgotPasswordOtp(new ForgotPasswordOtpRequest("unknown@bif.local"));

        assertFalse(result.success());
        assertEquals("Email does not exist", result.message());
        verify(passwordResetOtpRepository, never()).save(any(PasswordResetOtp.class));
    }

    @Test
    void requestForgotPasswordOtp_WhenEmailExists_SavesOtpAndReturnsSuccess() {
        User user = new User();
        user.setId("u1");
        user.setEmail("alex@bif.local");
        when(userRepository.findByEmailIgnoreCase("alex@bif.local")).thenReturn(Optional.of(user));
        when(passwordResetOtpRepository.save(any(PasswordResetOtp.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ForgotPasswordOtpResponse result = authService.requestForgotPasswordOtp(new ForgotPasswordOtpRequest("alex@bif.local"));

        assertTrue(result.success());
        assertEquals("OTP has been sent to your email", result.message());
        verify(passwordResetOtpRepository).save(argThat(otp ->
                "alex@bif.local".equals(otp.getEmail())
                        && otp.getOtp() != null
                        && otp.getOtp().matches("\\d{6}")
                        && otp.getExpiresAt() != null
                        && otp.getCreatedAt() != null
        ));
    }
}
