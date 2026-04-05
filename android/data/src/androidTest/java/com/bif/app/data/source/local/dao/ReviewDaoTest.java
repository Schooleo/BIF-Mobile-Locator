package com.bif.app.data.source.local.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.bif.app.data.LiveDataTestUtil;
import com.bif.app.data.source.local.database.AppDatabase;
import com.bif.app.data.source.local.entity.ReviewEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ReviewDaoTest {
    private AppDatabase db;
    private ReviewDao reviewDao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        // Use in-memory database for testing
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        reviewDao = db.reviewDao();
    }

    @After
    public void closeDb() {
        db.close();
    }

    @Test
    public void upsertAndGetByPlaceId_WhenMultipleReviewsExist_ReturnsDescendingOrderByCreatedAt() throws InterruptedException {
        String placeId = "p1";
        for (int i = 1; i <= 5; i++) {
            ReviewEntity review = new ReviewEntity();
            review.placeId = placeId;
            review.userId = "user" + i;
            review.createdAt = 1000L * i; // Incrementing timestamps
            reviewDao.upsert(review);
        }

        List<ReviewEntity> results = LiveDataTestUtil.getOrAwaitValue(reviewDao.getByPlaceId(placeId));

        assertEquals(5, results.size());
        // Verify descending order (latest first)
        assertEquals("user5", results.get(0).userId);
        assertEquals("user1", results.get(4).userId);
        assertTrue(results.get(0).createdAt > results.get(1).createdAt);
    }

    @Test
    public void getPendingSync_WhenMixedSyncStatesExist_ReturnsOnlyPendingItems() {
        // Unsynced item
        ReviewEntity pending = new ReviewEntity();
        pending.placeId = "p1";
        pending.userId = "u1";
        pending.pendingSync = true;
        reviewDao.upsert(pending);

        // Synced item
        ReviewEntity synced = new ReviewEntity();
        synced.placeId = "p1";
        synced.userId = "u2";
        synced.pendingSync = false;
        reviewDao.upsert(synced);

        List<ReviewEntity> pendingList = reviewDao.getPendingSync();

        assertEquals(1, pendingList.size());
        assertEquals("u1", pendingList.get(0).userId);
        assertTrue(pendingList.get(0).pendingSync);
    }

    @Test
    public void markSynced_WhenCalledForExistingReview_UpdatesMetadataAndUnsetsPendingSync() {
        ReviewEntity pending = new ReviewEntity();
        pending.placeId = "p1";
        pending.userId = "u1";
        pending.pendingSync = true;
        pending.serverVersion = 0;
        reviewDao.upsert(pending);

        long newVersion = 10L;
        long syncTime = System.currentTimeMillis();
        reviewDao.markSynced("p1", "u1", newVersion, syncTime);

        // Verify using sync read
        ReviewEntity updated = reviewDao.getReviewSync("p1", "u1");
        assertFalse(updated.pendingSync);
        assertEquals(newVersion, updated.serverVersion);
        assertEquals(syncTime, updated.lastSyncedAt);
    }
}
