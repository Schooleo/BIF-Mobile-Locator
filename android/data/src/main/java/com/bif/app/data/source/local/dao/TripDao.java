package com.bif.app.data.source.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Embedded;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Relation;
import androidx.room.Transaction;

import com.bif.app.data.source.local.entity.TripPlanEntity;
import com.bif.app.data.source.local.entity.TripStopEntity;

import java.util.List;

@Dao
public interface TripDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertTrip(TripPlanEntity trip);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertStop(TripStopEntity stop);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertStops(List<TripStopEntity> stops);

    @Transaction
    @Query("SELECT * FROM trip_plans WHERE groupId = :groupId "
            + "AND deleted = 0 ORDER BY startAt ASC")
    LiveData<List<TripPlanWithStops>> getTripsWithStopsByGroup(String groupId);

    @Query("SELECT * FROM trip_stops WHERE tripId = :tripId "
            + "AND deleted = 0 ORDER BY orderIndex ASC")
    List<TripStopEntity> getActiveStopsByTripSync(String tripId);

    @Query("SELECT * FROM trip_stops WHERE id = :stopId LIMIT 1")
    TripStopEntity getStopByIdSync(String stopId);

    @Query("SELECT * FROM trip_stops WHERE deleted = 0 AND localImagePath IS NOT NULL "
            + "AND uploadStatus IN ('PENDING','ERROR') ORDER BY serverVersion ASC LIMIT 1")
    TripStopEntity getFirstPendingUploadStop();

    @Query("SELECT * FROM trip_stops WHERE deleted = 0 AND uploadStatus = 'SYNCED' "
            + "AND localImagePath IS NOT NULL")
    List<TripStopEntity> getSyncedStopsWithLocalImagePath();

    @Query("SELECT localImagePath FROM trip_stops WHERE localImagePath IS NOT NULL")
    List<String> getAllReferencedLocalImagePaths();

    @Query("SELECT * FROM trip_plans WHERE id = :tripId LIMIT 1")
    TripPlanEntity getTripByIdSync(String tripId);

    @Query("SELECT COUNT(*) FROM trip_plans WHERE groupId = :groupId "
            + "AND deleted = 0")
    int countActiveTripsByGroup(String groupId);

    @Query("DELETE FROM trip_plans WHERE id IN "
            + "(SELECT id FROM trip_plans WHERE groupId = :groupId "
            + "AND deleted = 0 ORDER BY serverVersion ASC LIMIT :excess)")
    void evictOldestTripsByGroup(String groupId, int excess);

    @Transaction
    @Query("SELECT * FROM trip_plans WHERE id = :tripId LIMIT 1")
    TripPlanWithStops getTripWithStopsByIdSync(String tripId);

    @Query("DELETE FROM trip_stops WHERE tripId = :tripId")
    void deleteStopsByTripId(String tripId);

    class TripPlanWithStops {
        @Embedded
        public TripPlanEntity trip;

        @Relation(parentColumn = "id", entityColumn = "tripId")
        public List<TripStopEntity> stops;
    }
}
