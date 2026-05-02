package com.bif.app.feature.social.core;

import com.bif.app.feature.social.R;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.bif.app.core.utils.InputLimits;
import com.bif.app.core.utils.UserPreferences;
import com.bif.app.domain.model.AiTripDraft;
import com.bif.app.domain.model.AiTripDraftResult;
import com.bif.app.domain.model.AiTripDraftStop;
import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.Friendship;
import com.bif.app.domain.model.Group;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Place;
import com.bif.app.domain.model.TripPlan;
import com.bif.app.domain.model.TripStop;
import com.bif.app.domain.repository.IChatRepository;
import com.bif.app.domain.repository.IFriendshipRepository;
import com.bif.app.domain.repository.IGroupRepository;
import com.bif.app.domain.repository.ITripRepository;
import com.bif.app.feature.social.ai.AiDraftPromptBuilder;
import com.bif.app.feature.social.ai.AiDraftScheduleResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public class SocialViewModel extends ViewModel {

    public static final String AI_DRAFT_EMPTY_QUERY_MESSAGE = "__MSG_AI_DRAFT_EMPTY_QUERY__";
    public static final String AI_DRAFT_FAILED_MESSAGE = "__MSG_AI_DRAFT_FAILED__";
    public static final String AI_DRAFT_INVALID_MESSAGE = "__MSG_AI_DRAFT_INVALID__";
    public static final String AI_DRAFT_SAVE_SUCCESS_MESSAGE = "__MSG_AI_DRAFT_SAVE_SUCCESS__";
    public static final String AI_DRAFT_SAVE_FAILED_MESSAGE = "__MSG_AI_DRAFT_SAVE_FAILED__";
    public static final String AI_DRAFT_AUTH_REQUIRED_MESSAGE = "__MSG_AI_DRAFT_AUTH_REQUIRED__";

    private final IFriendshipRepository friendshipRepository;
    private final IGroupRepository groupRepository;
    private final ITripRepository tripRepository;
    private final IChatRepository chatRepository;
    private final Context appContext;
    private final LiveData<List<Friend>> friends;
    private final LiveData<List<Friendship>> pendingRequests;
    private final LiveData<List<Group>> groups;
    private final LiveData<List<TripPlan>> trips;
    private final MediatorLiveData<UiState<List<Friend>>> friendUiState = new MediatorLiveData<>();
    private final MediatorLiveData<UiState<List<Group>>> groupUiState = new MediatorLiveData<>();
    private final MediatorLiveData<UiState<List<TripPlan>>> tripUiState = new MediatorLiveData<>();
    private final MutableLiveData<String> friendActionMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> friendActionLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> groupActionMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> groupActionLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> tripActionMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> tripActionLoading = new MutableLiveData<>(false);
    private final MutableLiveData<AiTripDrafterUiState> aiTripDrafterUiState =
            new MutableLiveData<>(AiTripDrafterUiState.input(""));
    private final Map<LiveData<?>, Set<Observer<?>>> observeOnceObservers =
            Collections.synchronizedMap(new HashMap<>());
    private final AtomicInteger aiDraftRequestToken = new AtomicInteger(0);
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private volatile String lastAiDraftQuery = "";
    private volatile long lastAiDraftStartAt = 0L;
    private volatile long lastAiDraftEndAt = 0L;
    private volatile AiDraftSnapshot lastAiDraftSnapshot;

    @Inject
    public SocialViewModel(IFriendshipRepository friendshipRepository,
                           IGroupRepository groupRepository,
                           ITripRepository tripRepository,
                           IChatRepository chatRepository,
                           @ApplicationContext Context appContext) {
        this.friendshipRepository = friendshipRepository;
        this.groupRepository = groupRepository;
        this.tripRepository = tripRepository;
        this.chatRepository = chatRepository;
        this.appContext = appContext;
        this.friends = friendshipRepository.getFriends();
        this.pendingRequests = friendshipRepository.getPendingRequests();
        this.groups = groupRepository.getGroups();
        this.trips = tripRepository.getAllTrips();

        friendUiState.setValue(UiState.loading());
        groupUiState.setValue(UiState.loading());
        tripUiState.setValue(UiState.loading());

        friendUiState.addSource(this.friends, this::mapFriendState);
        groupUiState.addSource(this.groups, this::mapGroupState);
        tripUiState.addSource(this.trips, this::mapTripState);
    }

    public LiveData<List<Friend>> getFriends() {
        return friends;
    }

    public LiveData<List<Friendship>> getPendingRequests() {
        return pendingRequests;
    }

    public LiveData<List<Group>> getGroups() {
        return groups;
    }

    public LiveData<List<TripPlan>> getTrips() {
        return trips;
    }

    public LiveData<UiState<List<Friend>>> getFriendUiState() {
        return friendUiState;
    }

    public LiveData<UiState<List<Group>>> getGroupUiState() {
        return groupUiState;
    }

    public LiveData<UiState<List<TripPlan>>> getTripUiState() {
        return tripUiState;
    }

    public LiveData<String> getFriendActionMessage() {
        return friendActionMessage;
    }

    public LiveData<Boolean> getFriendActionLoading() {
        return friendActionLoading;
    }

    public void clearFriendActionMessage() {
        friendActionMessage.setValue(null);
    }

    public void clearGroupActionMessage() {
        groupActionMessage.setValue(null);
    }

    public void clearTripActionMessage() {
        tripActionMessage.setValue(null);
    }

    public LiveData<String> getGroupActionMessage() {
        return groupActionMessage;
    }

    public LiveData<Boolean> getGroupActionLoading() {
        return groupActionLoading;
    }

    public LiveData<String> getTripActionMessage() {
        return tripActionMessage;
    }

    public LiveData<Boolean> getTripActionLoading() {
        return tripActionLoading;
    }

    public LiveData<AiTripDrafterUiState> getAiTripDrafterUiState() {
        return aiTripDrafterUiState;
    }

    public boolean isAuthenticated() {
        return UserPreferences.isLoggedIn(appContext)
                && !trimToEmpty(UserPreferences.getAuthToken(appContext)).isEmpty();
    }

    public void openAiTripDrafter() {
        AiTripDrafterUiState current = aiTripDrafterUiState.getValue();
        if (current == null) {
            aiTripDrafterUiState.setValue(AiTripDrafterUiState.input(lastAiDraftQuery));
        }
    }

    public void backToAiInput() {
        aiTripDrafterUiState.setValue(AiTripDrafterUiState.input(lastAiDraftQuery));
    }

    public void clearAiDraftRequest() {
        lastAiDraftQuery = "";
        lastAiDraftStartAt = 0L;
        lastAiDraftEndAt = 0L;
        aiTripDrafterUiState.setValue(AiTripDrafterUiState.input(""));
    }

    public void submitAiTripDraftQuery(String rawQuery) {
        submitAiTripDraftQuery(rawQuery, lastAiDraftStartAt, lastAiDraftEndAt);
    }

    public void submitAiTripDraftQuery(String rawQuery, long startAt, long endAt) {
        if (!isAuthenticated()) {
            aiTripDrafterUiState.setValue(
                    AiTripDrafterUiState.error(lastAiDraftQuery, AI_DRAFT_AUTH_REQUIRED_MESSAGE, null)
            );
            return;
        }

        String query = trimToEmpty(rawQuery);
        if (query.isEmpty()) {
            aiTripDrafterUiState.setValue(
                    AiTripDrafterUiState.error(lastAiDraftQuery, AI_DRAFT_EMPTY_QUERY_MESSAGE, null)
            );
            return;
        }

        lastAiDraftStartAt = Math.max(0L, startAt);
        lastAiDraftEndAt = Math.max(0L, endAt);
        lastAiDraftQuery = query;
        aiTripDrafterUiState.setValue(AiTripDrafterUiState.loading(query));

        int requestToken = aiDraftRequestToken.incrementAndGet();
        LiveData<AiTripDraftResult> source = chatRepository.draftTripFromQuery(
                AiDraftPromptBuilder.buildDraftQueryWithDateRange(query, lastAiDraftStartAt, lastAiDraftEndAt)
        );
        observeOnce(source, result -> {
            if (requestToken != aiDraftRequestToken.get()) {
                return;
            }

            if (result == null) {
                aiTripDrafterUiState.postValue(
                        AiTripDrafterUiState.error(query, AI_DRAFT_FAILED_MESSAGE, null)
                );
                return;
            }

            String failureCode = trimToNull(result.getFailureCode());
            if (failureCode != null) {
                aiTripDrafterUiState.postValue(
                        AiTripDrafterUiState.error(query, AI_DRAFT_FAILED_MESSAGE, failureCode)
                );
                return;
            }

            AiTripDraft draft = result.getDraft();
            if (draft == null) {
                aiTripDrafterUiState.postValue(
                        AiTripDrafterUiState.error(query, AI_DRAFT_INVALID_MESSAGE, null)
                );
                return;
            }

            String draftTitle = normalizeTripTitle(draft.getTitle());
            String draftDescription = trimToEmpty(draft.getSummary());
            List<AiDraftStopPreview> stopPreviews = mapStopPreviews(draft.getStops());
            if (stopPreviews.isEmpty()) {
                aiTripDrafterUiState.postValue(
                        AiTripDrafterUiState.error(query, AI_DRAFT_INVALID_MESSAGE, null)
                );
                return;
            }

            lastAiDraftSnapshot = new AiDraftSnapshot(draftTitle, draftDescription, stopPreviews);
            aiTripDrafterUiState.postValue(
                    AiTripDrafterUiState.success(query, draftTitle, draftDescription, stopPreviews)
            );
        });
    }

    public void retryAiTripDraft() {
        String retryQuery = trimToEmpty(lastAiDraftQuery);
        if (retryQuery.isEmpty()) {
            aiTripDrafterUiState.setValue(AiTripDrafterUiState.input(""));
            return;
        }
        submitAiTripDraftQuery(retryQuery);
    }

    public void saveCurrentAiDraftTrip(ITripRepository.OperationCallback callback) {
        AiDraftSnapshot snapshot = lastAiDraftSnapshot;
        if (snapshot == null) {
            tripActionMessage.postValue(AI_DRAFT_SAVE_FAILED_MESSAGE);
            if (callback != null) {
                callback.onComplete(false);
            }
            return;
        }

        List<TripStop> stops = toTripStops(snapshot.stops, lastAiDraftStartAt, lastAiDraftEndAt);
        if (stops.isEmpty()) {
            tripActionMessage.postValue(AI_DRAFT_SAVE_FAILED_MESSAGE);
            if (callback != null) {
                callback.onComplete(false);
            }
            return;
        }

        long startAt;
        long endAt;
        if (lastAiDraftStartAt > 0L && lastAiDraftEndAt > lastAiDraftStartAt) {
            startAt = lastAiDraftStartAt;
            endAt = lastAiDraftEndAt;
        } else {
            startAt = resolveStartAt(stops, lastAiDraftStartAt);
            endAt = resolveEndAt(stops, startAt, lastAiDraftEndAt);
        }
        String title = normalizeTripTitle(snapshot.title);
        if (title.isEmpty()) {
            title = "AI Draft Trip";
        }

        tripActionLoading.postValue(true);
        tripRepository.saveDraftTrip(
                "ai-draft-" + UUID.randomUUID(),
                "",
                title,
                trimToEmpty(snapshot.description),
                startAt,
                endAt,
                stops,
                success -> {
                    tripActionMessage.postValue(
                            success ? AI_DRAFT_SAVE_SUCCESS_MESSAGE : AI_DRAFT_SAVE_FAILED_MESSAGE
                    );
                    if (success) {
                        tripRepository.refreshTrips("");
                    }
                    tripActionLoading.postValue(false);
                    if (callback != null) {
                        callback.onComplete(success);
                    }
                }
        );
    }

    public void retryFriends() {
        if (!isAuthenticated()) {
            friendUiState.setValue(UiState.empty("Please log in to start collaborating"));
            return;
        }
        mapFriendState(friends.getValue());
        friendshipRepository.refreshFriends();
        friendshipRepository.refreshPendingRequests();
    }

    public void refreshRequestsOnly() {
        if (!isAuthenticated()) {
            return;
        }
        friendshipRepository.refreshPendingRequests();
    }

    public void retryGroups() {
        mapGroupState(groups.getValue());
        groupRepository.refreshGroups();
    }

    public void retryTrips() {
        mapTripState(trips.getValue());
        tripRepository.refreshTrips("");
    }

    public void addFriend(String receiverId) {
        runFriendAction(() -> {
            String resolvedUserId = friendshipRepository.resolveUserId(receiverId);
            if (resolvedUserId == null || resolvedUserId.trim().isEmpty()) {
                throw new IllegalStateException("USER_NOT_FOUND");
            }

            friendshipRepository.sendFriendRequest(resolvedUserId);
            friendshipRepository.refreshPendingRequests();
            return "__MSG_FRIEND_REQUEST_SENT__";
        }, "__MSG_FRIEND_REQUEST_SEND_FAILED__");
    }

    public void deleteFriend(Friend friend) {
        if (friend == null || friend.getServerUserId() == null || friend.getServerUserId().trim().isEmpty()) {
            friendUiState.setValue(UiState.error("Unable to unfriend: missing user id."));
            return;
        }

        runFriendAction(() -> {
            friendshipRepository.unfriend(friend.getServerUserId());
            friendshipRepository.refreshFriends();
            friendshipRepository.refreshPendingRequests();
            return "__MSG_UNFRIEND_SUCCESS__";
        }, "__MSG_UNFRIEND_FAILED__");
    }

    public void acceptFriendRequest(int friendshipId) {
        runFriendAction(() -> {
            friendshipRepository.acceptFriendRequest(friendshipId);
            friendshipRepository.refreshPendingRequests();
            friendshipRepository.refreshFriends();
            return "__MSG_FRIEND_REQUEST_ACCEPT_SUCCESS__";
        }, "__MSG_FRIEND_REQUEST_ACCEPT_FAILED__");
    }

    public void rejectFriendRequest(int friendshipId) {
        runFriendAction(() -> {
            friendshipRepository.rejectFriendRequest(friendshipId);
            friendshipRepository.refreshPendingRequests();
            return "__MSG_FRIEND_REQUEST_REJECT_SUCCESS__";
        }, "__MSG_FRIEND_REQUEST_REJECT_FAILED__");
    }

    public void createTrip(String title, String description, long startAt, long endAt) {
        String normalizedTitle = normalizeTripTitle(title);
        tripActionLoading.postValue(true);
        tripRepository.createTrip(normalizedTitle, description, startAt, endAt, success -> {
            tripActionMessage.postValue(success
                    ? "__MSG_TRIP_CREATE_SUCCESS__"
                    : "__MSG_TRIP_CREATE_FAILED__");
            tripActionLoading.postValue(false);
        });
    }

    public void updateTrip(String tripId, String title, String description, long startAt, long endAt) {
        String normalizedTitle = normalizeTripTitle(title);
        tripActionLoading.postValue(true);
        tripRepository.updateTrip(tripId, normalizedTitle, description, startAt, endAt, success -> {
            tripActionMessage.postValue(success
                    ? "__MSG_TRIP_UPDATE_SUCCESS__"
                    : "__MSG_TRIP_UPDATE_FAILED__");
            tripActionLoading.postValue(false);
        });
    }

    public void deleteTrip(String tripId) {
        tripActionLoading.postValue(true);
        tripRepository.deleteTrip(tripId, success -> {
            tripActionMessage.postValue(success
                    ? "__MSG_TRIP_DELETE_SUCCESS__"
                    : "__MSG_TRIP_DELETE_FAILED__");
            tripActionLoading.postValue(false);
        });
    }

    public void leaveTrip(String tripId) {
        String normalizedTripId = trimToEmpty(tripId);
        String currentUserId = trimToEmpty(UserPreferences.getId(appContext));
        if (normalizedTripId.isEmpty() || currentUserId.isEmpty()) {
            tripActionMessage.postValue("__MSG_TRIP_LEAVE_FAILED__");
            tripActionLoading.postValue(false);
            return;
        }

        tripActionLoading.postValue(true);
        tripRepository.removeCollaborator(normalizedTripId, currentUserId, success -> {
            tripActionMessage.postValue(success
                    ? "__MSG_TRIP_LEAVE_SUCCESS__"
                    : "__MSG_TRIP_LEAVE_FAILED__");
            tripActionLoading.postValue(false);
        });
    }

    public void createGroup(String groupName, List<Friend> selectedMembers) {
        runGroupAction(() -> {
            groupRepository.createGroup(groupName, selectedMembers);
            groupRepository.refreshGroups();
            return "__MSG_GROUP_CREATE_SUCCESS__";
        }, "__MSG_GROUP_CREATE_FAILED__");
    }

    public void handleGroupAction(Group group) {
        if (group == null) {
            return;
        }

        runGroupAction(() -> {
            if (group.isOwner()) {
                groupRepository.disbandGroup(group);
            } else {
                groupRepository.leaveGroup(group);
            }
            groupRepository.refreshGroups();
            return group.isOwner() ? "__MSG_GROUP_DISBAND_SUCCESS__" : "__MSG_GROUP_LEAVE_SUCCESS__";
        }, group.isOwner() ? "__MSG_GROUP_DISBAND_FAILED__" : "__MSG_GROUP_LEAVE_FAILED__");
    }

    public void renameGroup(Group group, String newName) {
        if (group == null || newName == null) {
            return;
        }

        String trimmedName = newName.trim();
        if (trimmedName.isEmpty()) {
            return;
        }

        Group updatedGroup = new Group(
                group.getId(),
                group.getServerId(),
                trimmedName,
                trimmedName.substring(0, 1).toUpperCase(),
                group.getAvatarColor(),
                group.getMembers(),
                group.isOwner()
        );

        runGroupAction(() -> {
            groupRepository.updateGroup(updatedGroup);
            groupRepository.refreshGroups();
            return "__MSG_GROUP_RENAME_SUCCESS__";
        }, "__MSG_GROUP_RENAME_FAILED__");
    }

    private void runGroupAction(GroupAction action, String fallbackErrorCode) {
        groupActionLoading.postValue(true);
        ioExecutor.execute(() -> {
            try {
                String successMessageCode = action.run();
                if (successMessageCode != null && !successMessageCode.isEmpty()) {
                    groupActionMessage.postValue(successMessageCode);
                }
            } catch (IllegalStateException exception) {
                groupActionMessage.postValue(mapGroupActionError(exception.getMessage(), fallbackErrorCode));
            } catch (Exception exception) {
                groupActionMessage.postValue(fallbackErrorCode);
            } finally {
                groupActionLoading.postValue(false);
            }
        });
    }

    private interface GroupAction {
        String run();
    }

    private void mapFriendState(List<Friend> friendList) {
        if (!isAuthenticated()) {
            friendUiState.setValue(UiState.empty("Please log in to start collaborating"));
            return;
        }
        if (friendList == null) {
            friendUiState.setValue(UiState.error("Unable to load friends."));
            return;
        }
        if (friendList.isEmpty()) {
            friendUiState.setValue(UiState.empty("No friends yet."));
            return;
        }
        friendUiState.setValue(UiState.success(friendList));
    }

    private void mapGroupState(List<Group> groupList) {
        if (groupList == null) {
            groupUiState.setValue(UiState.error("Unable to load groups."));
            return;
        }
        if (groupList.isEmpty()) {
            groupUiState.setValue(UiState.empty("No groups yet."));
            return;
        }
        groupUiState.setValue(UiState.success(groupList));
    }

    private void mapTripState(List<TripPlan> tripList) {
        if (tripList == null) {
            tripUiState.setValue(UiState.error("Unable to load trips."));
            return;
        }
        if (tripList.isEmpty()) {
            tripUiState.setValue(UiState.empty("No trips yet."));
            return;
        }
        tripUiState.setValue(UiState.success(tripList));
    }

    private void runFriendAction(FriendAction action, String fallbackErrorCode) {
        if (!isAuthenticated()) {
            friendActionMessage.postValue("__MSG_AUTH_REQUIRED__");
            friendUiState.postValue(UiState.empty("Please log in to start collaborating"));
            return;
        }
        friendActionLoading.postValue(true);
        ioExecutor.execute(() -> {
            try {
                String successMessageCode = action.run();
                if (successMessageCode != null && !successMessageCode.isEmpty()) {
                    friendActionMessage.postValue(successMessageCode);
                }
            } catch (IllegalStateException exception) {
                friendActionMessage.postValue(mapActionError(exception.getMessage(), fallbackErrorCode));
            } catch (RuntimeException exception) {
                friendActionMessage.postValue(fallbackErrorCode);
            } finally {
                friendActionLoading.postValue(false);
            }
        });
    }

    private String mapActionError(String errorCode, String fallbackErrorCode) {
        if ("USER_NOT_FOUND".equals(errorCode)) {
            return "__MSG_USER_NOT_FOUND__";
        }
        if ("SELF_REQUEST".equals(errorCode)) {
            return "__MSG_FRIEND_REQUEST_SELF__";
        }
        if ("REQUEST_PENDING".equals(errorCode)) {
            return "__MSG_FRIEND_REQUEST_PENDING__";
        }
        if ("ALREADY_FRIENDS".equals(errorCode)) {
            return "__MSG_FRIEND_REQUEST_ALREADY_FRIENDS__";
        }
        if ("SEND_FAILED".equals(errorCode)) {
            return "__MSG_FRIEND_REQUEST_SEND_FAILED__";
        }
        if ("SEND_REQUEST_REQUIRES_ONLINE".equals(errorCode)) {
            return "__MSG_FRIEND_REQUEST_REQUIRES_ONLINE__";
        }
        if ("UNFRIEND_FAILED".equals(errorCode)) {
            return "__MSG_UNFRIEND_FAILED__";
        }
        if ("UNFRIEND_REQUIRES_ONLINE".equals(errorCode)) {
            return "__MSG_UNFRIEND_REQUIRES_ONLINE__";
        }
        if ("ACCEPT_FAILED".equals(errorCode)) {
            return "__MSG_FRIEND_REQUEST_ACCEPT_FAILED__";
        }
        if ("REJECT_FAILED".equals(errorCode)) {
            return "__MSG_FRIEND_REQUEST_REJECT_FAILED__";
        }
        return fallbackErrorCode;
    }

    private String mapGroupActionError(String errorCode, String fallbackErrorCode) {
        if ("GROUP_CREATE_REQUIRES_ONLINE".equals(errorCode)) {
            return "__MSG_GROUP_CREATE_REQUIRES_ONLINE__";
        }
        if ("GROUP_DELETE_REQUIRES_ONLINE".equals(errorCode)) {
            return "__MSG_GROUP_DELETE_REQUIRES_ONLINE__";
        }
        if ("GROUP_REMOVE_MEMBER_REQUIRES_ONLINE".equals(errorCode)) {
            return "__MSG_GROUP_REMOVE_MEMBER_REQUIRES_ONLINE__";
        }
        return fallbackErrorCode;
    }

    private List<AiDraftStopPreview> mapStopPreviews(List<AiTripDraftStop> stops) {
        List<AiDraftStopPreview> previews = new ArrayList<>();
        if (stops == null) {
            return previews;
        }

        for (AiTripDraftStop stop : stops) {
            if (stop == null) {
                continue;
            }

            Place place = stop.getPlace();
            Location location = place != null ? place.location : null;
            Double latitude = location != null && Double.isFinite(location.latitude)
                    ? location.latitude
                    : null;
            Double longitude = location != null && Double.isFinite(location.longitude)
                    ? location.longitude
                    : null;

            previews.add(new AiDraftStopPreview(
                    trimToEmpty(stop.getPlaceId()),
                    place != null ? trimToEmpty(place.name) : "",
                    place != null ? trimToEmpty(place.address) : "",
                    trimToEmpty(stop.getNote()),
                    trimToEmpty(stop.getPlannedDateTime()),
                    trimToEmpty(stop.getStartTime()),
                    trimToEmpty(stop.getEndTime()),
                    Math.max(0, stop.getDuration() != null ? stop.getDuration() : stop.getDurationMinutes()),
                    latitude,
                    longitude
            ));
        }

        return previews;
    }

    private List<TripStop> toTripStops(List<AiDraftStopPreview> previews,
                                       long fallbackStartAt,
                                       long fallbackEndAt) {
        List<TripStop> mapped = new ArrayList<>();
        if (previews == null) {
            return mapped;
        }

        AiDraftScheduleResolver.ScheduleCursor cursor =
                AiDraftScheduleResolver.newCursor(fallbackStartAt, fallbackEndAt);
        int order = 0;
        for (AiDraftStopPreview preview : previews) {
            if (preview == null) {
                continue;
            }
            if (!preview.hasValidCoordinates()) {
                continue;
            }

            double latitude = preview.getLatitude();
            double longitude = preview.getLongitude();
            AiDraftScheduleResolver.ScheduledTime scheduledTime =
                    AiDraftScheduleResolver.resolveStopTimes(
                            preview.getPlannedDateTime(),
                            preview.getStartTime(),
                            preview.getEndTime(),
                            preview.getDurationMinutes(),
                            cursor
                    );
            long arrivalTime = scheduledTime.arrivalAt;
            long departureTime = scheduledTime.departureAt;

            mapped.add(new TripStop(
                    UUID.randomUUID().toString(),
                    trimToEmpty(preview.getName()),
                    trimToEmpty(preview.getAddress()),
                    trimToEmpty(preview.getNote()),
                    latitude,
                    longitude,
                    arrivalTime,
                    departureTime,
                    order
            ));
            order++;
        }

        return mapped;
    }

    private long resolveStartAt(List<TripStop> stops) {
        return resolveStartAt(stops, 0L);
    }

    private long resolveStartAt(List<TripStop> stops, long fallbackStartAt) {
        long minArrival = Long.MAX_VALUE;
        if (stops != null) {
            for (TripStop stop : stops) {
                if (stop == null) {
                    continue;
                }
                long arrival = stop.getArrivalTime();
                if (arrival > 0L && arrival < minArrival) {
                    minArrival = arrival;
                }
            }
        }
        if (minArrival == Long.MAX_VALUE) {
            return fallbackStartAt > 0L ? fallbackStartAt : System.currentTimeMillis();
        }
        return minArrival;
    }

    private long resolveEndAt(List<TripStop> stops, long startAt) {
        return resolveEndAt(stops, startAt, 0L);
    }

    private long resolveEndAt(List<TripStop> stops, long startAt, long fallbackEndAt) {
        long maxDeparture = 0L;
        if (stops != null) {
            for (TripStop stop : stops) {
                if (stop == null) {
                    continue;
                }
                long departure = stop.getDepartureTime();
                if (departure > maxDeparture) {
                    maxDeparture = departure;
                }
            }
        }
        if (maxDeparture <= 0L) {
            return fallbackEndAt > 0L ? Math.max(startAt, fallbackEndAt) : startAt;
        }
        return Math.max(startAt, maxDeparture);
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeTripTitle(String value) {
        return InputLimits.trimAndLimit(value, InputLimits.TRIP_TITLE_MAX_LENGTH);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private <T> void observeOnce(LiveData<T> source, Observer<T> observer) {
        Observer<T> oneShotObserver = new Observer<T>() {
            @Override
            public void onChanged(T value) {
                source.removeObserver(this);
                untrackObserveOnceObserver(source, this);
                observer.onChanged(value);
            }
        };

        trackObserveOnceObserver(source, oneShotObserver);
        source.observeForever(oneShotObserver);
    }

    private void trackObserveOnceObserver(LiveData<?> source, Observer<?> observer) {
        synchronized (observeOnceObservers) {
            Set<Observer<?>> observers = observeOnceObservers.computeIfAbsent(source, key -> new HashSet<>());
            observers.add(observer);
        }
    }

    private void untrackObserveOnceObserver(LiveData<?> source, Observer<?> observer) {
        synchronized (observeOnceObservers) {
            Set<Observer<?>> observers = observeOnceObservers.get(source);
            if (observers == null) {
                return;
            }
            observers.remove(observer);
            if (observers.isEmpty()) {
                observeOnceObservers.remove(source);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void clearObserveOnceObservers() {
        synchronized (observeOnceObservers) {
            for (Map.Entry<LiveData<?>, Set<Observer<?>>> entry : observeOnceObservers.entrySet()) {
                LiveData source = entry.getKey();
                for (Observer<?> observer : entry.getValue()) {
                    source.removeObserver(observer);
                }
            }
            observeOnceObservers.clear();
        }
    }

    @Override
    protected void onCleared() {
        clearObserveOnceObservers();
        super.onCleared();
        ioExecutor.shutdownNow();
    }

    private interface FriendAction {
        String run();
    }

    private static class AiDraftSnapshot {
        final String title;
        final String description;
        final List<AiDraftStopPreview> stops;

        AiDraftSnapshot(String title, String description, List<AiDraftStopPreview> stops) {
            this.title = title;
            this.description = description;
            this.stops = stops == null ? Collections.emptyList() : new ArrayList<>(stops);
        }
    }

    public static class AiDraftStopPreview {
        private final String placeId;
        private final String name;
        private final String address;
        private final String note;
        private final String plannedDateTime;
        private final String startTime;
        private final String endTime;
        private final int durationMinutes;
        private final Double latitude;
        private final Double longitude;

        public AiDraftStopPreview(String placeId,
                                  String name,
                                  String address,
                                  String note,
                                  String plannedDateTime,
                                  int durationMinutes,
                                  Double latitude,
                                  Double longitude) {
            this(placeId, name, address, note, plannedDateTime, null, null,
                    durationMinutes, latitude, longitude);
        }

        public AiDraftStopPreview(String placeId,
                                  String name,
                                  String address,
                                  String note,
                                  String plannedDateTime,
                                  String startTime,
                                  String endTime,
                                  int durationMinutes,
                                  Double latitude,
                                  Double longitude) {
            this.placeId = placeId;
            this.name = name;
            this.address = address;
            this.note = note;
            this.plannedDateTime = plannedDateTime;
            this.startTime = startTime;
            this.endTime = endTime;
            this.durationMinutes = durationMinutes;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public String getPlaceId() {
            return placeId;
        }

        public String getName() {
            return name;
        }

        public String getAddress() {
            return address;
        }

        public String getNote() {
            return note;
        }

        public String getPlannedDateTime() {
            return plannedDateTime;
        }

        public String getStartTime() {
            return startTime;
        }

        public String getEndTime() {
            return endTime;
        }

        public int getDurationMinutes() {
            return durationMinutes;
        }

        public Double getLatitude() {
            return latitude;
        }

        public Double getLongitude() {
            return longitude;
        }

        public boolean hasValidCoordinates() {
            return latitude != null
                    && longitude != null
                    && Double.isFinite(latitude)
                    && Double.isFinite(longitude);
        }
    }

    public abstract static class AiTripDrafterUiState {

        private AiTripDrafterUiState() {
        }

        public static AiTripDrafterUiState input(String query) {
            return new Input(query);
        }

        public static AiTripDrafterUiState loading(String query) {
            return new Loading(query);
        }

        public static AiTripDrafterUiState success(String query,
                                                   String title,
                                                   String description,
                                                   List<AiDraftStopPreview> stops) {
            return new Success(query, title, description, stops);
        }

        public static AiTripDrafterUiState error(String query,
                                                 String errorCode,
                                                 String failureCode) {
            return new Error(query, errorCode, failureCode);
        }

        public static final class Input extends AiTripDrafterUiState {
            private final String query;

            private Input(String query) {
                this.query = query;
            }

            public String getQuery() {
                return query;
            }
        }

        public static final class Loading extends AiTripDrafterUiState {
            private final String query;

            private Loading(String query) {
                this.query = query;
            }

            public String getQuery() {
                return query;
            }
        }

        public static final class Success extends AiTripDrafterUiState {
            private final String query;
            private final String title;
            private final String description;
            private final List<AiDraftStopPreview> stops;

            private Success(String query,
                            String title,
                            String description,
                            List<AiDraftStopPreview> stops) {
                this.query = query;
                this.title = title;
                this.description = description;
                this.stops = stops == null ? Collections.emptyList() : new ArrayList<>(stops);
            }

            public String getQuery() {
                return query;
            }

            public String getTitle() {
                return title;
            }

            public String getDescription() {
                return description;
            }

            public List<AiDraftStopPreview> getStops() {
                return new ArrayList<>(stops);
            }
        }

        public static final class Error extends AiTripDrafterUiState {
            private final String query;
            private final String errorCode;
            private final String failureCode;

            private Error(String query, String errorCode, String failureCode) {
                this.query = query;
                this.errorCode = errorCode;
                this.failureCode = failureCode;
            }

            public String getQuery() {
                return query;
            }

            public String getErrorCode() {
                return errorCode;
            }

            public String getFailureCode() {
                return failureCode;
            }
        }
    }
}
