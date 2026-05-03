package com.bif.app.data.repository;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.bif.app.data.LiveDataTestUtil;
import com.bif.app.data.source.local.dao.FriendDao;
import com.bif.app.data.source.local.entity.FriendEntity;
import com.bif.app.domain.model.Friend;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

public class FriendRepositoryTest {
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private FriendDao mockDao;

    private FriendRepository repository;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        repository = new FriendRepository(mockDao);
    }

    @Test
    public void getFriends_DaoReturnsEntities_ReturnsMappedDomains() throws InterruptedException {
        // Arrange
        List<FriendEntity> mockEntities = new ArrayList<>();
        FriendEntity entity = new FriendEntity();
        entity.name = "Hùng";
        entity.avatarLetter = "H";
        mockEntities.add(entity);

        MutableLiveData<List<FriendEntity>> fakeLiveData = new MutableLiveData<>();
        fakeLiveData.setValue(mockEntities);

        when(mockDao.getAllFriends()).thenReturn(fakeLiveData);

        // Act
        List<Friend> result = LiveDataTestUtil.getOrAwaitValue(repository.getFriends());

        // Assert
        assertEquals(1, result.size());
        assertEquals("Hùng", result.get(0).getName());
        assertEquals("H", result.get(0).getAvatarLetter());
    }

    @Test
    public void addFriend_ValidFriend_CallsDaoInsertOnBackgroundThread() {
        // Arrange
        Friend domainItem = new Friend(0,"Khánh", "K", 0xFFFFFF, true);

        // Act
        repository.addFriend(domainItem);

        // Assert
        // Dùng timeout(1000) vì Repository dùng ExecutorService. Hệ thống sẽ đợi tối đa 1s xem Dao có được gọi không.
        verify(mockDao, timeout(1000)).insert(any(FriendEntity.class));
    }

    @Test
    public void deleteFriend_ValidFriend_CallsDaoDeleteOnBackgroundThread() {
        Friend domainItem = new Friend(2,"Nam", "N", 0x000000, false);

        repository.deleteFriend(domainItem);

        verify(mockDao, timeout(1000)).delete(any(FriendEntity.class));
    }
}
