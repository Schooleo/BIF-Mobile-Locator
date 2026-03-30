package com.bif.app.feature.social;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.Group;
import com.bif.app.domain.repository.IFriendshipRepository;
import com.bif.app.domain.repository.IGroupRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SocialViewModelTest {
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private IFriendshipRepository mockFriendshipRepository;

    @Mock
    private IGroupRepository mockGroupRepository;

    private SocialViewModel viewModel;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mockFriendshipRepository.getFriends()).thenReturn(new MutableLiveData<>());
        when(mockFriendshipRepository.getPendingRequests()).thenReturn(new MutableLiveData<>());
        when(mockGroupRepository.getGroups()).thenReturn(new MutableLiveData<>());
        viewModel = new SocialViewModel(mockFriendshipRepository, mockGroupRepository);
    }

    // ==================== Friend Tests ====================

    @Test
    public void init_CallsRepositoryGetFriends() {
        verify(mockFriendshipRepository).getFriends();
    }

    @Test
    public void addFriend_ValidData_CallsSendFriendRequest() {
        when(mockFriendshipRepository.resolveUserId("cuong-id")).thenReturn("cuong-id");

        // Act
        viewModel.addFriend("cuong-id");

        // Assert
        verify(mockFriendshipRepository, timeout(1000)).resolveUserId("cuong-id");
        verify(mockFriendshipRepository, timeout(1000)).sendFriendRequest("cuong-id");
        verify(mockFriendshipRepository, timeout(1000)).refreshPendingRequests();
    }

    @Test
    public void addFriend_UserNotFound_DoesNotCallSendFriendRequest() {
        when(mockFriendshipRepository.resolveUserId("missing-user")).thenReturn(null);

        viewModel.addFriend("missing-user");

        verify(mockFriendshipRepository, timeout(1000)).resolveUserId("missing-user");
        org.mockito.Mockito.verify(mockFriendshipRepository, after(300).never())
                .sendFriendRequest(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    public void addFriend_SelfRequest_DoesNotCallSendFriendRequest() {
        when(mockFriendshipRepository.resolveUserId("self-user")).thenReturn("self-user");
        org.mockito.Mockito.doThrow(new IllegalStateException("SELF_REQUEST"))
                .when(mockFriendshipRepository).sendFriendRequest("self-user");

        viewModel.addFriend("self-user");

        verify(mockFriendshipRepository, timeout(1000)).resolveUserId("self-user");
        verify(mockFriendshipRepository, timeout(1000)).sendFriendRequest("self-user");
        org.mockito.Mockito.verify(mockFriendshipRepository, after(300).never()).refreshPendingRequests();
    }

    @Test
    public void addFriend_ExistingPending_DoesNotRefreshPendingRequests() {
        when(mockFriendshipRepository.resolveUserId("pending-user")).thenReturn("pending-user");
        org.mockito.Mockito.doThrow(new IllegalStateException("REQUEST_PENDING"))
                .when(mockFriendshipRepository).sendFriendRequest("pending-user");

        viewModel.addFriend("pending-user");

        verify(mockFriendshipRepository, timeout(1000)).sendFriendRequest("pending-user");
        org.mockito.Mockito.verify(mockFriendshipRepository, after(300).never()).refreshPendingRequests();
    }

    @Test
    public void addFriend_AlreadyFriends_DoesNotRefreshPendingRequests() {
        when(mockFriendshipRepository.resolveUserId("accepted-user")).thenReturn("accepted-user");
        org.mockito.Mockito.doThrow(new IllegalStateException("ALREADY_FRIENDS"))
                .when(mockFriendshipRepository).sendFriendRequest("accepted-user");

        viewModel.addFriend("accepted-user");

        verify(mockFriendshipRepository, timeout(1000)).sendFriendRequest("accepted-user");
        org.mockito.Mockito.verify(mockFriendshipRepository, after(300).never()).refreshPendingRequests();
    }

    @Test
    public void addFriend_RejectedBefore_CallsSendFriendRequestAgain() {
        when(mockFriendshipRepository.resolveUserId("rejected-user")).thenReturn("rejected-user");

        viewModel.addFriend("rejected-user");

        verify(mockFriendshipRepository, timeout(1000)).sendFriendRequest("rejected-user");
        verify(mockFriendshipRepository, timeout(1000)).refreshPendingRequests();
    }

    @Test
    public void deleteFriend_ValidFriend_CallsUnfriendApi() {
        Friend friendToDelete = new Friend(1, "user-huy", "Huy", "H", 0x111111, false);

        viewModel.deleteFriend(friendToDelete);

        verify(mockFriendshipRepository, timeout(1000)).unfriend("user-huy");
        verify(mockFriendshipRepository, timeout(1000)).refreshFriends();
        verify(mockFriendshipRepository, timeout(1000)).refreshPendingRequests();
    }

    // ==================== Group Tests ====================

    @Test
    public void init_CallsRepositoryGetGroups() {
        verify(mockGroupRepository).getGroups();
    }

    @Test
    public void createGroup_ValidData_CallsRepositoryCreateGroup() {
        // Arrange
        List<Friend> members = Arrays.asList(
                new Friend(1, "An", "A", 111, true),
                new Friend(2, "Bình", "B", 222, false)
        );

        // Act
        viewModel.createGroup("DevTeam", members);

        // Assert
        verify(mockGroupRepository, timeout(1000)).createGroup(eq("DevTeam"), eq(members));
        verify(mockGroupRepository, timeout(1000)).refreshGroups();
    }

    @Test
    public void handleGroupAction_OwnerGroup_CallsDisbandGroup() {
        // Arrange
        Group ownedGroup = new Group(1, "My Group", "M", 0xFF00FF, new ArrayList<>(), true);

        // Act
        viewModel.handleGroupAction(ownedGroup);

        // Assert
        verify(mockGroupRepository, timeout(1000)).disbandGroup(ownedGroup);
        verify(mockGroupRepository, timeout(1000)).refreshGroups();
    }

    @Test
    public void handleGroupAction_NonOwnerGroup_CallsLeaveGroup() {
        // Arrange
        Group otherGroup = new Group(2, "Their Group", "T", 0x00FF00, new ArrayList<>(), false);

        // Act
        viewModel.handleGroupAction(otherGroup);

        // Assert
        verify(mockGroupRepository, timeout(1000)).leaveGroup(otherGroup);
        verify(mockGroupRepository, timeout(1000)).refreshGroups();
    }
}