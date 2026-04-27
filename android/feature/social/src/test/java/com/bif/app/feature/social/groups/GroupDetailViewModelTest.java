package com.bif.app.feature.social.groups;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.Group;
import com.bif.app.domain.repository.IFriendshipRepository;
import com.bif.app.domain.repository.IGroupRepository;
import com.bif.app.feature.social.groups.GroupDetailViewModel;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GroupDetailViewModelTest {
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private IGroupRepository mockGroupRepository;

    @Mock
    private IFriendshipRepository mockFriendshipRepository;

    private GroupDetailViewModel viewModel;
    private MutableLiveData<Group> groupLiveData;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        groupLiveData = new MutableLiveData<>();
        // Return groupLiveData for any server group id
        when(mockGroupRepository.getGroupByServerId(anyString())).thenReturn(groupLiveData);
        when(mockFriendshipRepository.getFriends()).thenReturn(new MutableLiveData<>());
        viewModel = new GroupDetailViewModel(mockGroupRepository, mockFriendshipRepository);
    }

    /**
     * Helper: observe the group LiveData to activate Transformations.switchMap,
     * load the group, and set a value on the backing MutableLiveData.
     */
    private Group loadAndObserveGroup(Group group) {
        // Must observe for switchMap to activate
        viewModel.getGroup().observeForever(g -> {});
        viewModel.loadGroup(group.getId());
        groupLiveData.setValue(group);
        return viewModel.getGroup().getValue();
    }

    // ==================== loadGroup Tests ====================

    @Test
    public void loadGroup_ValidId_CallsRepositoryGetGroupById() {
        // Must observe for switchMap
        viewModel.getGroup().observeForever(g -> {});

        // Act
        viewModel.loadGroup(1);

        // Assert
        verify(mockGroupRepository).getGroupByServerId("1");
    }

    @Test
    public void getGroup_AfterLoad_ReturnsGroupLiveData() {
        // Arrange
        Group expectedGroup = new Group(1, "Team", "T", 0xFF03DAC5, new ArrayList<>(), true);

        // Act
        Group result = loadAndObserveGroup(expectedGroup);

        // Assert
        assertNotNull(result);
        assertEquals("Team", result.getName());
        assertEquals(1, result.getId());
    }

    @Test
    public void getGroup_BeforeLoad_ReturnsNull() {
        // Act — observe but don't load
        viewModel.getGroup().observeForever(g -> {});
        Group result = viewModel.getGroup().getValue();

        // Assert
        assertNull(result);
    }

    // ==================== updateGroupName Tests ====================

    @Test
    public void updateGroupName_ValidName_CallsRepositoryUpdateGroup() {
        // Arrange
        Group existingGroup = new Group(1, "OldName", "O", 0xFF03DAC5, new ArrayList<>(), true);
        loadAndObserveGroup(existingGroup);

        // Act
        viewModel.updateGroupName("NewName");

        // Assert
        ArgumentCaptor<Group> captor = ArgumentCaptor.forClass(Group.class);
        verify(mockGroupRepository).updateGroup(captor.capture());

        Group updatedGroup = captor.getValue();
        assertEquals("NewName", updatedGroup.getName());
        assertEquals("N", updatedGroup.getAvatarLetter());
        assertEquals(1, updatedGroup.getId());
        assertEquals(0xFF03DAC5, updatedGroup.getAvatarColor());
        assertTrue(updatedGroup.isOwner());
    }

    @Test
    public void updateGroupName_NoGroupLoaded_DoesNotCallRepository() {
        // Act — no group loaded
        viewModel.getGroup().observeForever(g -> {});
        viewModel.updateGroupName("SomeName");

        // Assert
        verify(mockGroupRepository, never()).updateGroup(any());
    }

    // ==================== removeMember Tests ====================

    @Test
    public void removeMember_ValidMember_CallsRepositoryRemoveMemberByServerId() {
        // Arrange
        List<Friend> members = Arrays.asList(
                new Friend(10, "An", "A", 111, true),
                new Friend(20, "Bình", "B", 222, false)
        );
        Group existingGroup = new Group(1, "Team", "T", 0xFF03DAC5, members, true);
        loadAndObserveGroup(existingGroup);

        Friend memberToRemove = members.get(0);

        // Act
        viewModel.removeMember(memberToRemove);

        // Assert
        verify(mockGroupRepository).removeMemberByServerId("1", 10);
    }

    @Test
    public void removeMember_NoGroupLoaded_DoesNotCallRepository() {
        // Arrange
        Friend member = new Friend(10, "An", "A", 111, true);

        // Act — no group loaded
        viewModel.getGroup().observeForever(g -> {});
        viewModel.removeMember(member);

        // Assert
        verify(mockGroupRepository, never()).removeMemberByServerId(anyString(), anyInt());
    }

    // ==================== disbandGroup Tests ====================

    @Test
    public void disbandGroup_ValidGroup_CallsRepositoryDisbandGroup() {
        // Arrange
        Group existingGroup = new Group(1, "MyGroup", "M", 0xFF03DAC5, new ArrayList<>(), true);
        loadAndObserveGroup(existingGroup);

        // Act
        viewModel.disbandGroup();

        // Assert
        verify(mockGroupRepository).disbandGroup(existingGroup);
    }

    @Test
    public void disbandGroup_NoGroupLoaded_DoesNotCallRepository() {
        // Act — no group loaded
        viewModel.getGroup().observeForever(g -> {});
        viewModel.disbandGroup();

        // Assert
        verify(mockGroupRepository, never()).disbandGroup(any());
    }
}
