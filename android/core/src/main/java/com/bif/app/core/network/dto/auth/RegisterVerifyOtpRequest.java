package com.bif.app.core.network.dto.auth;

public class RegisterVerifyOtpRequest {
    public String email;
    public String otp;

    public RegisterVerifyOtpRequest(String email, String otp) {
        this.email = email;
        this.otp = otp;
    }
}
