package com.bif.app.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.chat.ChatMessageDto;
import com.bif.app.core.network.dto.sync.SyncResponseDto;
import com.bif.app.core.network.dto.trip.TripPlanDto;
import com.bif.app.core.network.dto.trip.TripStopDto;
import com.bif.app.core.utils.UserPreferences;
import com.bif.app.data.source.local.dao.FriendDao;
import com.bif.app.data.source.local.dao.TripDao;
import com.bif.app.data.source.local.entity.FriendEntity;
import com.bif.app.data.source.local.entity.TripMemberCrossRef;
import com.bif.app.data.source.local.entity.TripPlanEntity;
import com.bif.app.data.source.local.entity.TripStopEntity;
import com.bif.app.data.source.local.entity.UploadStatus;
import com.bif.app.data.sync.worker.ImageUploadWorker;
import com.bif.app.data.sync.core.SyncManager;
import com.bif.app.domain.model.TripMember;
import com.bif.app.domain.model.TripPlan;
import com.bif.app.domain.model.TripStop;
import com.bif.app.domain.repository.ITripRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import retrofit2.Response;

@Singleton
public class TripRepository implements ITripRepository {

    private static final String TAG = "TripRepository";

    private final Context appContext;
    private final RestApiService restApiService;
    private final TripDao tripDao;
    private final FriendDao friendDao;
    private final SyncManager syncManager;
    private final ExecutorService executorService;
    private final ExecutorService networkExecutor;

    @Inject
    public TripRepository(@ApplicationContext Context appContext,
                          RestApiService restApiService,
                          TripDao tripDao,
                          FriendDao friendDao,
                          SyncManager syncManager) {
        this.appContext = appContext;
        this.restApiService = restApiService;
        this.tripDao = tripDao;
        this.friendDao = friendDao;
        this.syncManager = syncManager;
        this.executorService = Executors.newSingleThreadExecutor();
        this.networkExecutor = Executors.newSingleThreadExecutor();
    }

    // Visible for tests without Android context.
    public TripRepository(TripDao tripDao, SyncManager syncManager) {
        this(null, null, tripDao, null, syncManager);
    }

    @Override
    public LiveData<List<TripPlan>> getAllTrips() {
        executorService.execute(syncManager::syncIfOnline);
        return Transformations.map(tripDao.getAllTripsWithStops(), this::mapToDomain);
    }

    @Override
    public LiveData<TripPlan> getTripById(String tripId) {
        return Transformations.map(tripDao.getTripWithStopsById(tripId), item -> {
            if (item == null || item.trip == null || item.trip.deleted) {
                return null;
            }

            List<TripPlan> list = mapToDomain(Collections.singletonList(item));
            return list.isEmpty() ? null : list.get(0);
        });
    }

    @Override
    public void createTrip(String title, String description, long startAt, long endAt) {
        createTrip(title, description, startAt, endAt, null);
    }

    @Override
    public void createTrip(String title,
                           String description,
                           long startAt,
                           long endAt,
                           ITripRepository.OperationCallback callback) {
        executorService.execute(() -> {
            boolean success = false;
            try {
                TripPlanEntity entity = new TripPlanEntity();
                entity.id = UUID.randomUUID().toString();
                entity.groupId = entity.id;
                entity.title = title;
                entity.description = description;
                entity.startAt = startAt;
                entity.endAt = endAt;
                entity.serverVersion = 0L;
                entity.deleted = false;

                tripDao.upsertTrip(entity);
                String ownerId = resolveCurrentUserId();
                if (!ownerId.isEmpty()) {
                    tripDao.upsertTripMember(new TripMemberCrossRef(
                            entity.id,
                            ownerId,
                            "OWNER"
                    ));

                    String ownerName = appContext == null
                            ? ""
                            : normalize(UserPreferences.getUsername(appContext));
                    if (ownerName.isEmpty()) {
                        ownerName = ownerId;
                    }
                    upsertFriendCache(ownerId,
                            ownerName,
                            ownerName.substring(0, 1).toUpperCase(),
                            0xFF9C27B0);
                }
                enqueueTripPlanChange(entity.id, "CREATE");
                syncAndReconcileTrip(entity.id);
                success = true;
            } catch (Exception ex) {
                Log.w(TAG, "Failed to create trip", ex);
                success = false;
            }

            if (callback != null) {
                callback.onComplete(success);
            }
        });
    }

