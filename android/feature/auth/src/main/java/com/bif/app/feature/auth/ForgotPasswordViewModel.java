package com.bif.app.feature.auth;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.auth.ForgotPasswordRequestOtpResponse;
import com.bif.app.core.network.dto.auth.RequestOtpRequest;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@HiltViewModel
public class ForgotPasswordViewModel extends ViewModel {

    private final RestApiService restApiService;
    private final MutableLiveData<RequestOtpState> requestOtpState = new MutableLiveData<>(new RequestOtpState.Idle());

    @Inject
    public ForgotPasswordViewModel(RestApiService restApiService) {
        this.restApiService = restApiService;
    }

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

                        String message = resolveErrorMessage(response);
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

    private String resolveErrorMessage(Response<ForgotPasswordRequestOtpResponse> response) {
        ForgotPasswordRequestOtpResponse body = response.body();
        if (body != null && body.message != null && !body.message.trim().isEmpty()) {
            return body.message.trim();
        }

        if (response.code() == 404) {
            return "Email does not exist";
        }

        return "Request OTP failed";
    }

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
}