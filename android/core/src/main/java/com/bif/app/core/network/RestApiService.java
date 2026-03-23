package com.bif.app.core.network;

import com.bif.app.core.network.dto.CreateGroupRequestDto;
import com.bif.app.core.network.dto.GroupApiModel;
import com.bif.app.core.network.dto.UserApiModel;
import com.bif.app.core.network.dto.UpdateGroupRequestDto;
import com.bif.app.core.network.dto.PlaceDto;
import com.bif.app.core.network.dto.PlaceReviewDto;
import com.bif.app.core.network.dto.SyncRequestDto;
import com.bif.app.core.network.dto.SyncResponseDto;

import java.util.List;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface RestApiService {

    @Multipart
    @POST("users/avatar")
    Call<Void> uploadAvatar(@Part MultipartBody.Part image);

    @GET("users")
    Call<List<UserApiModel>> getUsers();

    @GET("groups/user/{userId}")
    Call<List<GroupApiModel>> getGroupsByUser(@Path("userId") String userId);

    @GET("groups/{groupId}")
    Call<GroupApiModel> getGroupById(@Path("groupId") String groupId);

    @POST("groups")
    Call<GroupApiModel> createGroup(@Body CreateGroupRequestDto request);

    @PUT("groups/{groupId}")
    Call<GroupApiModel> updateGroup(
            @Path("groupId") String groupId,
            @Query("actorId") String actorId,
            @Body UpdateGroupRequestDto request
    );

    @DELETE("groups/{groupId}")
    Call<Void> deleteGroup(
            @Path("groupId") String groupId,
            @Query("actorId") String actorId
    );

    @DELETE("groups/{groupId}/members/{memberId}")
    Call<GroupApiModel> removeMember(
            @Path("groupId") String groupId,
            @Path("memberId") String memberId,
            @Query("actorId") String actorId
    );

    // @GET("config/features")
    // Call<FeatureConfig> getFeatureConfig();
    // Places
    @GET("places/search")
    Call<List<PlaceDto>> searchServerPlaces(@Query("q") String query);

    @POST("places")
    Call<PlaceDto> upsertPlace(@Body PlaceDto place);

    @POST("places/from-search")
    Call<PlaceDto> saveFromSearch(@Body PlaceDto place);

    @DELETE("places/{id}")
    Call<Void> deletePlace(@Path("id") String id);

    @POST("places/{id}/reviews")
    Call<PlaceDto> addReview(@Path("id") String placeId,
                             @Body PlaceReviewDto review);

    // Sync
    @POST("sync")
    Call<SyncResponseDto> sync(@Body SyncRequestDto request);
}