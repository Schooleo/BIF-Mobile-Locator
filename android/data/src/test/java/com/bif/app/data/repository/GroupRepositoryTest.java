package com.bif.app.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.bif.app.data.LiveDataTestUtil;
import com.bif.app.data.mapper.GroupMapper;
import com.bif.app.data.source.local.GroupDao;
import com.bif.app.data.source.local.entity.GroupEntity;
import com.bif.app.data.source.local.entity.GroupWithFriends;
import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.Group;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GroupRepositoryTest {
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private GroupDao mockDao;

    @Mock
    private GroupMapper mockMapper;

    private GroupRepository repository;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        // Stub getAllGroupsWithFriends to return empty LiveData so constructor works
        MutableLiveData<List<GroupWithFriends>> emptyLiveData = new MutableLiveData<>(new ArrayList<>());
        when(mockDao.getAllGroupsWithFriends()).thenReturn(emptyLiveData);
        repository = new GroupRepository(mockDao, mockMapper);
    }

    @Test
    public void getGroups_DaoReturnsData_ReturnsMappedDomains() throws InterruptedException {
        // Arrange
        List<GroupWithFriends> mockEntities = new ArrayList<>();
        GroupWithFriends gwf = new GroupWithFriends();
        gwf.group = new GroupEntity(1, "Team", "T", 0xFF03DAC5, true);
        gwf.friends = new ArrayList<>();
        mockEntities.add(gwf);

        MutableLiveData<List<GroupWithFriends>> fakeLiveData = new MutableLiveData<>();
        fakeLiveData.setValue(mockEntities);
        when(mockDao.getAllGroupsWithFriends()).thenReturn(fakeLiveData);

        List<Group> expectedGroups = List.of(
                new Group(1, "Team", "T", 0xFF03DAC5, new ArrayList<>(), true)
        );
        when(mockMapper.mapToDomainList(mockEntities)).thenReturn(expectedGroups);

        // Re-create repository to pick up new mock behavior
        repository = new GroupRepository(mockDao, mockMapper);

        // Act
        List<Group> result = LiveDataTestUtil.getOrAwaitValue(repository.getGroups());

        // Assert
        assertEquals(1, result.size());
        assertEquals("Team", result.get(0).getName());
    }

    @Test
    public void createGroup_ValidData_CallsDaoInsertOnBackgroundThread() {
        // Arrange
        when(mockDao.insertGroup(any(GroupEntity.class))).thenReturn(1L);

        // Act
        repository.createGroup("My Group", new ArrayList<>());

        // Assert — use timeout because Repository runs on ExecutorService
        verify(mockDao, timeout(1000)).insertGroup(any(GroupEntity.class));
    }

    @Test
    public void createGroup_WithFriends_CallsInsertCrossRefs() {
        // Arrange
        when(mockDao.insertGroup(any(GroupEntity.class))).thenReturn(1L);
        List<Friend> friends = Arrays.asList(
                new Friend(10, "An", "A", 111, true),
                new Friend(20, "Bình", "B", 222, false)
        );

        // Act
        repository.createGroup("Team", friends);

        // Assert
        verify(mockDao, timeout(1000)).insertGroup(any(GroupEntity.class));
        verify(mockDao, timeout(1000)).insertGroupFriendCrossRefs(anyList());
    }

    @Test
    public void createGroup_EmptyFriends_DoesNotInsertCrossRefs() {
        // Arrange
        when(mockDao.insertGroup(any(GroupEntity.class))).thenReturn(1L);

        // Act
        repository.createGroup("Solo Group", new ArrayList<>());

        // Assert
        verify(mockDao, timeout(1000)).insertGroup(any(GroupEntity.class));
        verify(mockDao, timeout(1000).times(0)).insertGroupFriendCrossRefs(anyList());
    }

    @Test
    public void leaveGroup_ValidGroup_CallsDaoDeleteById() {
        // Arrange
        Group group = new Group(5, "Others", "O", 0, new ArrayList<>(), false);

        // Act
        repository.leaveGroup(group);

        // Assert
        verify(mockDao, timeout(1000)).deleteGroupById(5);
    }

    @Test
    public void disbandGroup_ValidGroup_CallsDaoDeleteById() {
        // Arrange
        Group group = new Group(7, "Mine", "M", 0, new ArrayList<>(), true);

        // Act
        repository.disbandGroup(group);

        // Assert
        verify(mockDao, timeout(1000)).deleteGroupById(7);
    }

    // ==================== New Method Tests ====================

    @Test
    public void getGroupById_DaoReturnsData_ReturnsMappedDomain() throws InterruptedException {
        // Arrange
        GroupWithFriends gwf = new GroupWithFriends();
        gwf.group = new GroupEntity(1, "Team", "T", 0xFF03DAC5, true);
        gwf.friends = new ArrayList<>();

        MutableLiveData<GroupWithFriends> fakeLiveData = new MutableLiveData<>();
        fakeLiveData.setValue(gwf);
        when(mockDao.getGroupWithFriendsById(1)).thenReturn(fakeLiveData);

        Group expectedGroup = new Group(1, "Team", "T", 0xFF03DAC5, new ArrayList<>(), true);
        when(mockMapper.mapToDomain(gwf)).thenReturn(expectedGroup);

        // Re-create repository to pick up new mock behavior
        repository = new GroupRepository(mockDao, mockMapper);

        // Act
        Group result = LiveDataTestUtil.getOrAwaitValue(repository.getGroupById(1));

        // Assert
        assertNotNull(result);
        assertEquals("Team", result.getName());
        assertEquals(1, result.getId());
    }

    @Test
    public void updateGroup_ValidGroup_CallsDaoUpdateOnBackgroundThread() {
        // Arrange
        Group group = new Group(3, "Updated", "U", 0xFF00FF, new ArrayList<>(), true);
        GroupEntity entity = new GroupEntity(3, "Updated", "U", 0xFF00FF, true);
        when(mockMapper.mapToEntity(group)).thenReturn(entity);

        // Act
        repository.updateGroup(group);

        // Assert — use timeout because Repository runs on ExecutorService
        verify(mockDao, timeout(1000)).updateGroup(entity);
    }

    @Test
    public void removeMember_ValidIds_CallsDaoDeleteCrossRefOnBackgroundThread() {
        // Act
        repository.removeMember(5, 10);

        // Assert
        verify(mockDao, timeout(1000)).deleteGroupFriendCrossRef(5, 10);
    }
}
