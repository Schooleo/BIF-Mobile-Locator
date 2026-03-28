package com.bif.app.data.source.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "trip_plans", indices = {
        @Index(value = {"groupId"})
})
public class TripPlanEntity {
    @PrimaryKey
    @NonNull
    public String id;

    public String groupId;
    public String title;
    public String description;
    public long startAt;
    public long endAt;
    public long serverVersion;
    public boolean deleted;

    public TripPlanEntity() {
        id = "";
    }
}
