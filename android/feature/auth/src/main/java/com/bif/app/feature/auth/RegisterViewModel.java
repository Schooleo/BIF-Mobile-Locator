package com.bif.app.feature.auth;

import android.util.Patterns;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.bif.app.data.repository.AuthRepository;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
@HiltViewModel
public class RegisterViewModel extends ViewModel {

    private static final int OTP_LENGTH = 6;
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final MutableLiveData<Boolean> sendOtpEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> otpEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> credentialsEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> registerEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<UiState> requestOtpState = new MutableLiveData<>(new UiState.Idle());
    private final MutableLiveData<UiState> registerState = new MutableLiveData<>(new UiState.Idle());

    private final AuthRepository authRepository;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private String email = "";
    private String otp = "";
    private String username = "";
    private String password = "";
    private String confirmPassword = "";
    private boolean otpRequested = false;

    @Inject
    public RegisterViewModel(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

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

    public LiveData<UiState> getRequestOtpState() {
        return requestOtpState;
    }

    public LiveData<UiState> getRegisterState() {
        return registerState;
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

    public void requestOtp(String email) {
        String resolvedEmail = email == null ? "" : email.trim();
        if (!isEmailValid(resolvedEmail)) {
            requestOtpState.setValue(new UiState.Error("Invalid email"));
            return;
        }

        requestOtpState.setValue(new UiState.Loading());
        ioExecutor.execute(() -> {
            AuthRepository.Result<?> result = authRepository.requestRegisterOtp(resolvedEmail);
            if (result instanceof AuthRepository.Result.Success) {
                requestOtpState.postValue(new UiState.Success());
                return;
            }
            AuthRepository.Result.Error<?> error = (AuthRepository.Result.Error<?>) result;
            requestOtpState.postValue(new UiState.Error(error.message));
        });
    }

    public void onRegisterClicked() {
        registerState.setValue(new UiState.Loading());

        String resolvedEmail = email == null ? "" : email.trim();
        String resolvedOtp = otp == null ? "" : otp.trim();
        String resolvedUsername = username == null ? "" : username.trim();

        ioExecutor.execute(() -> {
            AuthRepository.Result<?> verifyResult = authRepository.verifyRegisterOtp(resolvedEmail, resolvedOtp);
            if (!(verifyResult instanceof AuthRepository.Result.Success)) {
                registerState.postValue(new UiState.Error("Invalid OTP"));
                return;
            }

            AuthRepository.Result<?> registerResult = authRepository.register(
                    resolvedUsername,
                    resolvedEmail,
                    password,
                    confirmPassword);
            if (registerResult instanceof AuthRepository.Result.Success) {
                registerState.postValue(new UiState.Success());
                return;
            }
            AuthRepository.Result.Error<?> error = (AuthRepository.Result.Error<?>) registerResult;
            registerState.postValue(new UiState.Error(error.message));
        });
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

    @Override
    protected void onCleared() {
        super.onCleared();
        ioExecutor.shutdownNow();
    }

    public abstract static class UiState {
        private UiState() {
        }

        public static final class Idle extends UiState {
        }

        public static final class Loading extends UiState {
        }

        public static final class Success extends UiState {
        }

        public static final class Error extends UiState {
            private final String message;

            public Error(String message) {
                this.message = message;
            }

            public String getMessage() {
                return message;
            }
        }
    }
}
