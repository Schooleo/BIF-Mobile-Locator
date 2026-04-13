package com.bif.app.feature.favorites;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.bif.app.domain.model.Favorite;
import com.bif.app.domain.model.Group;
import com.bif.app.domain.model.Review;
import com.bif.app.domain.repository.IFavoriteRepository;
import com.bif.app.domain.repository.IGroupRepository;
import com.bif.app.domain.repository.IPlaceRepository;
import com.bif.app.domain.repository.IReviewRepository;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class FavoriteDetailViewModel extends ViewModel {

    private final IFavoriteRepository favoriteRepository;
    private final IPlaceRepository placeRepository;
    private final IReviewRepository reviewRepository;
    private final LiveData<List<Group>> groups;
    private final MutableLiveData<Favorite> currentFavorite = new MutableLiveData<>();
    private final MediatorLiveData<Float> dynamicRating = new MediatorLiveData<>();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean cleared;
    private LiveData<com.bif.app.domain.model.Place> placeRatingSource;
    private LiveData<List<Review>> reviewRatingSource;

    @Inject
    public FavoriteDetailViewModel(IGroupRepository groupRepository,
                                   IFavoriteRepository favoriteRepository,
                                   IPlaceRepository placeRepository,
                                   IReviewRepository reviewRepository) {
        this.favoriteRepository = favoriteRepository;
        this.placeRepository = placeRepository;
        this.reviewRepository = reviewRepository;
        this.groups = groupRepository.getGroups();
    }

    public LiveData<List<Group>> getGroups() {
        return groups;
    }

    public LiveData<Favorite> getCurrentFavorite() {
        return currentFavorite;
    }

    public LiveData<Float> getDynamicRating() {
        return dynamicRating;
    }

    public void initializeFavorite(@NonNull Favorite favorite) {
        Favorite existing = currentFavorite.getValue();
        if (existing != null && safeEquals(existing.id, favorite.id)) {
            return;
        }
        Favorite favoriteCopy = copyFavorite(favorite);
        currentFavorite.setValue(favoriteCopy);
        dynamicRating.setValue((float) favoriteCopy.rating);
        loadDynamicRating(favoriteCopy);
    }

    public void updateNotes(String notes) {
        Favorite favorite = currentFavorite.getValue();
        if (favorite == null) {
            return;
        }

        Favorite updatedFavorite = copyFavorite(favorite);
        updatedFavorite.notes = notes != null ? notes.trim() : "";
        currentFavorite.setValue(updatedFavorite);
        favoriteRepository.updateFavorite(updatedFavorite);
    }

    private Favorite copyFavorite(@NonNull Favorite source) {
        Favorite copy = new Favorite();
        copy.id = source.id;
        copy.placeId = source.placeId;
        copy.name = source.name;
        copy.latitude = source.latitude;
        copy.longitude = source.longitude;
        copy.address = source.address;
        copy.description = source.description;
        copy.notes = source.notes;
        copy.rating = source.rating;
        copy.serverVersion = source.serverVersion;
        copy.deleted = source.deleted;
        copy.userId = source.userId;
        return copy;
    }

    private void loadDynamicRating(@NonNull Favorite favorite) {
        String directPlaceId = normalizePlaceId(favorite.placeId);
        if (directPlaceId != null) {
            observeResolvedPlaceRating(directPlaceId);
            return;
        }

        ioExecutor.execute(() -> {
            String resolvedPlaceId = reviewRepository.resolveInternalPlaceId(
                    "FAVORITE",
                    buildExternalId(favorite),
                    favorite.latitude,
                    favorite.longitude,
                    buildPlaceName(favorite)
            );

            if (resolvedPlaceId == null || resolvedPlaceId.trim().isEmpty()) {
                return;
            }

            String normalizedPlaceId = resolvedPlaceId.trim();
            if (cleared) {
                return;
            }
            mainHandler.post(() -> {
                if (cleared) {
                    return;
                }
                if (!safeEquals(normalizedPlaceId, favorite.placeId)) {
                    Favorite updatedFavorite = copyFavorite(favorite);
                    updatedFavorite.placeId = normalizedPlaceId;
                    if (cleared) {
                        return;
                    }
                    currentFavorite.setValue(updatedFavorite);
                    if (cleared) {
                        return;
                    }
                    favoriteRepository.updateFavorite(updatedFavorite);
                }
                if (cleared) {
                    return;
                }
                observeResolvedPlaceRating(normalizedPlaceId);
            });
        });
    }

    private void observeResolvedPlaceRating(@NonNull String placeId) {
        LiveData<com.bif.app.domain.model.Place> source = placeRepository.getPlaceById(placeId);
        if (source == null) {
            return;
        }

        if (placeRatingSource != null) {
            dynamicRating.removeSource(placeRatingSource);
        }

        placeRatingSource = source;
        dynamicRating.addSource(source, place -> {
            if (place != null) {
                if (place.rating > 0f) {
                    dynamicRating.setValue((float) place.rating);
                    clearReviewRatingSource();
                } else {
                    observeReviewAverage(placeId);
                }
            } else {
                observeReviewAverage(placeId);
            }
            dynamicRating.removeSource(source);
            if (placeRatingSource == source) {
                placeRatingSource = null;
            }
        });
    }

    private void observeReviewAverage(@NonNull String placeId) {
        reviewRepository.refreshReviews(placeId, null);

        LiveData<List<Review>> source = reviewRepository.getReviewsForPlace(placeId);
        if (source == null) {
            return;
        }

        if (reviewRatingSource != null) {
            dynamicRating.removeSource(reviewRatingSource);
        }

        reviewRatingSource = source;
        dynamicRating.addSource(source, reviews -> {
            float averageRating = calculateAverageRating(reviews);
            dynamicRating.setValue(averageRating);
        });
    }

    private void clearReviewRatingSource() {
        if (reviewRatingSource != null) {
            dynamicRating.removeSource(reviewRatingSource);
            reviewRatingSource = null;
        }
    }

    private float calculateAverageRating(List<Review> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return 0f;
        }

        int count = 0;
        float totalStars = 0f;
        for (Review review : reviews) {
            if (review == null || review.deleted) {
                continue;
            }
            count++;
            totalStars += review.stars;
        }

        if (count <= 0) {
            return 0f;
        }

        return totalStars / count;
    }

    private String buildExternalId(@NonNull Favorite favorite) {
        if (favorite.id != null && !favorite.id.trim().isEmpty()) {
            return favorite.id.trim();
        }
        if (favorite.name != null && !favorite.name.trim().isEmpty()) {
            return favorite.name.trim();
        }
        return "favorite-unknown";
    }

    private String buildPlaceName(@NonNull Favorite favorite) {
        if (favorite.name != null && !favorite.name.trim().isEmpty()) {
            return favorite.name.trim();
        }
        if (favorite.address != null && !favorite.address.trim().isEmpty()) {
            return favorite.address.trim();
        }
        return "favorite-place";
    }

    private boolean safeEquals(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private String normalizePlaceId(String placeId) {
        if (placeId == null) {
            return null;
        }
        String normalized = placeId.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }

    @Override
    protected void onCleared() {
        if (placeRatingSource != null) {
            dynamicRating.removeSource(placeRatingSource);
            placeRatingSource = null;
        }
        clearReviewRatingSource();
        ioExecutor.shutdownNow();
        cleared = true;
        mainHandler.removeCallbacksAndMessages(null);
        super.onCleared();
    }
}
