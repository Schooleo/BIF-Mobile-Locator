package com.bif.server.features.auth.controllers;

import com.bif.server.features.auth.dto.rest.AuthResponse;
import com.bif.server.features.auth.dto.rest.ForgotPasswordOtpRequest;
import com.bif.server.features.auth.dto.rest.ForgotPasswordOtpResponse;
import com.bif.server.features.auth.dto.rest.ForgotPasswordResetRequest;
import com.bif.server.features.auth.dto.rest.ForgotPasswordResetResponse;
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
import com.bif.server.features.user.services.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/auth", "/auth"})
public class AuthController {
    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
        } catch (InvalidRegistrationException e) {
            return ResponseEntity.badRequest().build();
        } catch (EmailAlreadyUsedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        try {
            return ResponseEntity.ok(authService.login(request));
        } catch (InvalidRegistrationException e) {
            return ResponseEntity.badRequest().build();
        } catch (InvalidCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshTokenRequest request) {
        try {
            return ResponseEntity.ok(authService.refresh(request));
        } catch (InvalidRegistrationException e) {
            return ResponseEntity.badRequest().build();
        } catch (InvalidRefreshTokenException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/forgot-password/request-otp")
    public ResponseEntity<ForgotPasswordOtpResponse> requestForgotPasswordOtp(@RequestBody ForgotPasswordOtpRequest request) {
        ForgotPasswordOtpResponse response = authService.requestForgotPasswordOtp(request);
        if (response.success()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @PostMapping("/forgot-password/verify-otp")
    public ResponseEntity<ForgotPasswordVerifyOtpResponse> verifyForgotPasswordOtp(@RequestBody ForgotPasswordVerifyOtpRequest request) {
        ForgotPasswordVerifyOtpResponse response = authService.verifyForgotPasswordOtp(request);
        if (response.success()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.badRequest().body(response);
    }

    @PostMapping("/forgot-password/reset")
    public ResponseEntity<ForgotPasswordResetResponse> resetForgotPassword(@RequestBody ForgotPasswordResetRequest request) {
        try {
            ForgotPasswordResetResponse response = authService.resetForgotPassword(request);
            if (response.success()) {
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.badRequest().body(response);
        } catch (InvalidRegistrationException e) {
            return ResponseEntity.badRequest().body(new ForgotPasswordResetResponse(false, e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody RefreshTokenRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        try {
            authService.logout(request, extractBearerToken(authorizationHeader));
            return ResponseEntity.noContent().build();
        } catch (InvalidRegistrationException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/me")
    public ResponseEntity<AuthStateResponse> getAuthState(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthStateResponse(false, null, false));
        }

        String userId = authentication.getPrincipal().toString();
        if (userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthStateResponse(false, null, false));
        }

        boolean hasProfile = userService.getById(userId).isPresent();
        return ResponseEntity.ok(new AuthStateResponse(true, userId, hasProfile));
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authorizationHeader.substring(7).trim();
        return token.isBlank() ? null : token;
    }
}
