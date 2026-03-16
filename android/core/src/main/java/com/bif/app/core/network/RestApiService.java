package com.bif.app.core.network;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface RestApiService {

    // Example: A multipart POST request for uploading a profile picture
    @Multipart
    @POST("users/avatar")
    Call<Void> uploadAvatar(@Part MultipartBody.Part image);

    // @GET("config/features")
    // Call<FeatureConfig> getFeatureConfig();
}