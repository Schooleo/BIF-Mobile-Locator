package com.bif.app.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.bif.app.data.mapper.FriendMapper;
import com.bif.app.data.source.local.FriendDao;
import com.bif.app.domain.model.Friend;
import com.bif.app.domain.repository.IFriendRepository;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

public class FriendRepository implements IFriendRepository {
    private final FriendDao friendDao;
    private final ExecutorService executorService;

    @Inject
    public FriendRepository(FriendDao friendDao) {
        this.friendDao = friendDao;
        this.executorService = Executors.newFixedThreadPool(4);
    }

    @Override
    public LiveData<List<Friend>> getFriends() {
        return Transformations.map(friendDao.getAllFriends(), FriendMapper::toDomainList);
    }

    @Override
    public void addFriend(Friend friend) {
        executorService.execute(() -> friendDao.insert(FriendMapper.toEntity(friend)));
    }

    @Override
    public void deleteFriend(Friend friend) {
        executorService.execute(() -> friendDao.delete(FriendMapper.toEntity(friend)));
    }
}
