package com.bif.app.data.source.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Embedded;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Update;
import androidx.room.Query;
import androidx.room.Relation;
import androidx.room.Transaction;

import com.bif.app.data.source.local.entity.TripMemberCrossRef;
import com.bif.app.data.source.local.entity.TripPlanEntity;
import com.bif.app.data.source.local.entity.TripStopEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Dao
public interface TripDao {

        @Insert(onConflict = OnConflictStrategy.IGNORE)
        long insertTripIgnore(TripPlanEntity trip);

        @Update
        void updateTrip(TripPlanEntity trip);

        @Transaction
        default void upsertTrip(TripPlanEntity trip) {
                long inserted = insertTripIgnore(trip);
                if (inserted == -1L) {
                        updateTrip(trip);
                }
        }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertStop(TripStopEntity stop);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertStops(List<TripStopEntity> stops);

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        void upsertTripMember(TripMemberCrossRef member);

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        void upsertTripMembers(List<TripMemberCrossRef> members);

    @Transaction
    @Query("SELECT * FROM trip_plans WHERE groupId = :groupId "
            + "AND deleted = 0 ORDER BY startAt ASC")
    LiveData<List<TripPlanWithStops>> getTripsWithStopsByGroup(String groupId);

    @Transaction
    @Query("SELECT * FROM trip_plans WHERE deleted = 0 ORDER BY startAt ASC")
    LiveData<List<TripPlanWithStops>> getAllTripsWithStops();

    @Transaction
    @Query("SELECT * FROM trip_plans WHERE id = :tripId AND deleted = 0 LIMIT 1")
    LiveData<TripPlanWithStops> getTripWithStopsById(String tripId);

    @Query("SELECT tm.tripId AS tripId, tm.userId AS userId, "
            + "COALESCE(f.name, tm.userId) AS name, "
            + "COALESCE(f.avatarLetter, '?') AS avatarLetter, "
            + "COALESCE(f.avatarColor, 0) AS avatarColor, "
            + "tm.role AS role "
            + "FROM trip_members tm "
            + "LEFT JOIN friends f ON f.serverUserId = tm.userId "
            + "WHERE tm.tripId = :tripId "
            + "ORDER BY CASE WHEN UPPER(tm.role) = 'OWNER' THEN 0 ELSE 1 END, "
            + "COALESCE(f.name, tm.userId) COLLATE NOCASE ASC")
    LiveData<List<TripMemberViewRow>> getTripMembers(String tripId);

    @Query("SELECT * FROM trip_stops WHERE tripId = :tripId "
            + "AND deleted = 0 ORDER BY orderIndex ASC")
    List<TripStopEntity> getActiveStopsByTripSync(String tripId);

    @Query("SELECT * FROM trip_stops WHERE id = :stopId LIMIT 1")
    TripStopEntity getStopByIdSync(String stopId);

    @Query("SELECT * FROM trip_members WHERE tripId = :tripId "
            + "ORDER BY CASE WHEN UPPER(role) = 'OWNER' THEN 0 ELSE 1 END, userId ASC")
    List<TripMemberCrossRef> getTripMembersSync(String tripId);

    @Query("SELECT * FROM trip_members WHERE tripId = :tripId AND userId = :userId LIMIT 1")
    TripMemberCrossRef getTripMemberSync(String tripId, String userId);

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

        @Query("DELETE FROM trip_members WHERE tripId = :tripId")
        void deleteTripMembersByTripId(String tripId);

        @Query("DELETE FROM trip_members WHERE tripId = :tripId AND userId = :userId")
        void deleteTripMember(String tripId, String userId);

        @Transaction
        default void replaceTripMembersFromParticipantIds(String tripId,
                                                                                                          List<String> participantIds,
                                                                                                          String activeUserId) {
                if (tripId == null || tripId.trim().isEmpty()) {
                        return;
                }

                deleteTripMembersByTripId(tripId);
                if (participantIds == null || participantIds.isEmpty()) {
                        return;
                }

                Set<String> uniqueIds = new HashSet<>();
                for (String participantId : participantIds) {
                        if (participantId != null && !participantId.trim().isEmpty()) {
                                uniqueIds.add(participantId.trim());
                        }
                }
                if (uniqueIds.isEmpty()) {
                        return;
                }

                String ownerId = null;
                for (String participantId : participantIds) {
                        if (participantId != null && !participantId.trim().isEmpty()) {
                                ownerId = participantId.trim();
                                break;
                        }
                }

                List<TripMemberCrossRef> members = new ArrayList<>();
                for (String participantId : uniqueIds) {
                        boolean isOwner = ownerId != null && ownerId.equals(participantId);
                        members.add(new TripMemberCrossRef(
                                        tripId,
                                        participantId,
                                        isOwner ? "OWNER" : "COLLABORATOR"
                        ));
                }
                upsertTripMembers(members);
        }

    class TripPlanWithStops {
        @Embedded
        public TripPlanEntity trip;

        @Relation(parentColumn = "id", entityColumn = "tripId")
        public List<TripStopEntity> stops;

                @Relation(parentColumn = "id", entityColumn = "tripId")
                public List<TripMemberCrossRef> members;
        }

        class TripMemberViewRow {
                public String tripId;
                public String userId;
                public String name;
                public String avatarLetter;
                public int avatarColor;
                public String role;
    }
}