    @Override
    public void updateTrip(String tripId,
                           String title,
                           String description,
                           long startAt,
                           long endAt) {
        updateTrip(tripId, title, description, startAt, endAt, null);
    }

    @Override
    public void updateTrip(String tripId,
                           String title,
                           String description,
                           long startAt,
                           long endAt,
                           ITripRepository.OperationCallback callback) {
        executorService.execute(() -> {
            boolean success = false;
            try {
                String safeTripId = normalize(tripId);
                if (safeTripId.isEmpty()) {
                    if (callback != null) {
                        callback.onComplete(false);
                    }
                    return;
                }

                TripPlanEntity entity = tripDao.getTripByIdSync(safeTripId);
                if (entity == null) {
                    if (callback != null) {
                        callback.onComplete(false);
                    }
                    return;
                }

                entity.title = title;
                entity.description = description;
                entity.startAt = startAt;
                entity.endAt = endAt;
                entity.deleted = false;

                tripDao.upsertTrip(entity);
                enqueueTripPlanChange(safeTripId, "UPDATE");
                syncAndReconcileTrip(safeTripId);
                success = true;
            } catch (Exception ex) {
                Log.w(TAG, "Failed to update trip", ex);
            }

            if (callback != null) {
                callback.onComplete(success);
            }
        });
    }

    @Override
    public void deleteTrip(String tripId) {
        deleteTrip(tripId, null);
    }

    @Override
    public void deleteTrip(String tripId, ITripRepository.OperationCallback callback) {
        executorService.execute(() -> {
            boolean success = false;
            try {
                String safeTripId = normalize(tripId);
                if (safeTripId.isEmpty()) {
                    if (callback != null) {
                        callback.onComplete(false);
                    }
                    return;
                }

                TripPlanEntity entity = tripDao.getTripByIdSync(safeTripId);
                if (entity == null) {
                    if (callback != null) {
                        callback.onComplete(false);
                    }
                    return;
                }

                entity.deleted = true;
                tripDao.upsertTrip(entity);

                enqueueTripPlanChange(safeTripId, "DELETE");
                syncAndReconcileTrip(safeTripId);
                success = true;
            } catch (Exception ignored) {
                success = false;
            }

            if (callback != null) {
                callback.onComplete(success);
            }
        });
    }

    @Override
    public void saveDraftTrip(String tripId,
                              String groupId,
                              String title,
                              String description,
                              long startAt,
                              long endAt,
                              List<TripStop> stops,
                              ITripRepository.OperationCallback callback) {
        executorService.execute(() -> {
            boolean success = false;
            try {
                String safeTripId = normalize(tripId);
                if (safeTripId.isEmpty()) {
                    if (callback != null) {
                        callback.onComplete(false);
                    }
                    return;
                }

                TripPlanEntity entity = tripDao.getTripByIdSync(safeTripId);
                boolean isNew = false;
                if (entity == null) {
                    entity = new TripPlanEntity();
                    entity.id = safeTripId;
                    isNew = true;
                }

                String safeGroupId = normalize(groupId);
                entity.groupId = safeGroupId.isEmpty() ? safeTripId : safeGroupId;
                entity.title = title;
                entity.description = description;
                entity.startAt = startAt;
                entity.endAt = endAt;
                entity.deleted = false;
                tripDao.upsertTrip(entity);

                String ownerId = resolveCurrentUserId();
                if (!ownerId.isEmpty()) {
                    tripDao.upsertTripMember(new TripMemberCrossRef(
                            safeTripId,
                            ownerId,
                            "OWNER"
                    ));
                }

                List<String> trackedStopChangeIds = new ArrayList<>();
                List<TripStopEntity> existingStops = tripDao.getActiveStopsByTripSync(safeTripId);
                if (existingStops != null) {
                    for (TripStopEntity existingStop : existingStops) {
                        if (existingStop == null) {
                            continue;
                        }
                        existingStop.deleted = true;
                        existingStop.serverVersion = Math.max(1L,
                                existingStop.serverVersion + 1L);
                        tripDao.upsertStop(existingStop);
                        trackedStopChangeIds.add(enqueueStopUpdate(existingStop));
                    }
                }

                if (stops != null) {
                    for (int i = 0; i < stops.size(); i++) {
                        TripStop stop = stops.get(i);
                        if (stop == null) {
                            continue;
                        }
                        TripStopEntity stopEntity = toStopEntity(safeTripId, stop);
                        stopEntity.orderIndex = i;
                        stopEntity.deleted = false;
                        stopEntity.serverVersion = Math.max(1L,
                                stopEntity.serverVersion + 1L);
                        tripDao.upsertStop(stopEntity);
                        trackedStopChangeIds.add(enqueueStopUpdate(stopEntity));
                    }
                }

                enqueueTripPlanChange(safeTripId, isNew ? "CREATE" : "UPDATE");
                if (trackedStopChangeIds.isEmpty()) {
                    syncAndReconcileTrip(safeTripId);
                } else {
                    syncAndReconcileTripStops(safeTripId, trackedStopChangeIds);
                }
                success = true;
            } catch (Exception ignored) {
                success = false;
            }

            if (callback != null) {
                callback.onComplete(success);
            }
        });
    }

