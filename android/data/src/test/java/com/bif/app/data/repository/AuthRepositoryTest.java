package com.bif.app.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.auth.ForgotPasswordRequestOtpResponse;
import com.bif.app.core.network.dto.auth.ResetPasswordResponse;
import com.bif.app.core.network.dto.auth.VerifyOtpResponse;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

public class AuthRepositoryTest {

    @Mock
    private RestApiService mockRestApiService;

    private AuthRepository repository;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        repository = new AuthRepository(mockRestApiService);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void requestOtp_whenSuccessful_returnsSuccessResult() throws Exception {
        ForgotPasswordRequestOtpResponse body = new ForgotPasswordRequestOtpResponse();
        body.success = true;
        body.message = "OTP sent";

        Call<ForgotPasswordRequestOtpResponse> call = (Call<ForgotPasswordRequestOtpResponse>) mock(Call.class);
        when(call.execute()).thenReturn(Response.success(body));
        when(mockRestApiService.requestForgotPasswordOtp(any())).thenReturn(call);

        AuthRepository.Result<ForgotPasswordRequestOtpResponse> result = repository.requestOtp("alex@bif.local");

        assertTrue(result instanceof AuthRepository.Result.Success);
        AuthRepository.Result.Success<ForgotPasswordRequestOtpResponse> success =
                (AuthRepository.Result.Success<ForgotPasswordRequestOtpResponse>) result;
        assertTrue(success.data.success);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void verifyOtp_whenApiError_returnsErrorResult() throws Exception {
        Call<VerifyOtpResponse> call = (Call<VerifyOtpResponse>) mock(Call.class);
        when(call.execute()).thenReturn(
                Response.error(400, ResponseBody.create(null, "{\"message\":\"Invalid OTP\"}")));
        when(mockRestApiService.verifyForgotPasswordOtp(any())).thenReturn(call);

        AuthRepository.Result<VerifyOtpResponse> result = repository.verifyOtp("alex@bif.local", "000000");

        assertTrue(result instanceof AuthRepository.Result.Error);
        AuthRepository.Result.Error<VerifyOtpResponse> error =
                (AuthRepository.Result.Error<VerifyOtpResponse>) result;
        assertEquals("Invalid OTP", error.message);
        assertEquals(400, error.code);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void resetPassword_whenIOException_returnsErrorResult() throws Exception {
        Call<ResetPasswordResponse> call = (Call<ResetPasswordResponse>) mock(Call.class);
        when(call.execute()).thenThrow(new IOException("offline"));
        when(mockRestApiService.resetForgotPassword(any())).thenReturn(call);

        AuthRepository.Result<ResetPasswordResponse> result = repository.resetPassword("token", "Password123!");

        assertTrue(result instanceof AuthRepository.Result.Error);
        AuthRepository.Result.Error<ResetPasswordResponse> error =
                (AuthRepository.Result.Error<ResetPasswordResponse>) result;
        assertEquals("Network error. Please try again.", error.message);
        assertEquals(0, error.code);
    }
}