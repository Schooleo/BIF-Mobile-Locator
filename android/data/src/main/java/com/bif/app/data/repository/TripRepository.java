package com.bif.app.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.bif.app.core.network.dto.chat.ChatMessageDto;
import com.bif.app.core.network.dto.trip.TripStopDto;
import com.bif.app.data.source.local.TripDao;
import com.bif.app.data.source.local.entity.TripStopEntity;
import com.bif.app.data.sync.SyncManager;
import com.bif.app.domain.model.TripPlan;
import com.bif.app.domain.model.TripStop;
import com.bif.app.domain.repository.ITripRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TripRepository implements ITripRepository {

    private final TripDao tripDao;
    private final SyncManager syncManager;
    private final ExecutorService executorService;

    @Inject
    public TripRepository(TripDao tripDao, SyncManager syncManager) {
        this.tripDao = tripDao;
        this.syncManager = syncManager;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public LiveData<List<TripPlan>> getTripsByGroup(String groupId) {
        refreshTrips(groupId);
        return Transformations.map(tripDao.getTripsWithStopsByGroup(groupId),
                this::mapToDomain);
    }

    @Override
    public void addStopToTrip(String tripId, TripStop stop) {
        executorService.execute(() -> {
            TripStopEntity entity = toStopEntity(tripId, stop);
            List<TripStopEntity> existing = tripDao.getActiveStopsByTripSync(tripId);
            int nextOrder = existing != null ? existing.size() : 0;
            entity.orderIndex = nextOrder;
            entity.deleted = false;

            tripDao.upsertStop(entity);
            enqueueStopUpdate(entity);
            syncManager.syncIfOnline();
        });
    }

    @Override
    public void removeStopFromTrip(String tripId, String stopId) {
        executorService.execute(() -> {
            TripStopEntity entity = tripDao.getStopByIdSync(stopId);
            if (entity == null) {
                entity = new TripStopEntity();
                entity.id = stopId;
                entity.tripId = tripId;
            }

            entity.deleted = true;
            tripDao.upsertStop(entity);
            enqueueStopUpdate(entity);

            normalizeActiveOrderIndexes(tripId, true);
            syncManager.syncIfOnline();
        });
    }

    @Override
    public void rearrangeStopsInTrip(String tripId, List<TripStop> newStops) {
        executorService.execute(() -> {
            if (newStops == null) {
                return;
            }

            for (int i = 0; i < newStops.size(); i++) {
                TripStopEntity entity = toStopEntity(tripId, newStops.get(i));
                entity.orderIndex = i;
                entity.deleted = false;
                tripDao.upsertStop(entity);
                enqueueStopUpdate(entity);
            }

            syncManager.syncIfOnline();
        });
    }

    @Override
    public void refreshTrips(String groupId) {
        executorService.execute(syncManager::syncIfOnline);
    }

    private List<TripPlan> mapToDomain(List<TripDao.TripPlanWithStops> items) {
        List<TripPlan> result = new ArrayList<>();
        if (items == null) {
            return result;
        }

        for (TripDao.TripPlanWithStops item : items) {
            if (item == null || item.trip == null || item.trip.deleted) {
                continue;
            }

            List<TripStop> stops = new ArrayList<>();
            if (item.stops != null) {
                List<TripStopEntity> stopEntities = new ArrayList<>(item.stops);
                stopEntities.sort((left, right) -> Integer.compare(
                        left != null ? left.orderIndex : Integer.MAX_VALUE,
                        right != null ? right.orderIndex : Integer.MAX_VALUE));
                for (TripStopEntity stop : stopEntities) {
                    if (stop == null || stop.deleted) {
                        continue;
                    }
                    stops.add(new TripStop(
                            stop.id,
                            stop.title,
                            stop.note,
                            stop.latitude,
                            stop.longitude,
                            stop.arrivalTime,
                            stop.departureTime,
                            stop.orderIndex
                    ));
                }
            }

            result.add(new TripPlan(
                    item.trip.id,
                    item.trip.groupId,
                    item.trip.title,
                    item.trip.description,
                    item.trip.startAt,
                    item.trip.endAt,
                    stops,
                    new ArrayList<>()
            ));
        }

        return result;
    }

    private void normalizeActiveOrderIndexes(String tripId,
                                             boolean enqueueChanges) {
        List<TripStopEntity> activeStops = tripDao.getActiveStopsByTripSync(tripId);
        if (activeStops == null || activeStops.isEmpty()) {
            return;
        }

        for (int i = 0; i < activeStops.size(); i++) {
            TripStopEntity stop = activeStops.get(i);
            if (stop.orderIndex != i) {
                stop.orderIndex = i;
                tripDao.upsertStop(stop);
                if (enqueueChanges) {
                    enqueueStopUpdate(stop);
                }
            }
        }
    }

    private void enqueueStopUpdate(TripStopEntity entity) {
        syncManager.enqueueChange(
                "trip_stop",
                entity.id,
                "UPDATE",
                UUID.randomUUID().toString(),
                toStopDto(entity)
        );
    }

    private TripStopEntity toStopEntity(String tripId, TripStop stop) {
        String stopId = stop.getId();
        if (stopId == null || stopId.trim().isEmpty()) {
            stopId = UUID.randomUUID().toString();
        }

        TripStopEntity existing = tripDao.getStopByIdSync(stopId);
        TripStopEntity entity = existing != null ? existing : new TripStopEntity();
        entity.id = stopId;
        entity.tripId = tripId;
        entity.title = stop.getTitle();
        entity.note = stop.getNote();
        entity.latitude = stop.getLatitude();
        entity.longitude = stop.getLongitude();
        entity.arrivalTime = stop.getArrivalTime();
        entity.departureTime = stop.getDepartureTime();
        entity.orderIndex = stop.getOrderIndex();
        if (existing == null) {
            entity.serverVersion = 0L;
            entity.deleted = false;
        }
        return entity;
    }

    private TripStopDto toStopDto(TripStopEntity entity) {
        TripStopDto dto = new TripStopDto();
        dto.id = entity.id;
        dto.tripId = entity.tripId;
        dto.title = entity.title;
        dto.note = entity.note;
        dto.orderIndex = entity.orderIndex;
        dto.arrivalTime = formatInstant(entity.arrivalTime);
        dto.departureTime = formatInstant(entity.departureTime);
        dto.serverVersion = entity.serverVersion;
        dto.deleted = entity.deleted;

        ChatMessageDto.LocationDto location = new ChatMessageDto.LocationDto();
        location.latitude = entity.latitude;
        location.longitude = entity.longitude;
        dto.location = location;

        return dto;
    }

    private String formatInstant(long value) {
        if (value <= 0L) {
            return null;
        }
        try {
            return java.time.Instant.ofEpochMilli(value).toString();
        } catch (Exception ignored) {
            return null;
        }
    }
}

