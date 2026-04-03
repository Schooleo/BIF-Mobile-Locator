package com.bif.app.data.source.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "trip_stops", foreignKeys = {
        @ForeignKey(entity = TripPlanEntity.class,
                parentColumns = "id",
                childColumns = "tripId",
                onDelete = ForeignKey.CASCADE)
}, indices = {
        @Index(value = {"tripId"}),
        @Index(value = {"tripId", "orderIndex"})
})
public class TripStopEntity {
    @PrimaryKey
    @NonNull
    public String id;

    public String tripId;
    public String title;
    public String note;
    public String photoUrl;
    public String localImagePath;
    public UploadStatus uploadStatus = UploadStatus.SYNCED;
    public double latitude;
    public double longitude;
    public long arrivalTime;
    public long departureTime;
    public int orderIndex;
    public long serverVersion;
    public boolean deleted;

    public TripStopEntity() {
        id = "";
    }
}
