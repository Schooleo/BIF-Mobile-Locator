package com.bif.app.core.network.dto.auth;

public class ChangePasswordRequest {
    public String currentPassword;
    public String newPassword;

    public ChangePasswordRequest(String currentPassword, String newPassword) {
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
    }
}