    @Override
    public LiveData<List<TripPlan>> getTripsByGroup(String groupId) {
        refreshTrips(groupId);
        return Transformations.map(tripDao.getTripsWithStopsByGroup(groupId),
                this::mapToDomain);
    }

    @Override
    public LiveData<List<TripMember>> getTripMembers(String tripId) {
        return Transformations.map(tripDao.getTripMembers(tripId), rows -> {
            List<TripMember> members = new ArrayList<>();
            if (rows == null) {
                return members;
            }
            for (TripDao.TripMemberViewRow row : rows) {
                if (row == null) {
                    continue;
                }
                members.add(new TripMember(
                        row.tripId,
                        row.userId,
                        row.name,
                        row.avatarLetter,
                        row.avatarColor,
                        row.role
                ));
            }
            return members;
        });
    }

    @Override
    public void addStopToTrip(String tripId, TripStop stop) {
        executorService.execute(() -> {
            TripStopEntity entity = toStopEntity(tripId, stop);
            List<TripStopEntity> existing = tripDao.getActiveStopsByTripSync(tripId);
            int nextOrder = existing != null ? existing.size() : 0;
            entity.orderIndex = nextOrder;
            entity.deleted = false;
            entity.serverVersion = Math.max(1L, entity.serverVersion + 1L);

            tripDao.upsertStop(entity);
            enqueueImageUploadIfPending(entity);
            String trackedClientChangeId = enqueueStopUpdate(entity);
            syncAndReconcileTripStops(tripId, Collections.singletonList(trackedClientChangeId));
        });
    }

    @Override
    public void updateStopInTrip(String tripId, TripStop stop) {
        executorService.execute(() -> {
            String safeTripId = normalize(tripId);
            if (safeTripId.isEmpty() || stop == null) {
                return;
            }

            String safeStopId = normalize(stop.getId());
            if (safeStopId.isEmpty()) {
                return;
            }

            TripStopEntity existing = tripDao.getStopByIdSync(safeStopId);
            if (existing == null) {
                return;
            }

            TripStopEntity entity = toStopEntity(safeTripId, stop);
            entity.deleted = false;
            entity.serverVersion = Math.max(1L, entity.serverVersion + 1L);

            tripDao.upsertStop(entity);
            enqueueImageUploadIfPending(entity);
            String trackedClientChangeId = enqueueStopUpdate(entity);
            syncAndReconcileTripStops(safeTripId, Collections.singletonList(trackedClientChangeId));
        });
    }

