package com.bif.app.feature.auth;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.auth.ForgotPasswordRequestOtpResponse;
import com.bif.app.core.network.dto.auth.RequestOtpRequest;
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
    private final MutableLiveData<RequestOtpState> requestOtpState = new MutableLiveData<>(new RequestOtpState.Idle());
    private final MutableLiveData<VerifyOtpState> verifyOtpState = new MutableLiveData<>(new VerifyOtpState.Idle());

    @Inject
    public ForgotPasswordViewModel(RestApiService restApiService) {
        this.restApiService = restApiService;
    }

    // ── Request OTP ──────────────────────────────────────────────────────────

    public LiveData<RequestOtpState> getRequestOtpState() {
        return requestOtpState;
    }

    public void requestOtp(@NonNull String email) {
        String normalizedEmail = email.trim();
        requestOtpState.setValue(new RequestOtpState.Loading());

        restApiService.requestForgotPasswordOtp(new RequestOtpRequest(normalizedEmail))
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<ForgotPasswordRequestOtpResponse> call,
                                           @NonNull Response<ForgotPasswordRequestOtpResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().success) {
                            requestOtpState.postValue(new RequestOtpState.Success(normalizedEmail));
                            return;
                        }

                        String message = resolveRequestOtpErrorMessage(response);
                        requestOtpState.postValue(new RequestOtpState.Error(message));
                    }

                    @Override
                    public void onFailure(@NonNull Call<ForgotPasswordRequestOtpResponse> call,
                                          @NonNull Throwable t) {
                        requestOtpState.postValue(new RequestOtpState.Error("Network error. Please try again."));
                    }
                });
    }

    public void clearTransientState() {
        RequestOtpState current = requestOtpState.getValue();
        if (current instanceof RequestOtpState.Success || current instanceof RequestOtpState.Error) {
            requestOtpState.setValue(new RequestOtpState.Idle());
        }
    }

    private String resolveRequestOtpErrorMessage(Response<ForgotPasswordRequestOtpResponse> response) {
        ForgotPasswordRequestOtpResponse body = response.body();
        if (body != null && body.message != null && !body.message.trim().isEmpty()) {
            return body.message.trim();
        }

        if (response.code() == 404) {
            return "Email does not exist";
        }

        return "Request OTP failed";
    }

    // ── Verify OTP ───────────────────────────────────────────────────────────

    public LiveData<VerifyOtpState> getVerifyOtpState() {
        return verifyOtpState;
    }

    public void verifyOtp(@NonNull String email, @NonNull String otp) {
        String normalizedEmail = email.trim();
        String normalizedOtp = otp.trim();
        verifyOtpState.setValue(new VerifyOtpState.Loading());

        restApiService.verifyForgotPasswordOtp(new VerifyOtpRequest(normalizedEmail, normalizedOtp))
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<VerifyOtpResponse> call,
                                           @NonNull Response<VerifyOtpResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().success) {
                            String resetToken = response.body().resetToken;
                            verifyOtpState.postValue(new VerifyOtpState.Success(resetToken));
                            return;
                        }

                        String message = resolveVerifyOtpErrorMessage(response);
                        verifyOtpState.postValue(new VerifyOtpState.Error(message));
                    }

                    @Override
                    public void onFailure(@NonNull Call<VerifyOtpResponse> call,
                                          @NonNull Throwable t) {
                        verifyOtpState.postValue(new VerifyOtpState.Error("Network error. Please try again."));
                    }
                });
    }

    public void clearVerifyOtpState() {
        VerifyOtpState current = verifyOtpState.getValue();
        if (current instanceof VerifyOtpState.Success || current instanceof VerifyOtpState.Error) {
            verifyOtpState.setValue(new VerifyOtpState.Idle());
        }
    }

    private String resolveVerifyOtpErrorMessage(Response<VerifyOtpResponse> response) {
        if (response.code() == 400) {
            return "Invalid or expired OTP";
        }
        return "OTP verification failed";
    }

    // ── State classes ────────────────────────────────────────────────────────

    public abstract static class RequestOtpState {
        private RequestOtpState() {
        }

        public static final class Idle extends RequestOtpState {
        }

        public static final class Loading extends RequestOtpState {
        }

        public static final class Success extends RequestOtpState {
            private final String email;

            public Success(String email) {
                this.email = email;
            }

            public String getEmail() {
                return email;
            }
        }

        public static final class Error extends RequestOtpState {
            private final String message;

            public Error(String message) {
                this.message = message;
            }

            public String getMessage() {
                return message;
            }
        }
    }

    public abstract static class VerifyOtpState {
        private VerifyOtpState() {
        }

        public static final class Idle extends VerifyOtpState {
        }

        public static final class Loading extends VerifyOtpState {
        }

        public static final class Success extends VerifyOtpState {
            private final String resetToken;

            public Success(String resetToken) {
                this.resetToken = resetToken;
            }

            public String getResetToken() {
                return resetToken;
            }
        }

        public static final class Error extends VerifyOtpState {
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