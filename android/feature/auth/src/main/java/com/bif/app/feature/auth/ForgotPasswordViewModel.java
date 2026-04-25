package com.bif.app.feature.auth;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.auth.ForgotPasswordRequestOtpResponse;
import com.bif.app.core.network.dto.auth.RequestOtpRequest;
import com.bif.app.core.network.dto.auth.ResetPasswordRequest;
import com.bif.app.core.network.dto.auth.ResetPasswordResponse;
import com.bif.app.core.network.dto.auth.VerifyOtpRequest;
import com.bif.app.core.network.dto.auth.VerifyOtpResponse;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@HiltViewModel
public class ForgotPasswordViewModel extends ViewModel {

    private final RestApiService restApiService;

    // ── State (shared UiState for all flows) ─────────────────────────────────
    private final MutableLiveData<UiState> requestOtpState = new MutableLiveData<>(new UiState.Idle());
    private final MutableLiveData<UiState> verifyOtpState = new MutableLiveData<>(new UiState.Idle());
    private final MutableLiveData<UiState> resetPasswordState = new MutableLiveData<>(new UiState.Idle());

    // ── Data holders (no View references) ────────────────────────────────────
    private String email = "";
    private String resetToken = "";

    @Inject
    public ForgotPasswordViewModel(RestApiService restApiService) {
        this.restApiService = restApiService;
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

        restApiService.requestForgotPasswordOtp(new RequestOtpRequest(this.email))
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<ForgotPasswordRequestOtpResponse> call,
                                           @NonNull Response<ForgotPasswordRequestOtpResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().success) {
                            requestOtpState.postValue(new UiState.Success());
                            return;
                        }

                        String message = resolveRequestOtpErrorMessage(response);
                        requestOtpState.postValue(new UiState.Error(message));
                    }

                    @Override
                    public void onFailure(@NonNull Call<ForgotPasswordRequestOtpResponse> call,
                                          @NonNull Throwable t) {
                        requestOtpState.postValue(new UiState.Error("Network error. Please try again."));
                    }
                });
    }

    // ── Verify OTP ───────────────────────────────────────────────────────────

    public void verifyOtp(@NonNull String email, @NonNull String otp) {
        this.email = email.trim();
        verifyOtpState.setValue(new UiState.Loading());

        restApiService.verifyForgotPasswordOtp(new VerifyOtpRequest(this.email, otp.trim()))
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<VerifyOtpResponse> call,
                                           @NonNull Response<VerifyOtpResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().success) {
                            resetToken = response.body().resetToken;
                            verifyOtpState.postValue(new UiState.Success());
                            return;
                        }

                        String message = resolveVerifyOtpErrorMessage(response);
                        verifyOtpState.postValue(new UiState.Error(message));
                    }

                    @Override
                    public void onFailure(@NonNull Call<VerifyOtpResponse> call,
                                          @NonNull Throwable t) {
                        verifyOtpState.postValue(new UiState.Error("Network error. Please try again."));
                    }
                });
    }

    // ── Reset Password ───────────────────────────────────────────────────────

    public void resetPassword(@NonNull String resetToken, @NonNull String newPassword) {
        resetPasswordState.setValue(new UiState.Loading());

        restApiService.resetForgotPassword(new ResetPasswordRequest(resetToken.trim(), newPassword))
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<ResetPasswordResponse> call,
                                           @NonNull Response<ResetPasswordResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().success) {
                            resetPasswordState.postValue(new UiState.Success());
                            return;
                        }

                        String message = resolveResetPasswordErrorMessage(response);
                        resetPasswordState.postValue(new UiState.Error(message));
                    }

                    @Override
                    public void onFailure(@NonNull Call<ResetPasswordResponse> call,
                                          @NonNull Throwable t) {
                        resetPasswordState.postValue(new UiState.Error("Network error. Please try again."));
                    }
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

    // ── Error message resolvers ──────────────────────────────────────────────

    private String parseErrorBody(Response<?> response) {
        if (response != null && response.errorBody() != null) {
            try {
                String errorStr = response.errorBody().string();
                if (errorStr != null && !errorStr.trim().isEmpty()) {
                    org.json.JSONObject jsonObject = new org.json.JSONObject(errorStr);
                    if (jsonObject.has("message") && !jsonObject.isNull("message")) {
                        String msg = jsonObject.getString("message");
                        if (msg != null && !msg.trim().isEmpty()) {
                            return msg.trim();
                        }
                    }
                    if (jsonObject.has("error") && !jsonObject.isNull("error")) {
                        String err = jsonObject.getString("error");
                        if (err != null && !err.trim().isEmpty()) {
                            return err.trim();
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String resolveRequestOtpErrorMessage(Response<ForgotPasswordRequestOtpResponse> response) {
        ForgotPasswordRequestOtpResponse body = response.body();
        if (body != null && body.message != null && !body.message.trim().isEmpty()) {
            return body.message.trim();
        }

        String parsedMessage = parseErrorBody(response);
        if (parsedMessage != null) {
            return parsedMessage;
        }

        if (response.code() == 404) {
            return "Email does not exist";
        }

        return "Request OTP failed";
    }

    private String resolveVerifyOtpErrorMessage(Response<VerifyOtpResponse> response) {
        String parsedMessage = parseErrorBody(response);
        if (parsedMessage != null) {
            return parsedMessage;
        }

        if (response.code() == 400) {
            return "Invalid or expired OTP";
        }
        return "OTP verification failed";
    }

    private String resolveResetPasswordErrorMessage(Response<ResetPasswordResponse> response) {
        ResetPasswordResponse body = response.body();
        if (body != null && body.message != null && !body.message.trim().isEmpty()) {
            return body.message.trim();
        }

        String parsedMessage = parseErrorBody(response);
        if (parsedMessage != null) {
            return parsedMessage;
        }

        return "Password reset failed";
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