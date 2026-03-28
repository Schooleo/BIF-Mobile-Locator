package com.bif.app.feature.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.profile.ProfileMetadataResponse;
import com.bif.app.core.network.dto.profile.UpdateMyProfileRequest;
import com.bif.app.core.utils.UserPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@RunWith(MockitoJUnitRunner.class)
public class ProfileRepositoryTest {

    @Mock
    private Context context;

    @Mock
    private RestApiService restApiService;

    @Mock
    private Call<ProfileMetadataResponse> profileMetadataCall;

    @Mock
    private Call<ProfileMetadataResponse> updateProfileCall;

    private ProfileRepository repository;

    @Before
    public void setUp() {
        repository = new ProfileRepository(context, restApiService);
    }

    @Test
    public void readLocalProfile_returnsSanitizedValues() {
        try (MockedStatic<UserPreferences> prefs = org.mockito.Mockito.mockStatic(UserPreferences.class)) {
            prefs.when(() -> UserPreferences.isLoggedIn(context)).thenReturn(true);
            prefs.when(() -> UserPreferences.getUsername(context)).thenReturn("alice");
            prefs.when(() -> UserPreferences.getEmail(context)).thenReturn(null);
            prefs.when(() -> UserPreferences.getAvatarUri(context)).thenReturn("");

            ProfileRepository.LocalProfile localProfile = repository.readLocalProfile();

            assertTrue(localProfile.isLoggedIn);
            assertEquals("alice", localProfile.username);
            assertEquals("", localProfile.email);
            assertEquals("", localProfile.avatarUri);
        }
    }

    @Test
    public void saveAvatarUri_delegatesToUserPreferences() {
        try (MockedStatic<UserPreferences> prefs = org.mockito.Mockito.mockStatic(UserPreferences.class)) {
            repository.saveAvatarUri("content://avatar");

            prefs.verify(() -> UserPreferences.setAvatarUri(context, "content://avatar"));
        }
    }

    @Test
    public void syncProfileMetadata_notLoggedIn_callsSuccessWithoutApiCall() {
        boolean[] success = {false};
        boolean[] failure = {false};

        try (MockedStatic<UserPreferences> prefs = org.mockito.Mockito.mockStatic(UserPreferences.class)) {
            prefs.when(() -> UserPreferences.isLoggedIn(context)).thenReturn(false);

            repository.syncProfileMetadata(newCallback(success, failure));

            assertTrue(success[0]);
            assertFalse(failure[0]);
            verify(restApiService, never()).getMyProfileMetadata();
        }
    }

    @Test
    public void syncProfileMetadata_successResponse_persistsAndCallsSuccess() {
        boolean[] success = {false};
        boolean[] failure = {false};

        ProfileMetadataResponse body = new ProfileMetadataResponse();
        body.userId = "id-123";
        body.displayName = "  Alice  ";
        body.email = " alice@bif.com ";

        try (MockedStatic<UserPreferences> prefs = org.mockito.Mockito.mockStatic(UserPreferences.class)) {
            prefs.when(() -> UserPreferences.isLoggedIn(context)).thenReturn(true);
            when(restApiService.getMyProfileMetadata()).thenReturn(profileMetadataCall);

            ArgumentCaptor<Callback<ProfileMetadataResponse>> callbackCaptor = ArgumentCaptor.forClass(Callback.class);
            org.mockito.Mockito.doNothing().when(profileMetadataCall).enqueue(callbackCaptor.capture());

            repository.syncProfileMetadata(newCallback(success, failure));
            callbackCaptor.getValue().onResponse(profileMetadataCall, Response.success(body));

            prefs.verify(() -> UserPreferences.saveUserProfile(context, "id-123", "Alice", "alice@bif.com"));
        }
    }

    @Test
    public void syncProfileMetadata_successWithEmptyProfile_doesNotPersist() {
        boolean[] success = {false};
        boolean[] failure = {false};

        ProfileMetadataResponse body = new ProfileMetadataResponse();
        body.displayName = "   ";
        body.email = null;

        try (MockedStatic<UserPreferences> prefs = org.mockito.Mockito.mockStatic(UserPreferences.class)) {
            prefs.when(() -> UserPreferences.isLoggedIn(context)).thenReturn(true);
            when(restApiService.getMyProfileMetadata()).thenReturn(profileMetadataCall);

            ArgumentCaptor<Callback<ProfileMetadataResponse>> callbackCaptor = ArgumentCaptor.forClass(Callback.class);
            org.mockito.Mockito.doNothing().when(profileMetadataCall).enqueue(callbackCaptor.capture());

            repository.syncProfileMetadata(newCallback(success, failure));
            callbackCaptor.getValue().onResponse(profileMetadataCall, Response.success(body));

            assertTrue(success[0]);
            assertFalse(failure[0]);
            prefs.verify(() -> UserPreferences.saveUserProfile(any(), anyString(), anyString(), anyString()), never());
        }
    }

