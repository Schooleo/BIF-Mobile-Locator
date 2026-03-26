package com.bif.app.core.network.dto.auth;

public class RefreshTokenRequest {
    public String refreshToken;

    public RefreshTokenRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}