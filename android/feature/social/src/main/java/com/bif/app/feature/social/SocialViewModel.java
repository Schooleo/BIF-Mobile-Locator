package com.bif.app.feature.social;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.bif.app.domain.model.Friend;
import com.bif.app.domain.repository.IFriendRepository;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class SocialViewModel extends ViewModel {
    private final IFriendRepository friendRepository;
    private final LiveData<List<Friend>> friends;

    @Inject
    public SocialViewModel(IFriendRepository friendRepository) {
        this.friendRepository = friendRepository;
        this.friends = friendRepository.getFriends();
    }

    public LiveData<List<Friend>> getFriends() {
        return friends;
    }

    public void addFriend(String name, String avatarLetter, int avatarColor) {
        Friend newFriend = new Friend(0, name, avatarLetter, avatarColor, true);
        friendRepository.addFriend(newFriend);
    }

    public void deleteFriend(Friend friend) {
        friendRepository.deleteFriend(friend);
    }
}
