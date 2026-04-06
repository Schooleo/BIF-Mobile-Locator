package com.bif.app.domain.repository;

import androidx.lifecycle.LiveData;
import com.bif.app.domain.model.Review;
import java.util.List;

public interface IReviewRepository {
    LiveData<List<Review>> getReviewsForPlace(String placeId);
    LiveData<Review> getMyReview(String placeId);
    void submitReview(String placeId, int stars, String comment);
    void updateReview(String placeId, int stars, String comment);
    void deleteMyReview(String placeId);
    default void refreshReviews(String placeId) {
        refreshReviews(placeId, null);
    }
    void refreshReviews(String placeId, Runnable onComplete);
    String resolveInternalPlaceId(String externalSource, String externalId, double lat, double lng, String name);
}
