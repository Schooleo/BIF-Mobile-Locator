package com.bif.server.features.favorite.dto.rest;

import com.bif.server.common.models.Location;
import jakarta.validation.constraints.AssertTrue;

public record UpsertMyFavoriteRequest(
        String id,
        String placeId,
        String externalSource,
        String externalId,
        String placeName,
        String name,
        Location location,
        String address,
        String description,
        String notes,
        int rating,
        String imagePath
) {
        @AssertTrue(message = "Either placeId or (externalSource and externalId) must be provided")
        public boolean hasResolvableIdentitySeed() {
                if (hasText(placeId)) {
                        return true;
                }
                return hasText(externalSource) && hasText(externalId);
        }

        @AssertTrue(message = "externalSource must be non-blank when externalId is provided")
        public boolean hasValidExternalSource() {
                if (!hasText(externalId)) {
                        return true;
                }
                return hasText(externalSource);
        }

        @AssertTrue(message = "externalId must be non-blank when externalSource is provided")
        public boolean hasValidExternalId() {
                if (!hasText(externalSource)) {
                        return true;
                }
                return hasText(externalId);
        }

        private static boolean hasText(String value) {
                return value != null && !value.isBlank();
        }
}
