package com.bif.app.data.source.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.bif.app.data.LiveDataTestUtil;
import com.bif.app.data.source.local.database.AppDatabase;
import com.bif.app.data.source.local.dao.FavoriteDao;
import com.bif.app.data.source.local.entity.FavoriteEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class FavoriteDaoInstrumentedTest {

    private static final String TEST_USER_ID = "user-test";

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
        entity.id = "fav-insert-1";
        entity.userId = TEST_USER_ID;
        entity.name = "My House";
        entity.address = "123 Street";

        // Act
        favoriteDao.insert(entity);
        List<FavoriteEntity> result = LiveDataTestUtil.getOrAwaitValue(
            favoriteDao.getAll(TEST_USER_ID));

        // Assert
        assertEquals(1, result.size());
        assertEquals("My House", result.get(0).name);
    }

    @Test
    public void upsert_NewEntity_InsertsIntoDatabase() throws InterruptedException {
        // Arrange
        FavoriteEntity entity = new FavoriteEntity();
        entity.id = "fav-upsert-new";
        entity.userId = TEST_USER_ID;
        entity.name = "Brand New";

        // Act
        favoriteDao.upsert(entity);
        FavoriteEntity result = favoriteDao.findById("fav-upsert-new", TEST_USER_ID);

        // Assert
        assertNotNull(result);
        assertEquals("Brand New", result.name);
    }

    @Test
    public void upsert_ExistingEntity_UpdatesDatabase() throws InterruptedException {
        // Arrange
        FavoriteEntity entity = new FavoriteEntity();
        entity.id = "fav-upsert-exist";
        entity.userId = TEST_USER_ID;
        entity.name = "Old Name";
        favoriteDao.insert(entity);

        FavoriteEntity updated = new FavoriteEntity();
        updated.id = "fav-upsert-exist";
        updated.userId = TEST_USER_ID;
        updated.name = "New Name";

        // Act
        favoriteDao.upsert(updated);
        FavoriteEntity result = favoriteDao.findById("fav-upsert-exist", TEST_USER_ID);

        // Assert
        assertNotNull(result);
        assertEquals("New Name", result.name);
    }

    @Test
    public void getAll_ExcludesDeletedEntities() throws InterruptedException {
        // Arrange
        FavoriteEntity active = new FavoriteEntity();
        active.id = "active-1";
        active.userId = TEST_USER_ID;
        active.name = "Active";
        active.deleted = false;

        FavoriteEntity deleted = new FavoriteEntity();
        deleted.id = "deleted-1";
        deleted.userId = TEST_USER_ID;
        deleted.name = "Deleted";
        deleted.deleted = true;

        favoriteDao.insert(active);
        favoriteDao.insert(deleted);

        // Act
        List<FavoriteEntity> result = LiveDataTestUtil.getOrAwaitValue(
            favoriteDao.getAll(TEST_USER_ID));

        // Assert
        assertEquals(1, result.size());
        assertEquals("active-1", result.get(0).id);
    }

    private void assertNotNull(Object obj) {
        org.junit.Assert.assertNotNull(obj);
    }

    @Test
    public void delete_ExistingEntity_RemovesItemFromDatabase() throws InterruptedException {
        // Arrange
        FavoriteEntity entity = new FavoriteEntity();
        entity.id = "fav-delete-1";
        entity.userId = TEST_USER_ID;
        entity.name = "To Be Deleted";
        favoriteDao.insert(entity);

        // Act
        favoriteDao.delete(entity);
        List<FavoriteEntity> result = LiveDataTestUtil.getOrAwaitValue(
            favoriteDao.getAll(TEST_USER_ID));

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    public void searchFavorites_MatchingKeyword_ReturnsMatchedEntities() throws InterruptedException {
        // Arrange
        FavoriteEntity e1 = new FavoriteEntity(); e1.id = "fav-search-1"; e1.userId = TEST_USER_ID; e1.name = "Highlands Coffee"; e1.address = "Q1";
        FavoriteEntity e2 = new FavoriteEntity(); e2.id = "fav-search-2"; e2.userId = TEST_USER_ID; e2.name = "Home"; e2.address = "Q2";
        FavoriteEntity e3 = new FavoriteEntity(); e3.id = "fav-search-3"; e3.userId = TEST_USER_ID; e3.name = "Trung Nguyen Coffee"; e3.address = "Q3";

        favoriteDao.insert(e1);
        favoriteDao.insert(e2);
        favoriteDao.insert(e3);

        // Act (Tìm chữ "Coffee")
        List<FavoriteEntity> result = LiveDataTestUtil.getOrAwaitValue(
            favoriteDao.searchFavorites(TEST_USER_ID, "Coffee"));

        // Assert
        assertEquals(2, result.size());
    }

    @Test
    public void updateAll_ModifiedEntities_UpdatesAllInDatabase() throws InterruptedException {
        // Arrange
        FavoriteEntity e1 = new FavoriteEntity(); e1.id = "fav-update-1"; e1.userId = TEST_USER_ID; e1.name = "A";
        FavoriteEntity e2 = new FavoriteEntity(); e2.id = "fav-update-2"; e2.userId = TEST_USER_ID; e2.name = "B";
        favoriteDao.insert(e1);
        favoriteDao.insert(e2);

        List<FavoriteEntity> listFromDb = LiveDataTestUtil.getOrAwaitValue(
            favoriteDao.getAll(TEST_USER_ID));

        // Sửa dữ liệu
        for (FavoriteEntity item : listFromDb) {
            item.rating = 5; // Cập nhật rating đồng loạt = 5
        }

        // Act
        favoriteDao.updateAll(listFromDb);
        List<FavoriteEntity> result = LiveDataTestUtil.getOrAwaitValue(
            favoriteDao.getAll(TEST_USER_ID));

        // Assert
        assertEquals(5, result.get(0).rating);
        assertEquals(5, result.get(1).rating);
    }
}