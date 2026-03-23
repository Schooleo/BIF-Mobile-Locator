package com.bif.app.core.network.dto.auth;

public class RegisterRequest {
    public String username;
    public String email;
    public String password;
    public String confirmPassword;

    public RegisterRequest(String username, String email, String password, String confirmPassword) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.confirmPassword = confirmPassword;
    }
}