package com.bif.app.core.network.dto.auth;

import com.google.gson.annotations.SerializedName;

public class ResetPasswordRequest {
    @SerializedName("resetToken")
    public String token;
    public String newPassword;

    public ResetPasswordRequest(String token, String newPassword) {
        this.token = token;
        this.newPassword = newPassword;
    }
}