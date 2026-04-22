package com.bif.server.features.place.repositories;

import com.bif.server.features.place.models.PlaceMapping;

public interface PlaceMappingRepositoryCustom {
    PlaceMapping upsertByExternalKey(String externalSource,
                                     String externalId,
                                     String candidateInternalPlaceId,
                                     String name,
                                     double lat,
                                     double lng);
}
