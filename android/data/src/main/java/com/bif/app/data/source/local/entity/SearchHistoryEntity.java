package com.bif.app.data.source.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "search_history")
public class SearchHistoryEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String query;
    public long searchedAt;
}
