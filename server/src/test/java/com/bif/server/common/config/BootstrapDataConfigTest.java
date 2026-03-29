package com.bif.server.common.config;

import com.bif.server.features.chat.models.ChatMessage;
import com.bif.server.features.chat.repositories.ChatMessageRepository;
import com.bif.server.features.favorite.models.Favorite;
import com.bif.server.features.favorite.repositories.FavoriteRepository;
import com.bif.server.features.group.models.Group;
import com.bif.server.features.group.repositories.GroupRepository;
import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.trip.models.TripPlan;
import com.bif.server.features.trip.repositories.TripPlanRepository;
import com.bif.server.features.user.models.User;
import com.bif.server.features.user.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BootstrapDataConfigTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private PlaceRepository placeRepository;
    @Mock
    private FavoriteRepository favoriteRepository;
    @Mock
    private TripPlanRepository tripPlanRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private BootstrapDataConfig config;

    @BeforeEach
    void setUp() {
        config = new BootstrapDataConfig();
    }

    @Test
    void seedBaseData_WhenUsersExist_SkipsSeeding() throws Exception {
        when(userRepository.count()).thenReturn(1L);

        ApplicationRunner runner = config.seedBaseData(
                userRepository,
                groupRepository,
                placeRepository,
                favoriteRepository,
                tripPlanRepository,
                chatMessageRepository,
                passwordEncoder
        );

        runner.run(mock(ApplicationArguments.class));

        verify(userRepository, never()).save(any(User.class));
        verify(groupRepository, never()).save(any(Group.class));
        verify(placeRepository, never()).save(any(Place.class));
        verify(favoriteRepository, never()).save(any(Favorite.class));
        verify(tripPlanRepository, never()).save(any(TripPlan.class));
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void seedBaseData_WhenNoUsers_SeedsLinkedData() {
        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-hash");

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId("u-1");
            return user;
        });
        when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> {
            Group group = invocation.getArgument(0);
            group.setId("g-1");
            return group;
        });
        when(placeRepository.save(any(Place.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationRunner runner = config.seedBaseData(
                userRepository,
                groupRepository,
                placeRepository,
                favoriteRepository,
                tripPlanRepository,
                chatMessageRepository,
                passwordEncoder
        );

        assertDoesNotThrow(() -> runner.run(mock(ApplicationArguments.class)));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("alex", userCaptor.getValue().getUsername());
        assertEquals("encoded-hash", userCaptor.getValue().getPasswordHash());

        ArgumentCaptor<Group> groupCaptor = ArgumentCaptor.forClass(Group.class);
        verify(groupRepository).save(groupCaptor.capture());
        assertEquals("Weekend Explorers", groupCaptor.getValue().getName());
        assertEquals("u-1", groupCaptor.getValue().getOwnerId());
        assertNotNull(groupCaptor.getValue().getMemberIds());
        assertEquals("u-1", groupCaptor.getValue().getMemberIds().getFirst());

        ArgumentCaptor<TripPlan> tripCaptor = ArgumentCaptor.forClass(TripPlan.class);
        verify(tripPlanRepository).save(tripCaptor.capture());
        assertEquals("g-1", tripCaptor.getValue().getGroupId());
        assertNotNull(tripCaptor.getValue().getStops());
        assertEquals(1, tripCaptor.getValue().getStops().size());

        ArgumentCaptor<ChatMessage> chatCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(chatCaptor.capture());
        assertEquals("g-1", chatCaptor.getValue().getGroupId());
        assertEquals("u-1", chatCaptor.getValue().getSenderUserId());
    }
}
