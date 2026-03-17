package com.bif.app.feature.social;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.Group;
import com.bif.app.domain.repository.IFriendRepository;
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
    private IFriendRepository mockFriendRepository;

    @Mock
    private IGroupRepository mockGroupRepository;

    private SocialViewModel viewModel;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mockFriendRepository.getFriends()).thenReturn(new MutableLiveData<>());
        when(mockGroupRepository.getGroups()).thenReturn(new MutableLiveData<>());
        viewModel = new SocialViewModel(mockFriendRepository, mockGroupRepository);
    }

    // ==================== Friend Tests ====================

    @Test
    public void init_CallsRepositoryGetFriends() {
        verify(mockFriendRepository).getFriends();
    }

    @Test
    public void addFriend_ValidData_CallsRepositoryWithCorrectModel() {
        // Act
        viewModel.addFriend("Cường", "C", 0xFF00FF);

        // Assert
        ArgumentCaptor<Friend> captor = ArgumentCaptor.forClass(Friend.class);
        verify(mockFriendRepository).addFriend(captor.capture());

        Friend capturedFriend = captor.getValue();
        assertEquals("Cường", capturedFriend.getName());
        assertEquals("C", capturedFriend.getAvatarLetter());
        assertEquals(0xFF00FF, capturedFriend.getAvatarColor());
        assertTrue(capturedFriend.isOnline());
    }

    @Test
    public void deleteFriend_ValidFriend_CallsRepositoryDelete() {
        Friend friendToDelete = new Friend(1, "Huy", "H", 0x111111, false);

        viewModel.deleteFriend(friendToDelete);

        verify(mockFriendRepository).deleteFriend(friendToDelete);
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
        verify(mockGroupRepository).createGroup(eq("DevTeam"), eq(members));
    }

    @Test
    public void handleGroupAction_OwnerGroup_CallsDisbandGroup() {
        // Arrange
        Group ownedGroup = new Group(1, "My Group", "M", 0xFF00FF, new ArrayList<>(), true);

        // Act
        viewModel.handleGroupAction(ownedGroup);

        // Assert
        verify(mockGroupRepository).disbandGroup(ownedGroup);
    }

    @Test
    public void handleGroupAction_NonOwnerGroup_CallsLeaveGroup() {
        // Arrange
        Group otherGroup = new Group(2, "Their Group", "T", 0x00FF00, new ArrayList<>(), false);

        // Act
        viewModel.handleGroupAction(otherGroup);

        // Assert
        verify(mockGroupRepository).leaveGroup(otherGroup);
    }
}