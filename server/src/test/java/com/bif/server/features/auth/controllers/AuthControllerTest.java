package com.bif.server.features.auth.controllers;

import com.bif.server.features.auth.dto.rest.AuthResponse;
import com.bif.server.features.auth.dto.rest.AuthUserResponse;
import com.bif.server.features.auth.dto.rest.ForgotPasswordOtpRequest;
import com.bif.server.features.auth.dto.rest.ForgotPasswordOtpResponse;
import com.bif.server.features.auth.dto.rest.ForgotPasswordVerifyOtpRequest;
import com.bif.server.features.auth.dto.rest.ForgotPasswordVerifyOtpResponse;
import com.bif.server.features.auth.dto.rest.LoginRequest;
import com.bif.server.features.auth.dto.rest.RefreshTokenRequest;
import com.bif.server.features.auth.dto.rest.RegisterRequest;
import com.bif.server.features.auth.exceptions.EmailAlreadyUsedException;
import com.bif.server.features.auth.exceptions.InvalidCredentialsException;
import com.bif.server.features.auth.exceptions.InvalidRefreshTokenException;
import com.bif.server.features.auth.exceptions.InvalidRegistrationException;
import com.bif.server.features.auth.services.AuthService;
import com.bif.server.features.user.dto.rest.AuthStateResponse;
import com.bif.server.features.user.models.User;
import com.bif.server.features.user.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private UserService userService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService, userService);
    }

    @Test
    void register_WhenValid_ReturnsCreated() {
        RegisterRequest request = new RegisterRequest("alex", "alex@bif.local", "Password123!", "Password123!");
        AuthResponse response = new AuthResponse("token", "refresh", "Bearer", 3600, new AuthUserResponse("u1", "alex", "alex@bif.local"));
        when(authService.register(request)).thenReturn(response);

        ResponseEntity<AuthResponse> result = controller.register(request);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals("token", result.getBody().accessToken());
    }

    @Test
    void register_WhenValidationFails_ReturnsBadRequest() {
        RegisterRequest request = new RegisterRequest("", "bad-email", "short", "short");
        when(authService.register(request)).thenThrow(new InvalidRegistrationException("invalid"));

        ResponseEntity<AuthResponse> result = controller.register(request);

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }

    @Test
    void register_WhenEmailExists_ReturnsConflict() {
        RegisterRequest request = new RegisterRequest("alex", "alex@bif.local", "Password123!", "Password123!");
        when(authService.register(request)).thenThrow(new EmailAlreadyUsedException("email exists"));

        ResponseEntity<AuthResponse> result = controller.register(request);

        assertEquals(HttpStatus.CONFLICT, result.getStatusCode());
    }

    @Test
    void login_WhenValid_ReturnsOk() {
        LoginRequest request = new LoginRequest("alex@bif.local", "Password123!");
        AuthResponse response = new AuthResponse("token", "refresh", "Bearer", 3600, new AuthUserResponse("u1", "alex", "alex@bif.local"));
        when(authService.login(request)).thenReturn(response);

        ResponseEntity<AuthResponse> result = controller.login(request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals("token", result.getBody().accessToken());
    }

    @Test
    void login_WhenCredentialsInvalid_ReturnsUnauthorized() {
        LoginRequest request = new LoginRequest("alex@bif.local", "wrong");
        when(authService.login(request)).thenThrow(new InvalidCredentialsException("invalid credentials"));

        ResponseEntity<AuthResponse> result = controller.login(request);

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
    }

    @Test
    void refresh_WhenValid_ReturnsOk() {
        RefreshTokenRequest request = new RefreshTokenRequest("rt");
        AuthResponse response = new AuthResponse("token", "new-rt", "Bearer", 3600, new AuthUserResponse("u1", "alex", "alex@bif.local"));
        when(authService.refresh(request)).thenReturn(response);

        ResponseEntity<AuthResponse> result = controller.refresh(request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals("new-rt", result.getBody().refreshToken());
    }

    @Test
    void refresh_WhenTokenInvalid_ReturnsUnauthorized() {
        RefreshTokenRequest request = new RefreshTokenRequest("bad");
        when(authService.refresh(request)).thenThrow(new InvalidRefreshTokenException("invalid"));

        ResponseEntity<AuthResponse> result = controller.refresh(request);

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
    }

    @Test
    void requestForgotPasswordOtp_WhenEmailExists_ReturnsOk() {
        ForgotPasswordOtpRequest request = new ForgotPasswordOtpRequest("alex@bif.local");
        ForgotPasswordOtpResponse response = new ForgotPasswordOtpResponse(true, "OTP has been sent to your email");
        when(authService.requestForgotPasswordOtp(request)).thenReturn(response);

        ResponseEntity<ForgotPasswordOtpResponse> result = controller.requestForgotPasswordOtp(request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertTrue(result.getBody().success());
    }

    @Test
    void requestForgotPasswordOtp_WhenEmailMissing_ReturnsNotFound() {
        ForgotPasswordOtpRequest request = new ForgotPasswordOtpRequest("unknown@bif.local");
        ForgotPasswordOtpResponse response = new ForgotPasswordOtpResponse(false, "Email does not exist");
        when(authService.requestForgotPasswordOtp(request)).thenReturn(response);

        ResponseEntity<ForgotPasswordOtpResponse> result = controller.requestForgotPasswordOtp(request);

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
        assertNotNull(result.getBody());
        assertFalse(result.getBody().success());
    }

    @Test
    void verifyForgotPasswordOtp_WhenValid_ReturnsOk() {
        ForgotPasswordVerifyOtpRequest request = new ForgotPasswordVerifyOtpRequest("alex@bif.local", "123456");
        ForgotPasswordVerifyOtpResponse response = new ForgotPasswordVerifyOtpResponse(true, "reset-token");
        when(authService.verifyForgotPasswordOtp(request)).thenReturn(response);

        ResponseEntity<ForgotPasswordVerifyOtpResponse> result = controller.verifyForgotPasswordOtp(request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertTrue(result.getBody().success());
        assertEquals("reset-token", result.getBody().resetToken());
    }

    @Test
    void verifyForgotPasswordOtp_WhenInvalid_ReturnsBadRequest() {
        ForgotPasswordVerifyOtpRequest request = new ForgotPasswordVerifyOtpRequest("alex@bif.local", "000000");
        ForgotPasswordVerifyOtpResponse response = new ForgotPasswordVerifyOtpResponse(false, null);
        when(authService.verifyForgotPasswordOtp(request)).thenReturn(response);

        ResponseEntity<ForgotPasswordVerifyOtpResponse> result = controller.verifyForgotPasswordOtp(request);

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        assertNotNull(result.getBody());
        assertFalse(result.getBody().success());
    }

    @Test
    void logout_WhenValid_ReturnsNoContent() {
        RefreshTokenRequest request = new RefreshTokenRequest("rt");

        ResponseEntity<Void> result = controller.logout(request, null);

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(authService).logout(request, null);
    }

    @Test
    void logout_WhenWithAccessToken_RevokesAndReturnsNoContent() {
        RefreshTokenRequest request = new RefreshTokenRequest("rt");
        String authHeader = "Bearer access-token-value";

        ResponseEntity<Void> result = controller.logout(request, authHeader);

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(authService).logout(request, "access-token-value");
    }

    @Test
    void getAuthState_WhenNotAuthenticated_ReturnsUnauthorized() {
        ResponseEntity<AuthStateResponse> result = controller.getAuthState(null);

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        assertNotNull(result.getBody());
        assertFalse(result.getBody().authenticated());
    }

    @Test
    void getAuthState_WhenAuthenticatedAndProfileExists_ReturnsOk() {
        Authentication auth = new UsernamePasswordAuthenticationToken("u1", null);
        when(userService.getById("u1")).thenReturn(Optional.of(new User()));

        ResponseEntity<AuthStateResponse> result = controller.getAuthState(auth);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertTrue(result.getBody().authenticated());
        assertEquals("u1", result.getBody().userId());
        assertTrue(result.getBody().hasProfile());
    }
}
