package com.bif.app.data.source.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.bif.app.data.LiveDataTestUtil;
import com.bif.app.data.source.local.entity.FriendEntity;
import com.bif.app.data.source.local.entity.GroupEntity;
import com.bif.app.data.source.local.entity.GroupFriendCrossRef;
import com.bif.app.data.source.local.entity.GroupWithFriends;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class GroupDaoInstrumentedTest {
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppDatabase database;
    private GroupDao groupDao;
    private FriendDao friendDao;

    @Before
    public void setup() {
        database = Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase.class
        ).allowMainThreadQueries().build();
        groupDao = database.groupDao();
        friendDao = database.friendDao();
    }

    @After
    public void teardown() {
        database.close();
    }

    @Test
    public void getAllGroupsWithFriends_EmptyDatabase_ReturnsEmptyList() throws InterruptedException {
        List<GroupWithFriends> list = LiveDataTestUtil.getOrAwaitValue(groupDao.getAllGroupsWithFriends());
        assertTrue(list.isEmpty());
    }

    @Test
    public void insertGroup_ValidData_SavesToDatabase() throws InterruptedException {
        GroupEntity group = new GroupEntity(0, "DevTeam", "D", 0xFF03DAC5, true);

        groupDao.insertGroup(group);

        List<GroupWithFriends> result = LiveDataTestUtil.getOrAwaitValue(groupDao.getAllGroupsWithFriends());
        assertEquals(1, result.size());
        assertEquals("DevTeam", result.get(0).group.getName());
        assertEquals("D", result.get(0).group.getAvatarLetter());
        assertTrue(result.get(0).group.isOwner());
    }

    @Test
    public void insertGroup_ReturnsGeneratedId() {
        GroupEntity group = new GroupEntity(0, "NewGroup", "N", 0, true);

        long generatedId = groupDao.insertGroup(group);

        assertTrue("Generated ID should be > 0", generatedId > 0);
    }

    @Test
    public void insertGroupWithFriends_ValidData_ReturnsGroupWithMembers() throws InterruptedException {
        // Add Friend first due to constraint
        FriendEntity f1 = new FriendEntity();
        f1.name = "An";
        f1.avatarLetter = "A";
        friendDao.insert(f1);

        FriendEntity f2 = new FriendEntity();
        f2.name = "Bình";
        f2.avatarLetter = "B";
        friendDao.insert(f2);

        // Get ID
        List<FriendEntity> friends = LiveDataTestUtil.getOrAwaitValue(friendDao.getAllFriends());
        int friendId1 = friends.get(0).id;
        int friendId2 = friends.get(1).id;

        // Add Group
        GroupEntity group = new GroupEntity(0, "Team Alpha", "T", 0xFFBB86FC, true);
        long groupId = groupDao.insertGroup(group);

        // Add CrossRef
        groupDao.insertGroupFriendCrossRefs(Arrays.asList(
                new GroupFriendCrossRef((int) groupId, friendId1),
                new GroupFriendCrossRef((int) groupId, friendId2)
        ));

        // Check Friends in Group
        List<GroupWithFriends> result = LiveDataTestUtil.getOrAwaitValue(groupDao.getAllGroupsWithFriends());

        assertEquals(1, result.size());
        assertEquals("Team Alpha", result.get(0).group.getName());
        assertEquals(2, result.get(0).friends.size());
    }

    @Test
    public void deleteGroupById_ExistingGroup_RemovesFromDatabase() throws InterruptedException {
        GroupEntity group = new GroupEntity(0, "ToDelete", "T", 0, true);
        long groupId = groupDao.insertGroup(group);

        // Insert confirmation
        List<GroupWithFriends> beforeDelete = LiveDataTestUtil.getOrAwaitValue(groupDao.getAllGroupsWithFriends());
        assertEquals(1, beforeDelete.size());

        // Delete
        groupDao.deleteGroupById((int) groupId);

        List<GroupWithFriends> afterDelete = LiveDataTestUtil.getOrAwaitValue(groupDao.getAllGroupsWithFriends());
        assertTrue(afterDelete.isEmpty());
    }

    @Test
    public void deleteGroupById_CascadesDeleteToCrossRefs() throws InterruptedException {
        // Add Friend
        FriendEntity friend = new FriendEntity();
        friend.name = "Cường";
        friendDao.insert(friend);

        List<FriendEntity> friendList = LiveDataTestUtil.getOrAwaitValue(friendDao.getAllFriends());
        int friendId = friendList.get(0).id;

        // Add Group + CrossRef
        GroupEntity group = new GroupEntity(0, "CascadeTest", "C", 0, true);
        long groupId = groupDao.insertGroup(group);
        groupDao.insertGroupFriendCrossRefs(List.of(
                new GroupFriendCrossRef((int) groupId, friendId)
        ));

        // Check Friends in Group
        List<GroupWithFriends> before = LiveDataTestUtil.getOrAwaitValue(groupDao.getAllGroupsWithFriends());
        assertEquals(1, before.size());
        assertEquals(1, before.get(0).friends.size());

        // Delete Group — CrossRef is also deleted (CASCADE)
        groupDao.deleteGroupById((int) groupId);

        List<GroupWithFriends> after = LiveDataTestUtil.getOrAwaitValue(groupDao.getAllGroupsWithFriends());
        assertTrue(after.isEmpty());

        // Friend still exists (only delete CrossRef)
        List<FriendEntity> remainingFriends = LiveDataTestUtil.getOrAwaitValue(friendDao.getAllFriends());
        assertEquals(1, remainingFriends.size());
        assertEquals("Cường", remainingFriends.get(0).name);
    }

    // ==================== New Method Tests ====================

    @Test
    public void getGroupWithFriendsById_ExistingGroup_ReturnsCorrectGroup() throws InterruptedException {
        // Arrange
        GroupEntity group1 = new GroupEntity(0, "Alpha", "A", 0xFFBB86FC, true);
        GroupEntity group2 = new GroupEntity(0, "Beta", "B", 0xFF03DAC5, false);
        long id1 = groupDao.insertGroup(group1);
        groupDao.insertGroup(group2);

        // Act
        GroupWithFriends result = LiveDataTestUtil.getOrAwaitValue(
                groupDao.getGroupWithFriendsById((int) id1));

        // Assert
        assertNotNull(result);
        assertEquals("Alpha", result.group.getName());
        assertEquals("A", result.group.getAvatarLetter());
        assertTrue(result.group.isOwner());
    }

    @Test
    public void getGroupWithFriendsById_WithMembers_ReturnsGroupWithFriends() throws InterruptedException {
        // Add Friends
        FriendEntity f1 = new FriendEntity();
        f1.name = "An";
        f1.avatarLetter = "A";
        friendDao.insert(f1);

        FriendEntity f2 = new FriendEntity();
        f2.name = "Bình";
        f2.avatarLetter = "B";
        friendDao.insert(f2);

        List<FriendEntity> friends = LiveDataTestUtil.getOrAwaitValue(friendDao.getAllFriends());
        int fId1 = friends.get(0).id;
        int fId2 = friends.get(1).id;

        // Add Group
        GroupEntity group = new GroupEntity(0, "WithMembers", "W", 0, true);
        long groupId = groupDao.insertGroup(group);

        // Add CrossRefs
        groupDao.insertGroupFriendCrossRefs(Arrays.asList(
                new GroupFriendCrossRef((int) groupId, fId1),
                new GroupFriendCrossRef((int) groupId, fId2)
        ));

        // Act
        GroupWithFriends result = LiveDataTestUtil.getOrAwaitValue(
                groupDao.getGroupWithFriendsById((int) groupId));

        // Assert
        assertNotNull(result);
        assertEquals("WithMembers", result.group.getName());
        assertEquals(2, result.friends.size());
    }

    @Test
    public void getGroupWithFriendsById_NonExistentId_ReturnsNull() throws InterruptedException {
        // Act
        GroupWithFriends result = LiveDataTestUtil.getOrAwaitValue(
                groupDao.getGroupWithFriendsById(999));

        // Assert
        assertNull(result);
    }

    @Test
    public void updateGroup_ValidData_UpdatesInDatabase() throws InterruptedException {
        // Arrange
        GroupEntity group = new GroupEntity(0, "Original", "O", 0xFFBB86FC, true);
        long groupId = groupDao.insertGroup(group);

        // Act
        GroupEntity updated = new GroupEntity((int) groupId, "Renamed", "R", 0xFF03DAC5, true);
        groupDao.updateGroup(updated);

        // Assert
        GroupWithFriends result = LiveDataTestUtil.getOrAwaitValue(
                groupDao.getGroupWithFriendsById((int) groupId));
        assertNotNull(result);
        assertEquals("Renamed", result.group.getName());
        assertEquals("R", result.group.getAvatarLetter());
        assertEquals(0xFF03DAC5, result.group.getAvatarColor());
    }

    @Test
    public void deleteGroupFriendCrossRef_RemovesOneMember_KeepsOthers() throws InterruptedException {
        // Add Friends
        FriendEntity f1 = new FriendEntity();
        f1.name = "An";
        f1.avatarLetter = "A";
        friendDao.insert(f1);

        FriendEntity f2 = new FriendEntity();
        f2.name = "Bình";
        f2.avatarLetter = "B";
        friendDao.insert(f2);

        List<FriendEntity> friends = LiveDataTestUtil.getOrAwaitValue(friendDao.getAllFriends());
        int fId1 = friends.get(0).id;
        int fId2 = friends.get(1).id;

        // Add Group + CrossRefs
        GroupEntity group = new GroupEntity(0, "RemoveTest", "R", 0, true);
        long groupId = groupDao.insertGroup(group);
        groupDao.insertGroupFriendCrossRefs(Arrays.asList(
                new GroupFriendCrossRef((int) groupId, fId1),
                new GroupFriendCrossRef((int) groupId, fId2)
        ));

        // Verify 2 members
        GroupWithFriends before = LiveDataTestUtil.getOrAwaitValue(
                groupDao.getGroupWithFriendsById((int) groupId));
        assertEquals(2, before.friends.size());

        // Act — remove first friend
        groupDao.deleteGroupFriendCrossRef((int) groupId, fId1);

        // Assert — 1 member remains
        GroupWithFriends after = LiveDataTestUtil.getOrAwaitValue(
                groupDao.getGroupWithFriendsById((int) groupId));
        assertEquals(1, after.friends.size());
        assertEquals("Bình", after.friends.get(0).name);

        // Friend entity still exists
        List<FriendEntity> allFriends = LiveDataTestUtil.getOrAwaitValue(friendDao.getAllFriends());
        assertEquals(2, allFriends.size());
    }

    @Test
    public void deleteGroupFriendCrossRef_NonExistentRef_DoesNothing() throws InterruptedException {
        // Arrange
        GroupEntity group = new GroupEntity(0, "NoEffect", "N", 0, true);
        long groupId = groupDao.insertGroup(group);

        // Act — delete non-existent cross ref
        groupDao.deleteGroupFriendCrossRef((int) groupId, 999);

        // Assert — group still exists, no crash
        GroupWithFriends result = LiveDataTestUtil.getOrAwaitValue(
                groupDao.getGroupWithFriendsById((int) groupId));
        assertNotNull(result);
        assertEquals("NoEffect", result.group.getName());
        assertTrue(result.friends.isEmpty());
    }
}
