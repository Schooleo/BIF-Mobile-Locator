package com.bif.app.feature.profile;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.auth.ChangePasswordRequest;
import com.bif.app.core.network.dto.auth.ChangePasswordResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChangePasswordViewModel extends ViewModel {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private final MutableLiveData<UiState> changePasswordState = new MutableLiveData<>(new UiState.Idle());

    public LiveData<UiState> getChangePasswordState() {
        return changePasswordState;
    }

    public void changePassword(@NonNull RestApiService restApiService,
                               @NonNull String currentPassword,
                               @NonNull String newPassword) {
        changePasswordState.setValue(new UiState.Loading());

        restApiService.changePassword(new ChangePasswordRequest(currentPassword, newPassword))
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<ChangePasswordResponse> call,
                                           @NonNull Response<ChangePasswordResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().success) {
                            changePasswordState.postValue(new UiState.Success());
                            return;
                        }

                        String message = resolveErrorMessage(response);

                        changePasswordState.postValue(new UiState.Error(message));
                    }

                    @Override
                    public void onFailure(@NonNull Call<ChangePasswordResponse> call,
                                          @NonNull Throwable t) {
                        changePasswordState.postValue(new UiState.Error("Network error. Please try again."));
                    }
                });
    }

    public void clearChangePasswordState() {
        UiState current = changePasswordState.getValue();
        if (current instanceof UiState.Success || current instanceof UiState.Error) {
            changePasswordState.setValue(new UiState.Idle());
        }
    }

    public enum ValidationError {
        NONE,
        CURRENT_PASSWORD_EMPTY,
        NEW_PASSWORD_TOO_SHORT,
        CONFIRM_PASSWORD_MISMATCH
    }

    public ValidationError validate(String currentPassword, String newPassword, String confirmPassword) {
        String current = currentPassword == null ? "" : currentPassword.trim();
        String next = newPassword == null ? "" : newPassword;
        String confirm = confirmPassword == null ? "" : confirmPassword;

        if (current.isEmpty()) {
            return ValidationError.CURRENT_PASSWORD_EMPTY;
        }

        if (next.length() < MIN_PASSWORD_LENGTH) {
            return ValidationError.NEW_PASSWORD_TOO_SHORT;
        }

        if (!next.equals(confirm)) {
            return ValidationError.CONFIRM_PASSWORD_MISMATCH;
        }

        return ValidationError.NONE;
    }

    private String resolveErrorMessage(Response<ChangePasswordResponse> response) {
        if (response.body() != null
                && response.body().message != null
                && !response.body().message.trim().isEmpty()) {
            return response.body().message.trim();
        }

        String parsedErrorBody = parseErrorBody(response);
        if (parsedErrorBody != null) {
            return parsedErrorBody;
        }

        if (response.code() == 400) {
            return "Current password is incorrect";
        }

        String responseMessage = response.message();
        if (responseMessage != null && !responseMessage.trim().isEmpty()) {
            return responseMessage.trim();
        }

        return "Unknown error";
    }

    private String parseErrorBody(Response<?> response) {
        if (response == null || response.errorBody() == null) {
            return null;
        }

        try {
            String errorStr = response.errorBody().string();
            if (errorStr == null || errorStr.trim().isEmpty()) {
                return null;
            }

            org.json.JSONObject jsonObject = new org.json.JSONObject(errorStr);
            if (jsonObject.has("message") && !jsonObject.isNull("message")) {
                String message = jsonObject.getString("message");
                if (message != null && !message.trim().isEmpty()) {
                    return message.trim();
                }
            }
        } catch (Exception ignored) {
        }

        return null;
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
                this.message = message == null ? "Unknown error" : message;
            }

            public String getMessage() {
                return message;
            }
        }
    }
}
