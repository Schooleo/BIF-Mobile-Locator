package com.bif.app.core.network;

import com.bif.app.core.network.dto.auth.AuthResponse;
import com.bif.app.core.network.dto.auth.AuthStateResponse;
import com.bif.app.core.network.dto.auth.LoginRequest;
import com.bif.app.core.network.dto.auth.RefreshTokenRequest;
import com.bif.app.core.network.dto.auth.RegisterRequest;
import com.bif.app.core.network.dto.favorite.FavoriteRequestDto;
import com.bif.app.core.network.dto.favorite.FavoriteResponseDto;

import okhttp3.MultipartBody;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;

public interface RestApiService {

    @POST("auth/register")
    Call<AuthResponse> register(@Body RegisterRequest request);

    @POST("auth/login")
    Call<AuthResponse> login(@Body LoginRequest request);

    @POST("auth/refresh")
    Call<AuthResponse> refresh(@Body RefreshTokenRequest request);

    @POST("auth/logout")
    Call<Void> logout(@Body RefreshTokenRequest request);

    @GET("auth/me")
    Call<AuthStateResponse> getAuthState();

    @GET("favorites/me")
    Call<List<FavoriteResponseDto>> getMyFavorites();

    @POST("favorites/me")
    Call<FavoriteResponseDto> upsertMyFavorite(@Body FavoriteRequestDto request);

    @DELETE("favorites/me/{id}")
    Call<Void> deleteMyFavorite(@Path("id") String id);

    // Example: A multipart POST request for uploading a profile picture
    @Multipart
    @POST("users/avatar")
    Call<Void> uploadAvatar(@Part MultipartBody.Part image);

    // @GET("config/features")
    // Call<FeatureConfig> getFeatureConfig();
}