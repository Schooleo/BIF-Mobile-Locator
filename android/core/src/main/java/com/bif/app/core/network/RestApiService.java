package com.bif.app.core.network;

import com.bif.app.core.network.dto.auth.AuthResponse;
import com.bif.app.core.network.dto.auth.AuthStateResponse;
import com.bif.app.core.network.dto.auth.LoginRequest;
import com.bif.app.core.network.dto.auth.RefreshTokenRequest;
import com.bif.app.core.network.dto.auth.RegisterRequest;
import com.bif.app.core.network.dto.favorite.FavoriteRequestDto;
import com.bif.app.core.network.dto.favorite.FavoriteResponseDto;
import com.bif.app.core.network.dto.friendship.CreateFriendRequestDto;
import com.bif.app.core.network.dto.friendship.FriendshipApiModel;
import com.bif.app.core.network.dto.profile.ProfileMetadataResponse;
import com.bif.app.core.network.dto.profile.UpdateMyProfileRequest;
import com.bif.app.core.network.dto.group.AddMemberRequestDto;
import com.bif.app.core.network.dto.group.CreateGroupRequestDto;
import com.bif.app.core.network.dto.group.GroupApiModel;
import com.bif.app.core.network.dto.user.UserApiModel;
import com.bif.app.core.network.dto.group.UpdateGroupRequestDto;
import com.bif.app.core.network.dto.group.UpdateMemberRoleRequestDto;
import com.bif.app.core.network.dto.chat.ChatMessageDto;
import com.bif.app.core.network.dto.trip.TripPlanDto;
import com.bif.app.core.network.dto.trip.TripStopDto;
import com.bif.app.core.network.dto.place.PlaceDto;
import com.bif.app.core.network.dto.place.PlaceReviewDto;
import com.bif.app.core.network.dto.route.RouteRequestDto;
import com.bif.app.core.network.dto.route.RouteResponseDto;
import com.bif.app.core.network.dto.sync.SyncRequestDto;
import com.bif.app.core.network.dto.sync.SyncResponseDto;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Streaming;

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

    @GET("users/me/profile-metadata")
    Call<ProfileMetadataResponse> getMyProfileMetadata();

    @PATCH("users/me/profile")
    Call<ProfileMetadataResponse> updateMyProfile(@Body UpdateMyProfileRequest request);

    // Example: A multipart POST request for uploading a profile picture
    @Multipart
    @POST("users/avatar")
    Call<Void> uploadAvatar(@Part MultipartBody.Part image);

    @GET("users")
    Call<List<UserApiModel>> getUsers();

    @GET("friends")
    Call<List<UserApiModel>> getFriends();

    @DELETE("friends/{id}")
    Call<Void> unfriend(@Path("id") String id);

    @GET("friends/requests/incoming")
    Call<List<FriendshipApiModel>> getIncomingFriendRequests();

    @GET("friends/requests/outgoing")
    Call<List<FriendshipApiModel>> getOutgoingFriendRequests();

    @POST("friends/requests")
    Call<FriendshipApiModel> sendFriendRequest(@Body CreateFriendRequestDto request);

    @POST("friends/{id}/accept")
    Call<FriendshipApiModel> acceptFriendRequest(@Path("id") String id);

    @POST("friends/{id}/reject")
    Call<FriendshipApiModel> rejectFriendRequest(@Path("id") String id);

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
            @Body UpdateGroupRequestDto request);

    @PATCH("groups/{groupId}")
    Call<GroupApiModel> patchGroup(
            @Path("groupId") String groupId,
            @Query("actorId") String actorId,
            @Body UpdateGroupRequestDto request);

    @DELETE("groups/{groupId}")
    Call<Void> deleteGroup(
            @Path("groupId") String groupId,
            @Query("actorId") String actorId);

    @DELETE("groups/{groupId}/members/{memberId}")
    Call<GroupApiModel> removeMember(
            @Path("groupId") String groupId,
            @Path("memberId") String memberId,
            @Query("actorId") String actorId);

    @POST("groups/{groupId}/members")
    Call<GroupApiModel> addMember(
            @Path("groupId") String groupId,
            @Query("actorId") String actorId,
            @Body AddMemberRequestDto request);

    @PATCH("groups/{groupId}/members/{memberId}/role")
    Call<GroupApiModel> updateMemberRole(
            @Path("groupId") String groupId,
            @Path("memberId") String memberId,
            @Query("actorId") String actorId,
            @Body UpdateMemberRoleRequestDto request);

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

    @GET("chat/group/{groupId}")
    Call<List<ChatMessageDto>> getChatMessages(@Path("groupId") String groupId);

    @POST("chat")
    Call<ChatMessageDto> postChatMessage(@Body ChatMessageDto message);

    @PATCH("chat/{id}/confirm")
    Call<ChatMessageDto> confirmMessage(@Path("id") String id);

    @GET("trips/group/{groupId}")
    Call<List<TripPlanDto>> getTripsByGroup(@Path("groupId") String groupId);

    @POST("trips/{tripId}/stops")
    Call<TripPlanDto> addTripStop(@Path("tripId") String tripId,
            @Body TripStopDto stop);

    @DELETE("trips/{tripId}/stops/{stopId}")
    Call<TripPlanDto> removeTripStop(@Path("tripId") String tripId,
            @Path("stopId") String stopId);

    @PUT("trips/{tripId}/stops/reorder")
    Call<TripPlanDto> rearrangeTripStops(@Path("tripId") String tripId,
            @Body List<TripStopDto> stops);

    @POST("routes")
    Call<RouteResponseDto> routeTrip(@Body RouteRequestDto request);

    @Streaming
    @GET("maps/city")
    Call<ResponseBody> downloadCityMap(
            @Query("lat") double latitude,
            @Query("lon") double longitude);

    @POST("sync")
    Call<SyncResponseDto> sync(@Body SyncRequestDto request);
}
