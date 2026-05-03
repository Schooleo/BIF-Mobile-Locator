package com.bif.app.data.source.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.bif.app.data.LiveDataTestUtil;
import com.bif.app.data.source.local.entity.FriendEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class FriendDaoInstrumentedTest {
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppDatabase database;
    private FriendDao friendDao;

    @Before
    public void setup() {
        database = Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase.class
        ).allowMainThreadQueries().build();
        friendDao = database.friendDao();
    }

    @After
    public void teardown() {
        database.close();
    }

    @Test
    public void getAllFriends_EmptyDatabase_ReturnsEmptyList() throws InterruptedException {
        List<FriendEntity> list = LiveDataTestUtil.getOrAwaitValue(friendDao.getAllFriends());
        assertTrue(list.isEmpty());
    }

    @Test
    public void insertFriend_ValidData_SavesToDatabase() throws InterruptedException {
        FriendEntity entity = new FriendEntity();
        entity.name = "Dũng";

        friendDao.insert(entity);
        List<FriendEntity> friends = LiveDataTestUtil.getOrAwaitValue(friendDao.getAllFriends());

        assertEquals(1, friends.size());
        assertEquals("Dũng", friends.get(0).name);
    }

    @Test
    public void getAllFriends_MultipleItems_ReturnsSortedByName() throws InterruptedException {
        FriendEntity e1 = new FriendEntity(); e1.name = "Zoe";
        FriendEntity e2 = new FriendEntity(); e2.name = "Alice";
        FriendEntity e3 = new FriendEntity(); e3.name = "Bob";

        friendDao.insert(e1);
        friendDao.insert(e2);
        friendDao.insert(e3);

        List<FriendEntity> result = LiveDataTestUtil.getOrAwaitValue(friendDao.getAllFriends());

        assertEquals(3, result.size());
        assertEquals("Alice", result.get(0).name);
        assertEquals("Bob", result.get(1).name);
        assertEquals("Zoe", result.get(2).name);
    }

    @Test
    public void deleteFriend_ExistingItem_RemovesFromDatabase() throws InterruptedException {
        FriendEntity entity = new FriendEntity();
        entity.name = "Target";
        friendDao.insert(entity);

        List<FriendEntity> currentList = LiveDataTestUtil.getOrAwaitValue(friendDao.getAllFriends());
        FriendEntity itemToDelete = currentList.get(0);

        friendDao.delete(itemToDelete);

        List<FriendEntity> afterDeleteList = LiveDataTestUtil.getOrAwaitValue(friendDao.getAllFriends());
        assertTrue(afterDeleteList.isEmpty());
    }
}