    @Test
    public void syncProfileMetadata_unsuccessfulResponse_callsSuccessWithoutPersist() {
        boolean[] success = {false};
        boolean[] failure = {false};

        @SuppressWarnings("unchecked")
        Response<ProfileMetadataResponse> failedResponse = mock(Response.class);

        try (MockedStatic<UserPreferences> prefs = org.mockito.Mockito.mockStatic(UserPreferences.class)) {
            prefs.when(() -> UserPreferences.isLoggedIn(context)).thenReturn(true);
            when(restApiService.getMyProfileMetadata()).thenReturn(profileMetadataCall);
            when(failedResponse.isSuccessful()).thenReturn(false);

            ArgumentCaptor<Callback<ProfileMetadataResponse>> callbackCaptor = ArgumentCaptor.forClass(Callback.class);
            org.mockito.Mockito.doNothing().when(profileMetadataCall).enqueue(callbackCaptor.capture());

            repository.syncProfileMetadata(newCallback(success, failure));
            callbackCaptor.getValue().onResponse(profileMetadataCall, failedResponse);

            assertTrue(success[0]);
            assertFalse(failure[0]);
            prefs.verify(() -> UserPreferences.saveUserProfile(any(), anyString(), anyString(), anyString()), never());
        }
    }

    @Test
    public void syncProfileMetadata_networkFailure_callsFailure() {
        boolean[] success = {false};
        boolean[] failure = {false};

        try (MockedStatic<UserPreferences> prefs = org.mockito.Mockito.mockStatic(UserPreferences.class)) {
            prefs.when(() -> UserPreferences.isLoggedIn(context)).thenReturn(true);
            when(restApiService.getMyProfileMetadata()).thenReturn(profileMetadataCall);

            ArgumentCaptor<Callback<ProfileMetadataResponse>> callbackCaptor = ArgumentCaptor.forClass(Callback.class);
            org.mockito.Mockito.doNothing().when(profileMetadataCall).enqueue(callbackCaptor.capture());

            repository.syncProfileMetadata(newCallback(success, failure));
            callbackCaptor.getValue().onFailure(profileMetadataCall, new RuntimeException("network"));

            assertFalse(success[0]);
            assertTrue(failure[0]);
        }
    }

    @Test
    public void updateProfile_successResponse_persistsAndCallsSuccess() {
        boolean[] success = {false};
        boolean[] failure = {false};

        ProfileMetadataResponse body = new ProfileMetadataResponse();
        body.userId = "Bob";
        body.displayName = " Bob ";
        body.email = " bob@bif.com ";

        try (MockedStatic<UserPreferences> prefs = org.mockito.Mockito.mockStatic(UserPreferences.class)) {
            when(restApiService.updateMyProfile(any(UpdateMyProfileRequest.class))).thenReturn(updateProfileCall);

            ArgumentCaptor<UpdateMyProfileRequest> requestCaptor = ArgumentCaptor.forClass(UpdateMyProfileRequest.class);
            ArgumentCaptor<Callback<ProfileMetadataResponse>> callbackCaptor = ArgumentCaptor.forClass(Callback.class);

            org.mockito.Mockito.doNothing().when(updateProfileCall).enqueue(callbackCaptor.capture());

            repository.updateProfile("Bob", newCallback(success, failure));
            verify(restApiService).updateMyProfile(requestCaptor.capture());
            callbackCaptor.getValue().onResponse(updateProfileCall, Response.success(body));

            assertEquals("Bob", requestCaptor.getValue().name);
            prefs.verify(() -> UserPreferences.saveUserProfile(context, "Bob", "Bob", "bob@bif.com"));
        }
    }

    @Test
    public void updateProfile_unsuccessfulResponse_callsFailure() {
        boolean[] success = {false};
        boolean[] failure = {false};

        @SuppressWarnings("unchecked")
        Response<ProfileMetadataResponse> failedResponse = mock(Response.class);

        when(restApiService.updateMyProfile(any(UpdateMyProfileRequest.class))).thenReturn(updateProfileCall);
        when(failedResponse.isSuccessful()).thenReturn(false);

        ArgumentCaptor<Callback<ProfileMetadataResponse>> callbackCaptor = ArgumentCaptor.forClass(Callback.class);
        org.mockito.Mockito.doNothing().when(updateProfileCall).enqueue(callbackCaptor.capture());

        repository.updateProfile("Bob", newCallback(success, failure));
        callbackCaptor.getValue().onResponse(updateProfileCall, failedResponse);

        assertFalse(success[0]);
        assertTrue(failure[0]);
    }

    @Test
    public void updateProfile_networkFailure_callsFailure() {
        boolean[] success = {false};
        boolean[] failure = {false};

        when(restApiService.updateMyProfile(any(UpdateMyProfileRequest.class))).thenReturn(updateProfileCall);

        ArgumentCaptor<Callback<ProfileMetadataResponse>> callbackCaptor = ArgumentCaptor.forClass(Callback.class);
        org.mockito.Mockito.doNothing().when(updateProfileCall).enqueue(callbackCaptor.capture());

        repository.updateProfile("Bob", newCallback(success, failure));
        callbackCaptor.getValue().onFailure(updateProfileCall, new RuntimeException("network"));

        assertFalse(success[0]);
        assertTrue(failure[0]);
    }

    private ProfileRepository.ProfileCallback newCallback(boolean[] success, boolean[] failure) {
        return new ProfileRepository.ProfileCallback() {
            @Override
            public void onSuccess() {
                success[0] = true;
            }

            @Override
            public void onFailure() {
                failure[0] = true;
            }
        };
    }
}