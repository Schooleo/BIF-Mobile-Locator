package com.bif.server.features.group.exceptions;

public class GroupMemberNotFoundException extends RuntimeException {
    public GroupMemberNotFoundException(String memberId) {
        super("member is not in group: " + memberId);
    }
}