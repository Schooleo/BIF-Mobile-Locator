package com.bif.app.data.repository;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.auth.ChangePasswordRequest;
import com.bif.app.core.network.dto.auth.ChangePasswordResponse;
import com.bif.app.core.network.dto.auth.AuthResponse;
import com.bif.app.core.network.dto.auth.ForgotPasswordRequestOtpResponse;
import com.bif.app.core.network.dto.auth.RequestOtpRequest;
import com.bif.app.core.network.dto.auth.RegisterOtpRequest;
import com.bif.app.core.network.dto.auth.RegisterOtpResponse;
import com.bif.app.core.network.dto.auth.RegisterRequest;
import com.bif.app.core.network.dto.auth.RegisterVerifyOtpRequest;
import com.bif.app.core.network.dto.auth.RegisterVerifyOtpResponse;
import com.bif.app.core.network.dto.auth.ResetPasswordRequest;
import com.bif.app.core.network.dto.auth.ResetPasswordResponse;
import com.bif.app.core.network.dto.auth.VerifyOtpRequest;
import com.bif.app.core.network.dto.auth.VerifyOtpResponse;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.ResponseBody;
import retrofit2.Response;

@Singleton
public class AuthRepository {

    private final RestApiService restApiService;

    @Inject
    public AuthRepository(RestApiService restApiService) {
        this.restApiService = restApiService;
    }

    public Result<ForgotPasswordRequestOtpResponse> requestOtp(String email) {
        if (isBlank(email)) {
            return new Result.Error<>("Email is required", 0, null);
        }

        try {
            Response<ForgotPasswordRequestOtpResponse> response = restApiService
                    .requestForgotPasswordOtp(new RequestOtpRequest(email.trim()))
                    .execute();

            if (response.isSuccessful() && response.body() != null) {
                ForgotPasswordRequestOtpResponse body = response.body();
                if (body.success) {
                    return new Result.Success<>(body);
                }
                return new Result.Error<>(
                        coalesce(body.message, "Request OTP failed"),
                        response.code(),
                        null);
            }

            return new Result.Error<>(
                    parseErrorMessage(response, "Request OTP failed"),
                    response.code(),
                    null);
        } catch (IOException ioException) {
            return new Result.Error<>("Network error. Please try again.", 0, ioException);
        } catch (Exception exception) {
            return new Result.Error<>("Unexpected error. Please try again.", 0, exception);
        }
    }

    public Result<RegisterOtpResponse> requestRegisterOtp(String email) {
        if (isBlank(email)) {
            return new Result.Error<>("Email is required", 0, null);
        }

        try {
            Response<RegisterOtpResponse> response = restApiService
                    .requestRegisterOtp(new RegisterOtpRequest(email.trim()))
                    .execute();

            if (response.isSuccessful() && response.body() != null) {
                RegisterOtpResponse body = response.body();
                if (body.success) {
                    return new Result.Success<>(body);
                }
                return new Result.Error<>(
                        coalesce(body.message, "Request OTP failed"),
                        response.code(),
                        null);
            }

            return new Result.Error<>(
                    parseErrorMessage(response, "Request OTP failed"),
                    response.code(),
                    null);
        } catch (IOException ioException) {
            return new Result.Error<>("Network error. Please try again.", 0, ioException);
        } catch (Exception exception) {
            return new Result.Error<>("Unexpected error. Please try again.", 0, exception);
        }
    }

    public Result<VerifyOtpResponse> verifyOtp(String email, String otp) {
        if (isBlank(email)) {
            return new Result.Error<>("Email is required", 0, null);
        }
        if (isBlank(otp)) {
            return new Result.Error<>("OTP is required", 0, null);
        }

        try {
            Response<VerifyOtpResponse> response = restApiService
                    .verifyForgotPasswordOtp(new VerifyOtpRequest(email.trim(), otp.trim()))
                    .execute();

            if (response.isSuccessful() && response.body() != null) {
                VerifyOtpResponse body = response.body();
                if (body.success && !isBlank(body.resetToken)) {
                    return new Result.Success<>(body);
                }
                return new Result.Error<>("OTP is invalid or expired", response.code(), null);
            }

            return new Result.Error<>(
                    parseErrorMessage(response, "OTP is invalid or expired"),
                    response.code(),
                    null);
        } catch (IOException ioException) {
            return new Result.Error<>("Network error. Please try again.", 0, ioException);
        } catch (Exception exception) {
            return new Result.Error<>("Unexpected error. Please try again.", 0, exception);
        }
    }

    public Result<RegisterVerifyOtpResponse> verifyRegisterOtp(String email, String otp) {
        if (isBlank(email)) {
            return new Result.Error<>("Email is required", 0, null);
        }
        if (isBlank(otp)) {
            return new Result.Error<>("OTP is required", 0, null);
        }

        try {
            Response<RegisterVerifyOtpResponse> response = restApiService
                    .verifyRegisterOtp(new RegisterVerifyOtpRequest(email.trim(), otp.trim()))
                    .execute();

            if (response.isSuccessful() && response.body() != null) {
                RegisterVerifyOtpResponse body = response.body();
                if (body.success) {
                    return new Result.Success<>(body);
                }
                return new Result.Error<>("Invalid OTP", response.code(), null);
            }

            return new Result.Error<>(
                    parseErrorMessage(response, "Invalid OTP"),
                    response.code(),
                    null);
        } catch (IOException ioException) {
            return new Result.Error<>("Network error. Please try again.", 0, ioException);
        } catch (Exception exception) {
            return new Result.Error<>("Unexpected error. Please try again.", 0, exception);
        }
    }

