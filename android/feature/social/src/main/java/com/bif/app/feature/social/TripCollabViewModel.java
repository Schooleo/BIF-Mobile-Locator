package com.bif.app.feature.social;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.bif.app.core.utils.UserPreferences;
import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.TripMember;
import com.bif.app.domain.repository.IFriendRepository;
import com.bif.app.domain.repository.ITripRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public class TripCollabViewModel extends ViewModel {

    private final ITripRepository tripRepository;
    private final IFriendRepository friendRepository;
    private final Context appContext;

    private final MutableLiveData<String> tripId = new MutableLiveData<>();
    private final LiveData<List<TripMember>> tripMembers;
    private final LiveData<List<Friend>> friends;
    private final MediatorLiveData<List<Friend>> availableFriends = new MediatorLiveData<>();
    private final MediatorLiveData<Boolean> isCurrentUserOwner = new MediatorLiveData<>(false);
    private final String currentUserId;

    @Inject
    public TripCollabViewModel(ITripRepository tripRepository,
                               IFriendRepository friendRepository,
                               @ApplicationContext Context appContext) {
        this.tripRepository = tripRepository;
        this.friendRepository = friendRepository;
        this.appContext = appContext;

        this.currentUserId = resolveCurrentUserId();
        this.tripMembers = Transformations.switchMap(
                tripId,
                value -> value == null || value.trim().isEmpty()
                        ? new MutableLiveData<>(new ArrayList<>())
                        : tripRepository.getTripMembers(value.trim())
        );
        this.friends = friendRepository.getFriends();

        availableFriends.addSource(this.friends, ignored -> recomputeAvailableFriends());
        availableFriends.addSource(this.tripMembers, ignored -> {
            recomputeAvailableFriends();
            recomputeOwnership();
        });
    }

    public void setTripId(String value) {
        String normalized = value == null ? "" : value.trim();
        String current = tripId.getValue();
        if (current != null && current.equals(normalized)) {
            return;
        }
        tripId.setValue(normalized);
    }

    public LiveData<List<TripMember>> getTripMembers() {
        return tripMembers;
    }

    public LiveData<List<Friend>> getAvailableFriends() {
        return availableFriends;
    }

    public LiveData<Boolean> getIsCurrentUserOwner() {
        return isCurrentUserOwner;
    }

    public String getCurrentUserId() {
        return currentUserId;
    }

    public void addCollaborator(Friend friend) {
        if (friend == null) {
            return;
        }
        String id = resolveFriendUserId(friend);
        if (id.isEmpty()) {
            return;
        }

        String trip = tripId.getValue();
        if (trip == null || trip.trim().isEmpty()) {
            return;
        }

        String avatarLetter = friend.getAvatarLetter();
        if (avatarLetter == null || avatarLetter.trim().isEmpty()) {
            String base = friend.getName() == null || friend.getName().trim().isEmpty()
                    ? id
                    : friend.getName().trim();
            avatarLetter = base.substring(0, 1).toUpperCase(Locale.ROOT);
        }

        tripRepository.addCollaborator(
                trip.trim(),
                id,
                friend.getName(),
                avatarLetter,
                friend.getAvatarColor()
        );
    }

    public void removeCollaborator(TripMember member) {
        if (member == null || member.isOwner()) {
            return;
        }

        String trip = tripId.getValue();
        if (trip == null || trip.trim().isEmpty()) {
            return;
        }

        String userId = member.getUserId();
        if (userId == null || userId.trim().isEmpty()) {
            return;
        }

        tripRepository.removeCollaborator(trip.trim(), userId.trim());
    }

    private void recomputeAvailableFriends() {
        List<Friend> allFriends = friends.getValue();
        List<TripMember> members = tripMembers.getValue();
        Set<String> memberIds = new HashSet<>();
        if (members != null) {
            for (TripMember member : members) {
                if (member != null && member.getUserId() != null && !member.getUserId().trim().isEmpty()) {
                    memberIds.add(member.getUserId().trim());
                }
            }
        }

        List<Friend> filtered = new ArrayList<>();
        if (allFriends != null) {
            for (Friend friend : allFriends) {
                if (friend == null) {
                    continue;
                }
                String friendId = resolveFriendUserId(friend);
                if (friendId.isEmpty() || memberIds.contains(friendId)) {
                    continue;
                }
                filtered.add(friend);
            }
        }
        availableFriends.setValue(filtered);
    }

    private void recomputeOwnership() {
        List<TripMember> members = tripMembers.getValue();
        if (members == null || members.isEmpty() || currentUserId.isEmpty()) {
            isCurrentUserOwner.setValue(false);
            return;
        }

        for (TripMember member : members) {
            if (member == null) {
                continue;
            }
            String memberId = member.getUserId();
            if (memberId != null
                    && currentUserId.equals(memberId.trim())
                    && member.isOwner()) {
                isCurrentUserOwner.setValue(true);
                return;
            }
        }
        isCurrentUserOwner.setValue(false);
    }

    private String resolveCurrentUserId() {
        String id = UserPreferences.getId(appContext);
        if (id != null && !id.trim().isEmpty()) {
            return id.trim();
        }

        String fallback = UserPreferences.getUsername(appContext);
        return fallback == null ? "" : fallback.trim();
    }

    private String resolveFriendUserId(Friend friend) {
        if (friend == null) {
            return "";
        }
        String serverId = friend.getServerUserId();
        if (serverId != null && !serverId.trim().isEmpty()) {
            return serverId.trim();
        }
        int localId = friend.getId();
        return localId > 0 ? String.valueOf(localId) : "";
    }
}