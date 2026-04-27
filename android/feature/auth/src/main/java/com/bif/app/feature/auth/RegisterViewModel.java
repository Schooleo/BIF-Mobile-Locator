package com.bif.app.feature.auth;

import android.util.Patterns;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class RegisterViewModel extends ViewModel {

    private static final int OTP_LENGTH = 6;
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final MutableLiveData<Boolean> sendOtpEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> otpEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> credentialsEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> registerEnabled = new MutableLiveData<>(false);

    private String email = "";
    private String otp = "";
    private String username = "";
    private String password = "";
    private String confirmPassword = "";
    private boolean otpRequested = false;

    public LiveData<Boolean> getSendOtpEnabled() {
        return sendOtpEnabled;
    }

    public LiveData<Boolean> getOtpEnabled() {
        return otpEnabled;
    }

    public LiveData<Boolean> getCredentialsEnabled() {
        return credentialsEnabled;
    }

    public LiveData<Boolean> getRegisterEnabled() {
        return registerEnabled;
    }

    public void onEmailChanged(String value) {
        email = value == null ? "" : value.trim();
        updateState();
    }

    public void onOtpChanged(String value) {
        otp = value == null ? "" : value.trim();
        updateState();
    }

    public void onUsernameChanged(String value) {
        username = value == null ? "" : value.trim();
        updateState();
    }

    public void onPasswordChanged(String value) {
        password = value == null ? "" : value;
        updateState();
    }

    public void onConfirmPasswordChanged(String value) {
        confirmPassword = value == null ? "" : value;
        updateState();
    }

    public void onSendOtpClicked() {
        otpRequested = isEmailValid(email);
        updateState();
    }

    private void updateState() {
        boolean emailValid = isEmailValid(email);
        if (!emailValid) {
            otpRequested = false;
        }

        boolean otpEnabledValue = otpRequested;
        boolean otpValid = otpEnabledValue && isOtpValid(otp);
        boolean credentialsEnabledValue = otpValid;

        boolean passwordValid = password != null && password.length() >= MIN_PASSWORD_LENGTH;
        boolean confirmValid = passwordValid && password.equals(confirmPassword);
        boolean usernameValid = username != null && !username.trim().isEmpty();

        sendOtpEnabled.setValue(emailValid);
        otpEnabled.setValue(otpEnabledValue);
        credentialsEnabled.setValue(credentialsEnabledValue);
        registerEnabled.setValue(emailValid && otpValid && usernameValid && confirmValid);
    }

    private boolean isEmailValid(String value) {
        return value != null && Patterns.EMAIL_ADDRESS.matcher(value).matches();
    }

    private boolean isOtpValid(String value) {
        return value != null && value.length() == OTP_LENGTH && value.matches("\\d+");
    }
}
