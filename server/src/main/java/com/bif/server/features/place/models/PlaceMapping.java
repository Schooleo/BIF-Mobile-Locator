package com.bif.server.features.place.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

import java.time.Instant;

@Data
@Document(collection = "place_mappings")
@CompoundIndexes({
    @CompoundIndex(name = "uk_source_extid",
        def = "{'externalSource': 1, 'externalId': 1}",
        unique = true,
        partialFilter = "{'externalSource': {'$exists': true, '$type': 'string', '$ne': ''}, "
            + "'externalId': {'$exists': true, '$type': 'string', '$ne': ''}}"),
    @CompoundIndex(name = "idx_internal_place_id", def = "{'internalPlaceId': 1}")
})
public class PlaceMapping {
    @Id
    private String id;
    private String internalPlaceId;
    private String externalSource;
    private String externalId;
    private String name;

    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoJsonPoint location;

    private Instant createdAt;
}
