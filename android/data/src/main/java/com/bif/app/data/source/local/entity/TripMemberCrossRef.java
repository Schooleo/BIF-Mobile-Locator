package com.bif.app.data.source.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(
        tableName = "trip_members",
        primaryKeys = { "tripId", "userId" },
        foreignKeys = {
                @ForeignKey(
                        entity = TripPlanEntity.class,
                        parentColumns = "id",
                        childColumns = "tripId",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index(value = { "tripId" }),
                @Index(value = { "userId" })
        }
)
public class TripMemberCrossRef {

    @NonNull
    public String tripId;

    @NonNull
    public String userId;

    @NonNull
    public String role;

    public TripMemberCrossRef(@NonNull String tripId,
                              @NonNull String userId,
                              @NonNull String role) {
        this.tripId = tripId;
        this.userId = userId;
        this.role = role;
    }
}