package com.bif.app.data.repository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.UserApiModel;
import com.bif.app.core.network.dto.auth.AuthStateResponse;
import com.bif.app.core.network.dto.friendship.FriendshipApiModel;
import com.bif.app.data.sync.SyncManager;
import com.bif.app.data.source.local.FriendDao;
import com.bif.app.data.source.local.FriendshipDao;
import com.bif.app.data.source.local.entity.FriendshipEntity;
import com.bif.app.data.source.local.entity.FriendshipStatus;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Response;

public class FriendshipRepositoryTest {

    @Mock
    private RestApiService mockRestApiService;

    @Mock
    private FriendshipDao mockFriendshipDao;

    @Mock
    private FriendDao mockFriendDao;

    @Mock
    private SyncManager mockSyncManager;

    private FriendshipRepository repository;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        repository = new FriendshipRepository(mockRestApiService,
                mockFriendshipDao, mockFriendDao, mockSyncManager);

        stubAuthUser();
        stubFriendshipRefreshEndpoints();
    }

    @Test
    public void sendFriendRequest_whenOffline_enqueuesSyncChange() throws Exception {
        when(mockFriendshipDao.reservePendingIfAbsent(eq("user-1"),
                eq("user-2"), anyLong())).thenReturn(true);

        @SuppressWarnings("unchecked")
        Call<FriendshipApiModel> sendCall = Mockito.mock(Call.class);
        when(sendCall.execute()).thenThrow(new IOException("offline"));
        when(mockRestApiService.sendFriendRequest(any())).thenReturn(sendCall);

        repository.sendFriendRequest("user-2");

        verify(mockSyncManager).enqueueChange(eq("friendship"), isNull(),
                eq("SEND_REQUEST"), anyString(), any());
    }

    @Test
    public void acceptFriendRequest_whenOffline_updatesLocalAndEnqueues() throws Exception {
        FriendshipEntity existing = new FriendshipEntity();
        existing.id = 1;
        existing.serverId = "friendship-1";
        existing.requesterId = "user-2";
        existing.receiverId = "user-1";
        existing.status = FriendshipStatus.PENDING;
        when(mockFriendshipDao.getById(1)).thenReturn(existing);

        @SuppressWarnings("unchecked")
        Call<FriendshipApiModel> acceptCall = Mockito.mock(Call.class);
        when(acceptCall.execute()).thenThrow(new IOException("offline"));
        when(mockRestApiService.acceptFriendRequest("friendship-1"))
                .thenReturn(acceptCall);

        repository.acceptFriendRequest(1);

        org.junit.Assert.assertEquals(FriendshipStatus.ACCEPTED,
                existing.status);
        verify(mockFriendshipDao).update(existing);
        verify(mockSyncManager).enqueueChange(eq("friendship"),
                eq("friendship-1"), eq("ACCEPT_REQUEST"), anyString(), any());
    }

    private void stubAuthUser() throws Exception {
        AuthStateResponse auth = new AuthStateResponse();
        auth.authenticated = true;
        auth.userId = "user-1";

        @SuppressWarnings("unchecked")
        Call<AuthStateResponse> authCall = Mockito.mock(Call.class);
        when(authCall.execute()).thenReturn(Response.success(auth));
        when(mockRestApiService.getAuthState()).thenReturn(authCall);
    }

    private void stubFriendshipRefreshEndpoints() throws Exception {
        @SuppressWarnings("unchecked")
        Call<List<UserApiModel>> friendsCall = Mockito.mock(Call.class);
        when(friendsCall.execute()).thenReturn(
                Response.success(Collections.emptyList()));
        when(mockRestApiService.getFriends()).thenReturn(friendsCall);

        @SuppressWarnings("unchecked")
        Call<List<FriendshipApiModel>> incomingCall = Mockito.mock(Call.class);
        when(incomingCall.execute()).thenReturn(
                Response.success(Collections.emptyList()));
        when(mockRestApiService.getIncomingFriendRequests())
                .thenReturn(incomingCall);

        @SuppressWarnings("unchecked")
        Call<List<FriendshipApiModel>> outgoingCall = Mockito.mock(Call.class);
        when(outgoingCall.execute()).thenReturn(
                Response.success(Collections.emptyList()));
        when(mockRestApiService.getOutgoingFriendRequests())
                .thenReturn(outgoingCall);

        when(mockFriendDao.getAllFriendsSync()).thenReturn(Collections.emptyList());
    }
}