    public Result<AuthResponse> register(String username, String email, String password, String confirmPassword) {
        if (isBlank(username)) {
            return new Result.Error<>("Username is required", 0, null);
        }
        if (isBlank(email)) {
            return new Result.Error<>("Email is required", 0, null);
        }
        if (isBlank(password) || isBlank(confirmPassword)) {
            return new Result.Error<>("Password is required", 0, null);
        }

        try {
            Response<AuthResponse> response = restApiService
                    .register(new RegisterRequest(username.trim(), email.trim(), password, confirmPassword))
                    .execute();

            if (response.isSuccessful() && response.body() != null) {
                return new Result.Success<>(response.body());
            }

            return new Result.Error<>(
                    parseErrorMessage(response, "Registration failed"),
                    response.code(),
                    null);
        } catch (IOException ioException) {
            return new Result.Error<>("Network error. Please try again.", 0, ioException);
        } catch (Exception exception) {
            return new Result.Error<>("Unexpected error. Please try again.", 0, exception);
        }
    }

    public Result<ResetPasswordResponse> resetPassword(String token, String newPassword) {
        if (isBlank(token)) {
            return new Result.Error<>("Reset token is required", 0, null);
        }
        if (isBlank(newPassword)) {
            return new Result.Error<>("New password is required", 0, null);
        }

        try {
            Response<ResetPasswordResponse> response = restApiService
                    .resetForgotPassword(new ResetPasswordRequest(token.trim(), newPassword))
                    .execute();

            if (response.isSuccessful() && response.body() != null) {
                ResetPasswordResponse body = response.body();
                if (body.success) {
                    return new Result.Success<>(body);
                }
                return new Result.Error<>(
                        coalesce(body.message, "Reset password failed"),
                        response.code(),
                        null);
            }

            return new Result.Error<>(
                    parseErrorMessage(response, "Reset password failed"),
                    response.code(),
                    null);
        } catch (IOException ioException) {
            return new Result.Error<>("Network error. Please try again.", 0, ioException);
        } catch (Exception exception) {
            return new Result.Error<>("Unexpected error. Please try again.", 0, exception);
        }
    }

    public Result<ChangePasswordResponse> changePassword(String currentPassword, String newPassword) {
        if (isBlank(currentPassword)) {
            return new Result.Error<>("Current password is required", 0, null);
        }
        if (isBlank(newPassword)) {
            return new Result.Error<>("New password is required", 0, null);
        }

        try {
            Response<ChangePasswordResponse> response = restApiService
                    .changePassword(new ChangePasswordRequest(currentPassword, newPassword))
                    .execute();

            if (response.isSuccessful() && response.body() != null) {
                ChangePasswordResponse body = response.body();
                if (body.success) {
                    return new Result.Success<>(body);
                }
                return new Result.Error<>(
                        coalesce(body.message, "Change password failed"),
                        response.code(),
                        null);
            }

            return new Result.Error<>(
                    parseErrorMessage(response, "Change password failed"),
                    response.code(),
                    null);
        } catch (IOException ioException) {
            return new Result.Error<>("Network error. Please try again.", 0, ioException);
        } catch (Exception exception) {
            return new Result.Error<>("Unexpected error. Please try again.", 0, exception);
        }
    }

    private String parseErrorMessage(Response<?> response, String fallback) {
        if (response == null) {
            return fallback;
        }

        try (ResponseBody errorBody = response.errorBody()) {
            if (errorBody != null) {
                String rawError = errorBody.string();
                if (!isBlank(rawError)) {
                    JsonElement parsed = JsonParser.parseString(rawError);
                    if (parsed != null && parsed.isJsonObject()) {
                        JsonObject jsonObject = parsed.getAsJsonObject();
                        if (jsonObject.has("message") && !jsonObject.get("message").isJsonNull()) {
                            return coalesce(jsonObject.get("message").getAsString(), fallback);
                        }
                        if (jsonObject.has("error") && !jsonObject.get("error").isJsonNull()) {
                            return coalesce(jsonObject.get("error").getAsString(), fallback);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Fall back to generic message when body is not parseable.
        }

        return fallback;
    }

    private static String coalesce(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public abstract static class Result<T> {
        private Result() {
        }

        public static final class Success<T> extends Result<T> {
            public final T data;

            public Success(T data) {
                this.data = data;
            }
        }

        public static final class Error<T> extends Result<T> {
            public final String message;
            public final int code;
            public final Throwable throwable;

            public Error(String message, int code, Throwable throwable) {
                this.message = message;
                this.code = code;
                this.throwable = throwable;
            }
        }
    }
}