    @Override
    public void stageStopImageUpload(String tripId, String stopId, String localImagePath) {
        executorService.execute(() -> {
            if (tripId == null || tripId.trim().isEmpty()
                    || stopId == null || stopId.trim().isEmpty()
                    || localImagePath == null || localImagePath.trim().isEmpty()) {
                return;
            }

            TripStopEntity entity = tripDao.getStopByIdSync(stopId.trim());
            if (entity == null) {
                entity = new TripStopEntity();
                entity.id = stopId.trim();
                entity.tripId = tripId.trim();
            }

            entity.localImagePath = localImagePath.trim();
            entity.uploadStatus = UploadStatus.PENDING;
            tripDao.upsertStop(entity);

            enqueueImageUploadIfPending(entity);
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
            entity.serverVersion = Math.max(1L, entity.serverVersion + 1L);
            tripDao.upsertStop(entity);
            List<String> trackedClientChangeIds = new ArrayList<>();
            trackedClientChangeIds.add(enqueueStopUpdate(entity));

            trackedClientChangeIds.addAll(normalizeActiveOrderIndexes(tripId, true));
            syncAndReconcileTripStops(tripId, trackedClientChangeIds);
        });
    }

    @Override
    public void addCollaborator(String tripId,
                                String userId,
                                String name,
                                String avatarLetter,
                                int avatarColor) {
        executorService.execute(() -> {
            String safeTripId = normalize(tripId);
            String safeUserId = normalize(userId);
            if (safeTripId.isEmpty() || safeUserId.isEmpty()) {
                return;
            }

            TripMemberCrossRef existingMember = tripDao.getTripMemberSync(safeTripId, safeUserId);
            String role = existingMember != null ? existingMember.role : "COLLABORATOR";
            tripDao.upsertTripMember(new TripMemberCrossRef(safeTripId, safeUserId, role));
            upsertFriendCache(safeUserId, name, avatarLetter, avatarColor);

            enqueueTripPlanChange(safeTripId, "UPDATE");
            syncAndReconcileTrip(safeTripId);
        });
    }

    @Override
    public void removeCollaborator(String tripId, String userId) {
        executorService.execute(() -> {
            String safeTripId = normalize(tripId);
            String safeUserId = normalize(userId);
            if (safeTripId.isEmpty() || safeUserId.isEmpty()) {
                return;
            }

            TripMemberCrossRef existingMember = tripDao.getTripMemberSync(safeTripId, safeUserId);
            if (existingMember != null && "OWNER".equalsIgnoreCase(existingMember.role)) {
                return;
            }

            tripDao.deleteTripMember(safeTripId, safeUserId);

            enqueueTripPlanChange(safeTripId, "UPDATE");
            syncAndReconcileTrip(safeTripId);
        });
    }

    @Override
    public void rearrangeStopsInTrip(String tripId, List<TripStop> newStops) {
        executorService.execute(() -> {
            if (newStops == null) {
                return;
            }

            List<String> trackedClientChangeIds = new ArrayList<>();
            for (int i = 0; i < newStops.size(); i++) {
                TripStopEntity entity = toStopEntity(tripId, newStops.get(i));
                entity.orderIndex = i;
                entity.deleted = false;
                entity.serverVersion = Math.max(1L, entity.serverVersion + 1L);
                tripDao.upsertStop(entity);
                trackedClientChangeIds.add(enqueueStopUpdate(entity));
            }

            syncAndReconcileTripStops(tripId, trackedClientChangeIds);
        });
    }

    @Override
    public void refreshTrips(String groupId) {
        networkExecutor.execute(() -> {
            String safeGroupId = normalize(groupId);
            if (restApiService != null) {
                if (!safeGroupId.isEmpty()) {
                    hydrateTripsByGroupFromApi(safeGroupId);
                } else {
                    hydrateVisibleTripsForCurrentUserFromApi();
                }
            }
            syncManager.syncIfOnline();
        });
    }

    private void hydrateVisibleTripsForCurrentUserFromApi() {
        String userId = resolveCurrentUserId();
        if (userId.isEmpty()) {
            return;
        }

        try {
            Response<List<TripPlanDto>> response = restApiService.getTrips().execute();
            if (!response.isSuccessful() || response.body() == null) {
                return;
            }

            List<TripPlanDto> allTrips = response.body();
            for (TripPlanDto dto : allTrips) {
                if (dto == null || normalize(dto.id).isEmpty()) {
                    continue;
                }
                if (!isVisibleToUser(dto, userId)) {
                    continue;
                }
                upsertTripFromApi(dto, userId);
            }
        } catch (Exception ignored) {
            // Keep local-first behavior when network/API fails.
        }
    }

