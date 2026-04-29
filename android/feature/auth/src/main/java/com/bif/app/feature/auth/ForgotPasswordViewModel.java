package com.bif.app.feature.auth;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.bif.app.data.repository.AuthRepository;
import com.bif.app.core.network.dto.auth.VerifyOtpResponse;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@HiltViewModel
public class ForgotPasswordViewModel extends ViewModel {

    private final AuthRepository authRepository;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    // ── State (shared UiState for all flows) ─────────────────────────────────
    private final MutableLiveData<UiState> requestOtpState = new MutableLiveData<>(new UiState.Idle());
    private final MutableLiveData<UiState> verifyOtpState = new MutableLiveData<>(new UiState.Idle());
    private final MutableLiveData<UiState> resetPasswordState = new MutableLiveData<>(new UiState.Idle());

    // ── Data holders (no View references) ────────────────────────────────────
    private String email = "";
    private String resetToken = "";

    @Inject
    public ForgotPasswordViewModel(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    // ── Expose state for UI observe ──────────────────────────────────────────

    public LiveData<UiState> getRequestOtpState() {
        return requestOtpState;
    }

    public LiveData<UiState> getVerifyOtpState() {
        return verifyOtpState;
    }

    public LiveData<UiState> getResetPasswordState() {
        return resetPasswordState;
    }

    public String getEmail() {
        return email;
    }

    public String getResetToken() {
        return resetToken;
    }

    // ── Request OTP ──────────────────────────────────────────────────────────

    public void requestOtp(@NonNull String email) {
        this.email = email.trim();
        requestOtpState.setValue(new UiState.Loading());

        ioExecutor.execute(() -> {
            AuthRepository.Result<?> result = authRepository.requestOtp(this.email);
            if (result instanceof AuthRepository.Result.Success) {
                requestOtpState.postValue(new UiState.Success());
                return;
            }
            AuthRepository.Result.Error<?> error = (AuthRepository.Result.Error<?>) result;
            requestOtpState.postValue(new UiState.Error(error.message));
        });
    }

    // ── Verify OTP ───────────────────────────────────────────────────────────

    public void verifyOtp(@NonNull String email, @NonNull String otp) {
        this.email = email.trim();
        verifyOtpState.setValue(new UiState.Loading());

        ioExecutor.execute(() -> {
            AuthRepository.Result<VerifyOtpResponse> result = authRepository.verifyOtp(this.email, otp.trim());
            if (result instanceof AuthRepository.Result.Success) {
                VerifyOtpResponse data = ((AuthRepository.Result.Success<VerifyOtpResponse>) result).data;
                String token = data == null ? null : data.resetToken;
                if (token != null && !token.trim().isEmpty()) {
                    resetToken = token.trim();
                    verifyOtpState.postValue(new UiState.Success());
                } else {
                    verifyOtpState.postValue(new UiState.Error("OTP verification failed"));
                }
                return;
            }
            AuthRepository.Result.Error<VerifyOtpResponse> error = (AuthRepository.Result.Error<VerifyOtpResponse>) result;
            verifyOtpState.postValue(new UiState.Error(error.message));
        });
    }

    // ── Reset Password ───────────────────────────────────────────────────────

    public void resetPassword(@NonNull String resetToken, @NonNull String newPassword) {
        resetPasswordState.setValue(new UiState.Loading());

        ioExecutor.execute(() -> {
            AuthRepository.Result<?> result = authRepository.resetPassword(resetToken.trim(), newPassword);
            if (result instanceof AuthRepository.Result.Success) {
                resetPasswordState.postValue(new UiState.Success());
                return;
            }
            AuthRepository.Result.Error<?> error = (AuthRepository.Result.Error<?>) result;
            resetPasswordState.postValue(new UiState.Error(error.message));
        });
    }

    // ── Clear transient states ───────────────────────────────────────────────

    public void clearRequestOtpState() {
        UiState current = requestOtpState.getValue();
        if (current instanceof UiState.Success || current instanceof UiState.Error) {
            requestOtpState.setValue(new UiState.Idle());
        }
    }

    public void clearVerifyOtpState() {
        UiState current = verifyOtpState.getValue();
        if (current instanceof UiState.Success || current instanceof UiState.Error) {
            verifyOtpState.setValue(new UiState.Idle());
        }
    }

    public void clearResetPasswordState() {
        UiState current = resetPasswordState.getValue();
        if (current instanceof UiState.Success || current instanceof UiState.Error) {
            resetPasswordState.setValue(new UiState.Idle());
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        ioExecutor.shutdownNow();
    }

    // ── UiState ──────────────────────────────────────────────────────────────

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