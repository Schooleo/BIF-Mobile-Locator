package com.bif.app.data.source.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.bif.app.data.LiveDataTestUtil;
import com.bif.app.data.source.local.entity.FavoriteEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class FavoriteDaoInstrumentedTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppDatabase database;
    private FavoriteDao favoriteDao;

    @Before
    public void setup() {
        // Khởi tạo In-Memory Database (Chỉ tồn tại trên RAM trong lúc chạy test)
        database = Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase.class
        ).allowMainThreadQueries().build(); // Cho phép chạy db trên MainThread khi test

        favoriteDao = database.favoriteDao();
    }

    @After
    public void teardown() {
        database.close();
    }

    @Test
    public void insert_ValidEntity_SavesItemToDatabase() throws InterruptedException {
        // Arrange
        FavoriteEntity entity = new FavoriteEntity();
        entity.name = "My House";
        entity.address = "123 Street";

        // Act
        favoriteDao.insert(entity);
        List<FavoriteEntity> result = LiveDataTestUtil.getOrAwaitValue(favoriteDao.getAll());

        // Assert
        assertEquals(1, result.size());
        assertEquals("My House", result.get(0).name);
    }

    @Test
    public void delete_ExistingEntity_RemovesItemFromDatabase() throws InterruptedException {
        // Arrange
        FavoriteEntity entity = new FavoriteEntity();
        entity.name = "To Be Deleted";
        favoriteDao.insert(entity);

        // Lấy list ra để lấy được ID (do autoGenerate)
        List<FavoriteEntity> currentList = LiveDataTestUtil.getOrAwaitValue(favoriteDao.getAll());
        FavoriteEntity itemToDelete = currentList.get(0);

        // Act
        favoriteDao.delete(itemToDelete);
        List<FavoriteEntity> result = LiveDataTestUtil.getOrAwaitValue(favoriteDao.getAll());

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    public void searchFavorites_MatchingKeyword_ReturnsMatchedEntities() throws InterruptedException {
        // Arrange
        FavoriteEntity e1 = new FavoriteEntity(); e1.name = "Highlands Coffee"; e1.address = "Q1";
        FavoriteEntity e2 = new FavoriteEntity(); e2.name = "Home"; e2.address = "Q2";
        FavoriteEntity e3 = new FavoriteEntity(); e3.name = "Trung Nguyen Coffee"; e3.address = "Q3";

        favoriteDao.insert(e1);
        favoriteDao.insert(e2);
        favoriteDao.insert(e3);

        // Act (Tìm chữ "Coffee")
        List<FavoriteEntity> result = LiveDataTestUtil.getOrAwaitValue(favoriteDao.searchFavorites("Coffee"));

        // Assert
        assertEquals(2, result.size());
    }

    @Test
    public void updateAll_ModifiedEntities_UpdatesAllInDatabase() throws InterruptedException {
        // Arrange
        FavoriteEntity e1 = new FavoriteEntity(); e1.name = "A";
        FavoriteEntity e2 = new FavoriteEntity(); e2.name = "B";
        favoriteDao.insert(e1);
        favoriteDao.insert(e2);

        List<FavoriteEntity> listFromDb = LiveDataTestUtil.getOrAwaitValue(favoriteDao.getAll());

        // Sửa dữ liệu
        for (FavoriteEntity item : listFromDb) {
            item.rating = 5; // Cập nhật rating đồng loạt = 5
        }

        // Act
        favoriteDao.updateAll(listFromDb);
        List<FavoriteEntity> result = LiveDataTestUtil.getOrAwaitValue(favoriteDao.getAll());

        // Assert
        assertEquals(5, result.get(0).rating);
        assertEquals(5, result.get(1).rating);
    }
}