package com.bif.server.features.place.events;

import com.bif.server.features.search.config.TypesenseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.typesense.api.Client;
import org.typesense.api.exceptions.TypesenseError;

import java.util.HashMap;
import java.util.Map;

@Component
public class PlaceRatingUpdatedEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaceRatingUpdatedEventListener.class);
    private static final int MAX_ATTEMPTS = 4;
    private static final long INITIAL_BACKOFF_MS = 500;

    private final Client typesenseClient;
    private final TypesenseProperties typesenseProperties;

    public PlaceRatingUpdatedEventListener(Client typesenseClient,
                                           TypesenseProperties typesenseProperties) {
        this.typesenseClient = typesenseClient;
        this.typesenseProperties = typesenseProperties;
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
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
            partialUpdateWithRetry(collectionName, event.placeId(), mapOfData);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Typesense partial update interrupted for place {}", event.placeId(), e);
        } catch (Exception e) {
            LOGGER.error("Failed to partially update rating in Typesense for place {}", event.placeId(), e);
        }
    }

    private void partialUpdateWithRetry(
            String collectionName,
            String placeId,
            Map<String, Object> payload
    ) throws Exception {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                typesenseClient
                        .collections(collectionName)
                        .documents(placeId)
                        .update(payload);
                return;
            } catch (Exception ex) {
                if (!isRetryable(ex) || attempt >= MAX_ATTEMPTS) {
                    throw ex;
                }
 
                long backoff = INITIAL_BACKOFF_MS * (1L << (attempt - 1));
                LOGGER.warn(
                        "Typesense partial update failed for place {}. Retrying in {}ms (attempt {}/{})...",
                        placeId,
                        backoff,
                        attempt,
                        MAX_ATTEMPTS);
                Thread.sleep(backoff);
            }
        }
    }

    private boolean isRetryable(Exception ex) {
        if (ex instanceof TypesenseError typesenseError) {
            int status = typesenseError.status;
            return status == 429 || status == 502 || status == 503 || status == 504;
        }
        return false;
    }
}
