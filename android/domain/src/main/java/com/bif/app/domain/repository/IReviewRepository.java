package com.bif.app.domain.repository;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import com.bif.app.domain.model.PlaceIdentityContext;
import com.bif.app.domain.model.Review;
import java.util.List;

public interface IReviewRepository {
    LiveData<List<Review>> getReviewsForPlace(String placeId);
    LiveData<Review> getMyReview(String placeId);
    /** identityContext may be null when no external identity metadata is available. */
    void submitReview(String placeId,
                      int stars,
                      String comment,
                      @Nullable PlaceIdentityContext identityContext);
    /** identityContext may be null when no external identity metadata is available. */
    void updateReview(String placeId,
                      int stars,
                      String comment,
                      @Nullable PlaceIdentityContext identityContext);
    void deleteMyReview(String placeId);
    default void refreshReviews(String placeId) {
        refreshReviews(placeId, null);
    }
    void refreshReviews(String placeId, Runnable onComplete);
    String resolveInternalPlaceId(String externalSource, String externalId, double lat, double lng, String name);
}
