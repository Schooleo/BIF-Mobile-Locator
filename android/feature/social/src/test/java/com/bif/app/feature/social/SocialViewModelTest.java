package com.bif.app.feature.social;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.bif.app.domain.model.AiTripDraft;
import com.bif.app.domain.model.AiTripDraftResult;
import com.bif.app.domain.model.AiTripDraftStop;
import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.Group;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Place;
import com.bif.app.domain.repository.IChatRepository;
import com.bif.app.domain.repository.IFriendshipRepository;
import com.bif.app.domain.repository.IGroupRepository;
import com.bif.app.domain.repository.ITripRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SocialViewModelTest {
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private IFriendshipRepository mockFriendshipRepository;

    @Mock
    private IGroupRepository mockGroupRepository;

    @Mock
    private ITripRepository mockTripRepository;

    @Mock
    private IChatRepository mockChatRepository;

    private SocialViewModel viewModel;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mockFriendshipRepository.getFriends()).thenReturn(new MutableLiveData<>());
        when(mockFriendshipRepository.getPendingRequests()).thenReturn(new MutableLiveData<>());
        when(mockGroupRepository.getGroups()).thenReturn(new MutableLiveData<>());
        when(mockTripRepository.getAllTrips()).thenReturn(new MutableLiveData<>());
        viewModel = new SocialViewModel(
            mockFriendshipRepository,
            mockGroupRepository,
            mockTripRepository,
            mockChatRepository);
    }

    // ==================== Friend Tests ====================

    @Test
    public void init_CallsRepositoryGetFriends() {
        verify(mockFriendshipRepository).getFriends();
    }

    @Test
    public void addFriend_ValidData_CallsSendFriendRequest() {
        when(mockFriendshipRepository.resolveUserId("cuong-id")).thenReturn("cuong-id");

        // Act
        viewModel.addFriend("cuong-id");

        // Assert
        verify(mockFriendshipRepository, timeout(1000)).resolveUserId("cuong-id");
        verify(mockFriendshipRepository, timeout(1000)).sendFriendRequest("cuong-id");
        verify(mockFriendshipRepository, timeout(1000)).refreshPendingRequests();
    }

    @Test
    public void addFriend_UserNotFound_DoesNotCallSendFriendRequest() {
        when(mockFriendshipRepository.resolveUserId("missing-user")).thenReturn(null);

        viewModel.addFriend("missing-user");

        verify(mockFriendshipRepository, timeout(1000)).resolveUserId("missing-user");
        org.mockito.Mockito.verify(mockFriendshipRepository, after(300).never())
                .sendFriendRequest(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    public void addFriend_SelfRequest_DoesNotCallSendFriendRequest() {
        when(mockFriendshipRepository.resolveUserId("self-user")).thenReturn("self-user");
        org.mockito.Mockito.doThrow(new IllegalStateException("SELF_REQUEST"))
                .when(mockFriendshipRepository).sendFriendRequest("self-user");

        viewModel.addFriend("self-user");

        verify(mockFriendshipRepository, timeout(1000)).resolveUserId("self-user");
        verify(mockFriendshipRepository, timeout(1000)).sendFriendRequest("self-user");
        org.mockito.Mockito.verify(mockFriendshipRepository, after(300).never()).refreshPendingRequests();
    }

    @Test
    public void addFriend_ExistingPending_DoesNotRefreshPendingRequests() {
        when(mockFriendshipRepository.resolveUserId("pending-user")).thenReturn("pending-user");
        org.mockito.Mockito.doThrow(new IllegalStateException("REQUEST_PENDING"))
                .when(mockFriendshipRepository).sendFriendRequest("pending-user");

        viewModel.addFriend("pending-user");

        verify(mockFriendshipRepository, timeout(1000)).sendFriendRequest("pending-user");
        org.mockito.Mockito.verify(mockFriendshipRepository, after(300).never()).refreshPendingRequests();
    }

    @Test
    public void addFriend_AlreadyFriends_DoesNotRefreshPendingRequests() {
        when(mockFriendshipRepository.resolveUserId("accepted-user")).thenReturn("accepted-user");
        org.mockito.Mockito.doThrow(new IllegalStateException("ALREADY_FRIENDS"))
                .when(mockFriendshipRepository).sendFriendRequest("accepted-user");

        viewModel.addFriend("accepted-user");

        verify(mockFriendshipRepository, timeout(1000)).sendFriendRequest("accepted-user");
        org.mockito.Mockito.verify(mockFriendshipRepository, after(300).never()).refreshPendingRequests();
    }

    @Test
    public void addFriend_RejectedBefore_CallsSendFriendRequestAgain() {
        when(mockFriendshipRepository.resolveUserId("rejected-user")).thenReturn("rejected-user");

        viewModel.addFriend("rejected-user");

        verify(mockFriendshipRepository, timeout(1000)).sendFriendRequest("rejected-user");
        verify(mockFriendshipRepository, timeout(1000)).refreshPendingRequests();
    }

    @Test
    public void deleteFriend_ValidFriend_CallsUnfriendApi() {
        Friend friendToDelete = new Friend(1, "user-huy", "Huy", "H", 0x111111, false);

        viewModel.deleteFriend(friendToDelete);

        verify(mockFriendshipRepository, timeout(1000)).unfriend("user-huy");
        verify(mockFriendshipRepository, timeout(1000)).refreshFriends();
        verify(mockFriendshipRepository, timeout(1000)).refreshPendingRequests();
    }

    // ==================== Group Tests ====================

    @Test
    public void init_CallsRepositoryGetGroups() {
        verify(mockGroupRepository).getGroups();
    }

    @Test
    public void createGroup_ValidData_CallsRepositoryCreateGroup() {
        // Arrange
        List<Friend> members = Arrays.asList(
                new Friend(1, "An", "A", 111, true),
                new Friend(2, "Bình", "B", 222, false)
        );

        // Act
        viewModel.createGroup("DevTeam", members);

        // Assert
        verify(mockGroupRepository, timeout(1000)).createGroup(eq("DevTeam"), eq(members));
        verify(mockGroupRepository, timeout(1000)).refreshGroups();
    }

    @Test
    public void handleGroupAction_OwnerGroup_CallsDisbandGroup() {
        // Arrange
        Group ownedGroup = new Group(1, "My Group", "M", 0xFF00FF, new ArrayList<>(), true);

        // Act
        viewModel.handleGroupAction(ownedGroup);

        // Assert
        verify(mockGroupRepository, timeout(1000)).disbandGroup(ownedGroup);
        verify(mockGroupRepository, timeout(1000)).refreshGroups();
    }

    @Test
    public void handleGroupAction_NonOwnerGroup_CallsLeaveGroup() {
        // Arrange
        Group otherGroup = new Group(2, "Their Group", "T", 0x00FF00, new ArrayList<>(), false);

        // Act
        viewModel.handleGroupAction(otherGroup);

        // Assert
        verify(mockGroupRepository, timeout(1000)).leaveGroup(otherGroup);
        verify(mockGroupRepository, timeout(1000)).refreshGroups();
    }

    @Test
    public void submitAiTripDraftQuery_success_PublishesSuccessState() {
        Place place = new Place("place-1", "Museum", "District 1", 4.6, new Location(10.77, 106.70));
        AiTripDraftStop stop = new AiTripDraftStop(
                "place-1",
                place,
                90,
                "Visit gallery",
                "2026-05-01T09:00:00Z"
        );
        AiTripDraft draft = new AiTripDraft("Culture day", "City museums and cafés", Collections.singletonList(stop));
        MutableLiveData<AiTripDraftResult> result = new MutableLiveData<>(
                new AiTripDraftResult(draft, Collections.singletonList(place), Collections.emptyList(), null)
        );
        when(mockChatRepository.draftTripFromQuery("plan me a museum day")).thenReturn(result);

        viewModel.submitAiTripDraftQuery("plan me a museum day");

        verify(mockChatRepository).draftTripFromQuery("plan me a museum day");
        SocialViewModel.AiTripDrafterUiState state = viewModel.getAiTripDrafterUiState().getValue();
        org.junit.Assert.assertTrue(state instanceof SocialViewModel.AiTripDrafterUiState.Success);
        SocialViewModel.AiTripDrafterUiState.Success success =
                (SocialViewModel.AiTripDrafterUiState.Success) state;
        org.junit.Assert.assertEquals("Culture day", success.getTitle());
        org.junit.Assert.assertEquals("City museums and cafés", success.getDescription());
        org.junit.Assert.assertEquals(1, success.getStops().size());
    }

    @Test
    public void submitAiTripDraftQuery_failure_PublishesErrorState() {
        MutableLiveData<AiTripDraftResult> result = new MutableLiveData<>(
                new AiTripDraftResult(
                        new AiTripDraft("", "", Collections.emptyList()),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        "AI_FAILURE"
                )
        );
        when(mockChatRepository.draftTripFromQuery("bad")).thenReturn(result);

        viewModel.submitAiTripDraftQuery("bad");

        SocialViewModel.AiTripDrafterUiState state = viewModel.getAiTripDrafterUiState().getValue();
        org.junit.Assert.assertTrue(state instanceof SocialViewModel.AiTripDrafterUiState.Error);
        SocialViewModel.AiTripDrafterUiState.Error error =
                (SocialViewModel.AiTripDrafterUiState.Error) state;
        org.junit.Assert.assertEquals(SocialViewModel.AI_DRAFT_FAILED_MESSAGE, error.getErrorCode());
        org.junit.Assert.assertEquals("AI_FAILURE", error.getFailureCode());
    }

    @Test
    public void saveCurrentAiDraftTrip_withValidStop_CallsRepositorySaveDraftTrip() {
        Place place = new Place("place-1", "Museum", "District 1", 4.6, new Location(10.77, 106.70));
        AiTripDraftStop stop = new AiTripDraftStop(
                "place-1",
                place,
                90,
                "Visit gallery",
                "2026-05-01T09:00:00Z"
        );
        AiTripDraft draft = new AiTripDraft("Culture day", "City museums and cafés", Collections.singletonList(stop));
        MutableLiveData<AiTripDraftResult> result = new MutableLiveData<>(
                new AiTripDraftResult(draft, Collections.singletonList(place), Collections.emptyList(), null)
        );
        when(mockChatRepository.draftTripFromQuery("plan me a museum day")).thenReturn(result);
        doAnswer(invocation -> {
            ITripRepository.OperationCallback callback = invocation.getArgument(7);
            callback.onComplete(true);
            return null;
        }).when(mockTripRepository).saveDraftTrip(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyLong(),
                anyLong(),
                anyList(),
                any()
        );

        viewModel.submitAiTripDraftQuery("plan me a museum day");
        viewModel.saveCurrentAiDraftTrip(null);

        verify(mockTripRepository, timeout(1000)).saveDraftTrip(
                anyString(),
                anyString(),
                eq("Culture day"),
                eq("City museums and cafés"),
                anyLong(),
                anyLong(),
                anyList(),
                any()
        );
        org.junit.Assert.assertEquals(
                SocialViewModel.AI_DRAFT_SAVE_SUCCESS_MESSAGE,
                viewModel.getTripActionMessage().getValue()
        );
    }

    @Test
    public void saveCurrentAiDraftTrip_whenAllStopsInvalid_PublishesSaveFailed() {
        Place placeWithoutLocation = new Place("place-1", "Museum", "District 1", 4.6, null);
        AiTripDraftStop stop = new AiTripDraftStop(
                "place-1",
                placeWithoutLocation,
                90,
                "Visit gallery",
                "2026-05-01T09:00:00Z"
        );
        AiTripDraft draft = new AiTripDraft("Culture day", "City museums and cafés", Collections.singletonList(stop));
        MutableLiveData<AiTripDraftResult> result = new MutableLiveData<>(
                new AiTripDraftResult(draft, Collections.singletonList(placeWithoutLocation), Collections.emptyList(), null)
        );
        when(mockChatRepository.draftTripFromQuery("plan me a museum day")).thenReturn(result);

        viewModel.submitAiTripDraftQuery("plan me a museum day");
        viewModel.saveCurrentAiDraftTrip(null);

        org.mockito.Mockito.verify(mockTripRepository, after(300).never()).saveDraftTrip(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyLong(),
                anyLong(),
                anyList(),
                any()
        );
        org.junit.Assert.assertEquals(
                SocialViewModel.AI_DRAFT_SAVE_FAILED_MESSAGE,
                viewModel.getTripActionMessage().getValue()
        );
    }
}
