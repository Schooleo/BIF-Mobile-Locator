package com.bif.app.data.repository;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.bif.app.data.LiveDataTestUtil;
import com.bif.app.data.source.local.FavoriteDao;
import com.bif.app.data.source.local.entity.FavoriteEntity;
import com.bif.app.domain.model.Favorite;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

public class FavoriteRepositoryTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private FavoriteDao mockDao;

    private FavoriteRepository repository;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        repository = new FavoriteRepository(mockDao);
    }

    @Test
    public void getAllFavorites_DaoReturnsEntities_ReturnsMappedDomains() throws InterruptedException {
        // Arrange
        List<FavoriteEntity> mockEntities = new ArrayList<>();
        FavoriteEntity entity = new FavoriteEntity();
        entity.id = 1;
        entity.name = "Test Place";
        mockEntities.add(entity);

        MutableLiveData<List<FavoriteEntity>> fakeLiveData = new MutableLiveData<>();
        fakeLiveData.setValue(mockEntities);

        when(mockDao.getAll()).thenReturn(fakeLiveData);

        // Act
        List<Favorite> result = LiveDataTestUtil.getOrAwaitValue(repository.getAllFavorites());

        // Assert
        assertEquals(1, result.size());
        assertEquals("Test Place", result.get(0).name);
        assertEquals(1, result.get(0).id);
    }

    @Test
    public void addFavorite_ValidFavorite_CallsDaoInsert() {
        // Arrange
        Favorite domainItem = new Favorite();
        domainItem.id = 10;
        domainItem.name = "Cafe";

        // Act
        repository.addFavorite(domainItem);

        // Assert
        // Dùng timeout vì Repository gọi ExecutorService chạy dưới nền
        verify(mockDao, timeout(1000)).insert(any(FavoriteEntity.class));
    }
}