    private boolean isVisibleToUser(TripPlanDto dto, String userId) {
        if (dto == null || userId == null || userId.trim().isEmpty()) {
            return false;
        }
        if (dto.participantIds == null || dto.participantIds.isEmpty()) {
            return false;
        }
        String normalizedUserId = userId.trim();
        for (String participantId : dto.participantIds) {
            if (participantId != null && normalizedUserId.equals(participantId.trim())) {
                return true;
            }
        }
        return false;
    }

    private void hydrateTripsByGroupFromApi(String groupId) {
        try {
            Response<List<TripPlanDto>> response = restApiService.getTripsByGroup(groupId).execute();
            if (!response.isSuccessful() || response.body() == null) {
                return;
            }

            String activeUserId = resolveCurrentUserId();
            for (TripPlanDto dto : response.body()) {
                upsertTripFromApi(dto, activeUserId);
            }
        } catch (Exception ignored) {
            // Keep local-first behavior when network/API fails.
        }
    }

    private void upsertTripFromApi(TripPlanDto dto, String activeUserId) {
        if (dto == null || normalize(dto.id).isEmpty()) {
            return;
        }

        String tripId = normalize(dto.id);
        TripPlanEntity entity = tripDao.getTripByIdSync(tripId);
        if (entity == null) {
            entity = new TripPlanEntity();
            entity.id = tripId;
        }

        entity.groupId = normalize(dto.groupId);
        entity.title = dto.title;
        entity.description = dto.description;
        entity.startAt = parseInstant(dto.startAt);
        entity.endAt = parseInstant(dto.endAt);
        entity.serverVersion = Math.max(entity.serverVersion, dto.serverVersion);
        entity.deleted = dto.deleted;
        tripDao.upsertTrip(entity);

        if (dto.stops != null) {
            List<TripStopEntity> mappedStops = new ArrayList<>();
            for (TripStopDto stopDto : dto.stops) {
                if (stopDto == null || normalize(stopDto.id).isEmpty()) {
                    continue;
                }
                String stopId = normalize(stopDto.id);
                TripStopEntity existingStop = tripDao.getStopByIdSync(stopId);
                TripStopEntity stopEntity = existingStop != null ? existingStop : new TripStopEntity();
                long incomingVersion = Math.max(stopDto.serverVersion, 0L);
                if (existingStop != null && existingStop.serverVersion > incomingVersion) {
                    mappedStops.add(existingStop);
                    continue;
                }
                stopEntity.id = stopId;
                stopEntity.tripId = tripId;
                stopEntity.title = stopDto.title;
                String incomingAddress = normalize(stopDto.address);
                if (incomingAddress.isEmpty() && existingStop != null) {
                    incomingAddress = normalize(existingStop.address);
                }
                if (incomingAddress.isEmpty() && stopDto.note != null && !stopDto.note.trim().isEmpty()) {
                    incomingAddress = stopDto.note.trim();
                }
                stopEntity.address = incomingAddress;
                stopEntity.note = stopDto.note;
                stopEntity.photoUrl = stopDto.photoUrl;
                if (stopEntity.uploadStatus == null || stopEntity.uploadStatus == UploadStatus.SYNCED) {
                    stopEntity.localImagePath = null;
                    stopEntity.uploadStatus = UploadStatus.SYNCED;
                }
                stopEntity.latitude = stopDto.location != null ? stopDto.location.latitude : 0d;
                stopEntity.longitude = stopDto.location != null ? stopDto.location.longitude : 0d;
                stopEntity.arrivalTime = parseInstant(stopDto.arrivalTime);
                stopEntity.departureTime = parseInstant(stopDto.departureTime);
                stopEntity.orderIndex = stopDto.orderIndex;
                stopEntity.serverVersion = Math.max(stopEntity.serverVersion, incomingVersion);
                stopEntity.deleted = stopDto.deleted;
                mappedStops.add(stopEntity);
            }
            if (!mappedStops.isEmpty()) {
                tripDao.upsertStops(mappedStops);
            }
        }

        if (dto.participantIds != null) {
            tripDao.replaceTripMembersFromParticipantIds(tripId, dto.participantIds, activeUserId);
        }
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
                            stop.address,
                            stop.note,
                            stop.photoUrl,
                            stop.localImagePath,
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
                    mapParticipantIds(item.members)
            ));
        }

        return result;
    }

    private List<String> normalizeActiveOrderIndexes(String tripId,
                                                     boolean enqueueChanges) {
        List<String> trackedClientChangeIds = new ArrayList<>();
        List<TripStopEntity> activeStops = tripDao.getActiveStopsByTripSync(tripId);
        if (activeStops == null || activeStops.isEmpty()) {
            return trackedClientChangeIds;
        }

        for (int i = 0; i < activeStops.size(); i++) {
            TripStopEntity stop = activeStops.get(i);
            if (stop.orderIndex != i) {
                stop.orderIndex = i;
                stop.serverVersion = Math.max(1L, stop.serverVersion + 1L);
                tripDao.upsertStop(stop);
                if (enqueueChanges) {
                    trackedClientChangeIds.add(enqueueStopUpdate(stop));
                }
            }
        }
        return trackedClientChangeIds;
    }

    private String enqueueStopUpdate(TripStopEntity entity) {
        String clientChangeId = UUID.randomUUID().toString();
        syncManager.enqueueChange(
                "trip_stop",
                entity.id,
                "UPDATE",
                clientChangeId,
                toStopDto(entity)
        );
        return clientChangeId;
    }

    private void syncAndReconcileTrip(String tripId) {
        String safeTripId = normalize(tripId);
        if (safeTripId.isEmpty()) {
            syncManager.syncIfOnline();
            return;
        }

        networkExecutor.execute(() -> {
            if (!syncManager.isOnline()) {
                return;
            }

            SyncResponseDto syncResponse = syncManager.sync();
            if (syncResponse != null) {
                reconcileTripFromServer(safeTripId);
            }
        });
    }

    private void syncAndReconcileTripStops(String tripId,
                                           List<String> trackedClientChangeIds) {
        String safeTripId = normalize(tripId);
        if (safeTripId.isEmpty()) {
            syncManager.syncIfOnline();
            return;
        }

        List<String> trackedIds = trackedClientChangeIds == null
                ? Collections.emptyList()
                : new ArrayList<>(trackedClientChangeIds);

        networkExecutor.execute(() -> {
            if (!syncManager.isOnline()) {
                return;
            }

            SyncResponseDto syncResponse = syncManager.sync();
            if (syncManager.areTrackedChangesAccepted(syncResponse, trackedIds)) {
                reconcileTripFromServer(safeTripId);
            }
        });
    }

    private void reconcileTripFromServer(String tripId) {
        if (restApiService == null) {
            return;
        }

        try {
            Response<List<TripPlanDto>> response = restApiService.getTrips().execute();
            if (!response.isSuccessful() || response.body() == null) {
                return;
            }

            String activeUserId = resolveCurrentUserId();
            for (TripPlanDto dto : response.body()) {
                if (dto == null || normalize(dto.id).isEmpty()) {
                    continue;
                }
                if (!tripId.equals(normalize(dto.id))) {
                    continue;
                }
                upsertTripFromApi(dto, activeUserId);
                return;
            }
        } catch (Exception ex) {
            Log.w(TAG, "Failed to reconcile trip from server: " + tripId, ex);
        }
    }

    private List<String> mapParticipantIds(List<TripMemberCrossRef> members) {
        List<String> participantIds = new ArrayList<>();
        if (members == null) {
            return participantIds;
        }

        String ownerId = null;
        for (TripMemberCrossRef member : members) {
            if (member != null && "OWNER".equalsIgnoreCase(member.role)) {
                ownerId = member.userId;
                break;
            }
        }

        if (ownerId != null && !ownerId.trim().isEmpty()) {
            participantIds.add(ownerId);
        }
        for (TripMemberCrossRef member : members) {
            if (member == null || member.userId == null || member.userId.trim().isEmpty()) {
                continue;
            }
            if (ownerId != null && ownerId.equals(member.userId)) {
                continue;
            }
            participantIds.add(member.userId);
        }
        return participantIds;
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
        entity.address = stop.getAddress();
        entity.note = stop.getNote();
        entity.photoUrl = stop.getPhotoUrl();
        entity.localImagePath = stop.getLocalImagePath();
        if (hasStagedLocalImage(stop.getLocalImagePath())) {
            entity.uploadStatus = UploadStatus.PENDING;
        } else if (entity.uploadStatus == null) {
            entity.uploadStatus = UploadStatus.SYNCED;
        }
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

    private String resolveCurrentUserId() {
        if (appContext == null) {
            return "";
        }
        String id = normalize(UserPreferences.getId(appContext));
        if (!id.isEmpty()) {
            return id;
        }
        return normalize(UserPreferences.getUsername(appContext));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private void enqueueTripPlanChange(String tripId, String operation) {
        String safeTripId = normalize(tripId);
        if (safeTripId.isEmpty()) {
            return;
        }

        TripDao.TripPlanWithStops data = tripDao.getTripWithStopsByIdSync(safeTripId);
        if (data == null || data.trip == null) {
            return;
        }

        syncManager.enqueueChange(
                "trip_plan",
                safeTripId,
                operation,
                UUID.randomUUID().toString(),
                toTripPlanDto(data)
        );
    }

    private void upsertFriendCache(String userId,
                                   String name,
                                   String avatarLetter,
                                   int avatarColor) {
        if (friendDao == null) {
            return;
        }

        FriendEntity existingFriend = friendDao.getByServerUserId(userId);
        FriendEntity entity = existingFriend != null ? existingFriend : new FriendEntity();
        if (existingFriend == null) {
            entity.id = userId.hashCode() & 0x7fffffff;
            entity.serverUserId = userId;
        }

        String safeName = normalize(name);
        if (safeName.isEmpty()) {
            safeName = userId;
        }
        entity.name = safeName;

        String safeLetter = normalize(avatarLetter);
        if (safeLetter.isEmpty()) {
            safeLetter = safeName.isEmpty()
                    ? ""
                    : safeName.substring(0, 1).toUpperCase();
        }
        entity.avatarLetter = safeLetter;
        entity.avatarColor = avatarColor;
        friendDao.insert(entity);
    }

    private TripStopDto toStopDto(TripStopEntity entity) {
        TripStopDto dto = new TripStopDto();
        dto.id = entity.id;
        dto.tripId = entity.tripId;
        dto.title = entity.title;
        dto.address = entity.address;
        dto.note = entity.note;
        dto.photoUrl = entity.photoUrl;
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

    private TripPlanDto toTripPlanDto(TripDao.TripPlanWithStops data) {
        TripPlanDto dto = new TripPlanDto();
        dto.id = data.trip.id;
        dto.groupId = data.trip.groupId;
        dto.title = data.trip.title;
        dto.description = data.trip.description;
        dto.startAt = formatInstant(data.trip.startAt);
        dto.endAt = formatInstant(data.trip.endAt);
        dto.serverVersion = data.trip.serverVersion;
        dto.deleted = data.trip.deleted;
        dto.participantIds = mapParticipantIds(data.members);
        return dto;
    }

    private boolean hasStagedLocalImage(String localImagePath) {
        if (localImagePath == null || localImagePath.trim().isEmpty()) {
            return false;
        }
        String trimmed = localImagePath.trim();
        return !startsWithIgnoreCase(trimmed, "http://")
                && !startsWithIgnoreCase(trimmed, "https://");
    }

    private boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private void enqueueImageUploadIfPending(TripStopEntity entity) {
        if (appContext == null || entity == null) {
            return;
        }
        if (entity.uploadStatus == UploadStatus.PENDING
                && hasStagedLocalImage(entity.localImagePath)) {
            ImageUploadWorker.enqueue(appContext);
        }
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

    private long parseInstant(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0L;
        }
        try {
            return java.time.Instant.parse(value.trim()).toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
