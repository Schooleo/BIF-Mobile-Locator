package com.bif.server.features.trip.models;

import com.bif.server.common.models.SyncDocument;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Version;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "trip_plans")
public class TripPlan extends SyncDocument {
    @Id
    private String id;
    
    @Version
    private Long version;

    private String groupId;
    private String title;
    private String description;
    private String coverImageUrl;
    private Instant startAt;
    private Instant endAt;
    private List<TripStop> stops;
    private List<String> participantIds;
}