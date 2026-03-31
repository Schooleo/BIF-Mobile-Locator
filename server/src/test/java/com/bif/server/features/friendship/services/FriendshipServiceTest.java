package com.bif.server.features.friendship.services;

import com.bif.server.features.friendship.models.Friendship;
import com.bif.server.features.friendship.models.FriendshipStatus;
import com.bif.server.features.friendship.repositories.FriendshipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendshipServiceTest {

    @Mock
    private FriendshipRepository friendshipRepository;

    private FriendshipService friendshipService;

    @BeforeEach
    void setUp() {
        friendshipService = new FriendshipService(friendshipRepository);
    }

    @Test
    void acceptRequest_whenAlreadyAccepted_returnsExistingWithoutSave() {
        Friendship existing = new Friendship();
        existing.setId("f-1");
        existing.setReceiverId("u-2");
        existing.setStatus(FriendshipStatus.ACCEPTED);
        when(friendshipRepository.findById("f-1"))
                .thenReturn(Optional.of(existing));

        Friendship result = friendshipService.acceptRequest("f-1", "u-2");

        assertSame(existing, result);
        verify(friendshipRepository, never()).save(existing);
    }

    @Test
    void rejectRequest_whenAlreadyRejected_returnsExistingWithoutSave() {
        Friendship existing = new Friendship();
        existing.setId("f-1");
        existing.setReceiverId("u-2");
        existing.setStatus(FriendshipStatus.REJECTED);
        when(friendshipRepository.findById("f-1"))
                .thenReturn(Optional.of(existing));

        Friendship result = friendshipService.rejectRequest("f-1", "u-2");

        assertSame(existing, result);
        verify(friendshipRepository, never()).save(existing);
    }

    @Test
    void removeFriendship_skipsAlreadyCanceledRows() {
        Friendship canceled = new Friendship();
        canceled.setStatus(FriendshipStatus.CANCELED);

        Friendship accepted = new Friendship();
        accepted.setStatus(FriendshipStatus.ACCEPTED);

        when(friendshipRepository.findByRequesterIdAndReceiverId("u-1", "u-2"))
                .thenReturn(List.of(canceled));
        when(friendshipRepository.findByRequesterIdAndReceiverId("u-2", "u-1"))
                .thenReturn(List.of(accepted));

        friendshipService.removeFriendship("u-2", "u-1");

        verify(friendshipRepository, never()).save(same(canceled));
        verify(friendshipRepository).save(same(accepted));
        assertEquals(FriendshipStatus.CANCELED, accepted.getStatus());
    }
}
