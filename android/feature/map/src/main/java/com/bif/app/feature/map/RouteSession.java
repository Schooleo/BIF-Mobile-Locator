package com.bif.app.feature.map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Place;
import com.bif.app.domain.model.Route;

public final class RouteSession {

    public enum Status {
        IDLE,
        LOADING,
        READY,
        ERROR
    }

    public final Status status;
    @Nullable
    public final Place destinationPlace;
    @Nullable
    public final Route route;
    @Nullable
    public final String summaryText;
    @Nullable
    public final String durationText;
    @Nullable
    public final String distanceText;
    @Nullable
    public final String errorText;
    public final boolean following;
    @Nullable
    public final Location lastKnownLocation;
    public final float lastBearingDegrees;

    private RouteSession(@NonNull Status status,
                         @Nullable Place destinationPlace,
                         @Nullable Route route,
                         @Nullable String summaryText,
                         @Nullable String durationText,
                         @Nullable String distanceText,
                         @Nullable String errorText,
                         boolean following,
                         @Nullable Location lastKnownLocation,
                         float lastBearingDegrees) {
        this.status = status;
        this.destinationPlace = destinationPlace;
        this.route = route;
        this.summaryText = summaryText;
        this.durationText = durationText;
        this.distanceText = distanceText;
        this.errorText = errorText;
        this.following = following;
        this.lastKnownLocation = lastKnownLocation;
        this.lastBearingDegrees = lastBearingDegrees;
    }

    @NonNull
    public static RouteSession idle() {
        return new RouteSession(
                Status.IDLE,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                0f);
    }

    @NonNull
    public static RouteSession loading(@Nullable Place destinationPlace) {
        return new RouteSession(
                Status.LOADING,
                destinationPlace,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                0f);
    }

    @NonNull
    public static RouteSession ready(@Nullable Place destinationPlace,
                                     @NonNull Route route,
                                     @NonNull String summaryText,
                                     @NonNull String durationText,
                                     @NonNull String distanceText) {
        return new RouteSession(
                Status.READY,
                destinationPlace,
                route,
                summaryText,
                durationText,
                distanceText,
                null,
                false,
                null,
                0f);
    }

    @NonNull
    public static RouteSession error(@Nullable Place destinationPlace,
                                     @NonNull String errorText) {
        return new RouteSession(
                Status.ERROR,
                destinationPlace,
                null,
                errorText,
                null,
                null,
                errorText,
                false,
                null,
                0f);
    }

    public boolean isVisible() {
        return status != Status.IDLE;
    }

    public boolean hasRoute() {
        return status == Status.READY && route != null;
    }

    @NonNull
    public RouteSession withFollowing(boolean following) {
        return new RouteSession(
                status,
                destinationPlace,
                route,
                summaryText,
                durationText,
                distanceText,
                errorText,
                following,
                lastKnownLocation,
                lastBearingDegrees);
    }

    @NonNull
    public RouteSession withLocation(@Nullable Location lastKnownLocation,
                                     float lastBearingDegrees) {
        return new RouteSession(
                status,
                destinationPlace,
                route,
                summaryText,
                durationText,
                distanceText,
                errorText,
                following,
                lastKnownLocation,
                lastBearingDegrees);
    }
}
