package com.bif.app.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.group.GroupApiModel;
import com.bif.app.core.network.dto.user.UserApiModel;
import com.bif.app.core.network.dto.auth.AuthStateResponse;
import com.bif.app.data.sync.core.NetworkMonitor;
import com.bif.app.data.sync.core.SyncManager;
import com.bif.app.data.source.local.dao.FriendDao;
import com.bif.app.data.source.local.dao.GroupDao;
import com.bif.app.data.source.local.entity.GroupEntity;
import com.bif.app.domain.model.Group;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import retrofit2.Call;
import retrofit2.Response;

public class GroupRepositoryApiFallbackTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private RestApiService mockRestApiService;

    @Mock
    private GroupDao mockGroupDao;

    @Mock
    private FriendDao mockFriendDao;

    @Mock
    private SyncManager mockSyncManager;

    @Mock
    private NetworkMonitor mockNetworkMonitor;

    private GroupRepository repository;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        repository = new GroupRepository(mockRestApiService, mockGroupDao,
            mockSyncManager, mockNetworkMonitor, mockFriendDao);
        when(mockNetworkMonitor.isOnline()).thenReturn(true);
        stubAuthenticatedUser();
    }

    @Test
    public void getGroups_whenRemoteFails_emitsCachedGroups() throws Exception {
        GroupEntity cached = new GroupEntity(
                101,
                "group-1",
                "Explorers",
                "E",
                123,
                true,
                "user-1",
                "[\"user-1\",\"user-2\"]",
                "{\"user-1\":\"ADMIN\",\"user-2\":\"MEMBER\"}",
                0L,
                false,
                System.currentTimeMillis()
        );
        when(mockGroupDao.getAllGroupsSync()).thenReturn(Collections.singletonList(cached));

        @SuppressWarnings("unchecked")
        Call<List<UserApiModel>> usersCall = Mockito.mock(Call.class);
        when(usersCall.execute()).thenThrow(new IOException("offline users"));
        when(mockRestApiService.getUsers()).thenReturn(usersCall);

        @SuppressWarnings("unchecked")
        Call<List<GroupApiModel>> groupsCall = Mockito.mock(Call.class);
        when(groupsCall.execute()).thenThrow(new IOException("offline groups"));
        when(mockRestApiService.getGroupsByUser("user-1")).thenReturn(groupsCall);

        LiveData<List<Group>> groupsLiveData = repository.getGroups();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<Group>> emitted = new AtomicReference<>();
        @SuppressWarnings("unchecked")
        final Observer<List<Group>>[] holder = new Observer[1];
        holder[0] = groups -> {
            if (groups != null && !groups.isEmpty()) {
                emitted.set(groups);
                groupsLiveData.removeObserver(holder[0]);
                latch.countDown();
            }
        };
        groupsLiveData.observeForever(holder[0]);

        assertTrue("Expected non-empty cached groups emission", latch.await(2, TimeUnit.SECONDS));

        List<Group> result = emitted.get();
        assertEquals(1, result.size());
        assertEquals("group-1", result.get(0).getServerId());
        assertEquals("Explorers", result.get(0).getName());
        assertEquals(2, result.get(0).getMembers().size());

        verify(mockGroupDao, timeout(1000).atLeastOnce()).getAllGroupsSync();
        verify(mockRestApiService, timeout(1000).atLeastOnce()).getGroupsByUser("user-1");
    }

    @Test
    public void getGroups_whenAuthUnavailable_emitsCachedGroups() throws Exception {
        GroupEntity cached = new GroupEntity(
                201,
                "group-2",
                "Offline Group",
                "O",
                456,
                false,
                "user-2",
                "[\"user-1\",\"user-2\"]",
                "{\"user-2\":\"ADMIN\",\"user-1\":\"MEMBER\"}",
                0L,
                false,
                System.currentTimeMillis()
        );
        when(mockGroupDao.getAllGroupsSync()).thenReturn(Collections.singletonList(cached));

        AuthStateResponse auth = new AuthStateResponse();
        auth.authenticated = false;

        @SuppressWarnings("unchecked")
        Call<AuthStateResponse> authCall = Mockito.mock(Call.class);
        when(authCall.execute()).thenReturn(Response.success(auth));
        when(mockRestApiService.getAuthState()).thenReturn(authCall);

        LiveData<List<Group>> groupsLiveData = repository.getGroups();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<Group>> emitted = new AtomicReference<>();
        @SuppressWarnings("unchecked")
        final Observer<List<Group>>[] holder = new Observer[1];
        holder[0] = groups -> {
            if (groups != null && !groups.isEmpty()) {
                emitted.set(groups);
                groupsLiveData.removeObserver(holder[0]);
                latch.countDown();
            }
        };
        groupsLiveData.observeForever(holder[0]);

        assertTrue("Expected cached groups emission without auth",
                latch.await(2, TimeUnit.SECONDS));

        List<Group> result = emitted.get();
        assertEquals(1, result.size());
        assertEquals("group-2", result.get(0).getServerId());
        assertEquals("Offline Group", result.get(0).getName());
        verify(mockGroupDao, timeout(1000).atLeastOnce()).getAllGroupsSync();
    }

    private void stubAuthenticatedUser() throws Exception {
        AuthStateResponse auth = new AuthStateResponse();
        auth.authenticated = true;
        auth.userId = "user-1";

        @SuppressWarnings("unchecked")
        Call<AuthStateResponse> authCall = Mockito.mock(Call.class);
        when(authCall.execute()).thenReturn(Response.success(auth));
        when(mockRestApiService.getAuthState()).thenReturn(authCall);
    }
}


