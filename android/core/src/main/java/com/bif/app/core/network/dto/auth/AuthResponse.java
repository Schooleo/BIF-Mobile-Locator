package com.bif.app.core.network.dto.auth;

public class AuthResponse {
    public String accessToken;
    public String refreshToken;
    public String tokenType;
    public long expiresIn;
    public AuthUserResponse user;
}