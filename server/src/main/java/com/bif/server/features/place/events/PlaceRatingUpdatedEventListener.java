package com.bif.server.features.place.events;

import com.bif.server.features.search.config.TypesenseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.typesense.api.Client;

import java.util.HashMap;
import java.util.Map;

@Component
public class PlaceRatingUpdatedEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaceRatingUpdatedEventListener.class);

    private final Client typesenseClient;
    private final TypesenseProperties typesenseProperties;

    public PlaceRatingUpdatedEventListener(Client typesenseClient,
                                           TypesenseProperties typesenseProperties) {
        this.typesenseClient = typesenseClient;
        this.typesenseProperties = typesenseProperties;
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlaceRatingUpdated(PlaceRatingUpdatedEvent event) {
        if (event == null || event.placeId() == null || event.placeId().isBlank()) {
            return;
        }

        if (!typesenseProperties.isEnabled()) {
            return;
        }

        if (typesenseProperties.getApiKey() == null || typesenseProperties.getApiKey().isBlank()) {
            LOGGER.warn("Skip Typesense rating partial update because API key is empty");
            return;
        }

        String collectionName = typesenseProperties.getPlacesCollection();
        if (collectionName == null || collectionName.isBlank()) {
            collectionName = "places";
        }

        Map<String, Object> mapOfData = new HashMap<>();
        mapOfData.put("rating", event.rating());
        mapOfData.put("reviewCount", event.reviewCount());

        try {
            typesenseClient
                    .collections(collectionName)
                    .documents(event.placeId())
                    .update(mapOfData);
        } catch (Exception e) {
            LOGGER.error("Failed to partially update rating in Typesense for place {}", event.placeId(), e);
        }
    }
}
