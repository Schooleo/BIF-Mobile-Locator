package com.bif.server.features.group.exceptions;

public class DuplicateGroupMemberException extends RuntimeException {
    public DuplicateGroupMemberException(String memberId) {
        super("member already in group: " + memberId);
    }
}