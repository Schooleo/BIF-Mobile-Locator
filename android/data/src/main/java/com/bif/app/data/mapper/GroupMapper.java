package com.bif.app.data.mapper;

import com.bif.app.data.source.local.entity.GroupEntity;
import com.bif.app.data.source.local.entity.GroupWithFriends;
import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.Group;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class GroupMapper {

    @Inject
    public GroupMapper() {
    }

    public Group mapToDomain(GroupWithFriends groupWithFriends) {
        if (groupWithFriends == null || groupWithFriends.group == null) return null;

        List<Friend> members = new ArrayList<>();
        if (groupWithFriends.friends != null) {
            members = FriendMapper.toDomainList(groupWithFriends.friends);
        }

        return new Group(
                groupWithFriends.group.getId(),
                groupWithFriends.group.getName(),
                groupWithFriends.group.getAvatarLetter(),
                groupWithFriends.group.getAvatarColor(),
                members,
                groupWithFriends.group.isOwner()
        );
    }

    public GroupEntity mapToEntity(Group domain) {
        if (domain == null) return null;
        return new GroupEntity(
                domain.getId(),
                domain.getName(),
                domain.getAvatarLetter(),
                domain.getAvatarColor(),
                domain.isOwner()
        );
    }

    public List<Group> mapToDomainList(List<GroupWithFriends> entities) {
        if (entities == null) return new ArrayList<>();
        return entities.stream().map(this::mapToDomain).collect(Collectors.toList());
    }
}