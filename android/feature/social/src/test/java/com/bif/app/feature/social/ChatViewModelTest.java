package com.bif.app.feature.social;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.bif.app.data.sync.core.NetworkMonitor;
import com.bif.app.domain.model.AiPlaceSuggestion;
import com.bif.app.domain.model.AiPlaceSuggestionResult;
import com.bif.app.domain.model.AiTripDraft;
import com.bif.app.domain.model.AiTripDraftResult;
import com.bif.app.domain.model.AiTripDraftStop;
import com.bif.app.domain.model.ChatMessage;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Place;
import com.bif.app.domain.model.TripPlan;
import com.bif.app.domain.model.TripStop;
import com.bif.app.domain.repository.IChatRepository;
import com.bif.app.domain.repository.IPlaceRepository;
import com.bif.app.domain.repository.ITripRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Arrays;

public class ChatViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private IChatRepository mockChatRepository;

    @Mock
    private ITripRepository mockTripRepository;

    @Mock
    private IPlaceRepository mockPlaceRepository;

    @Mock
    private NetworkMonitor networkMonitor;

    private MutableLiveData<Boolean> connectivityLiveData;
    private MutableLiveData<List<TripPlan>> tripsLiveData;

    private ChatViewModel viewModel;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        connectivityLiveData = new MutableLiveData<>(true);
        tripsLiveData = new MutableLiveData<>(Collections.emptyList());
        
        when(mockChatRepository.getMessagesByGroup(any())).thenReturn(new MutableLiveData<>());
        when(mockTripRepository.getTripsByGroup(any())).thenReturn(tripsLiveData);
        when(networkMonitor.isOnline()).thenReturn(true);
        when(networkMonitor.observeConnectivity()).thenReturn(connectivityLiveData);
        
        when(mockPlaceRepository.suggestPlacesFromQuery(anyString()))
                .thenReturn(new MutableLiveData<>(new AiPlaceSuggestionResult(
                        Collections.emptyList(),
                        Collections.emptyList(),
                        "AI_FAILURE"
                )));

        viewModel = new ChatViewModel(mockChatRepository, mockPlaceRepository,
                mockTripRepository, networkMonitor);
    }

    @Test
    public void init_CallsRefreshOnRepositories() {
        viewModel.init("group123", "Travel Group", "user123");

        assertEquals("Travel Group", viewModel.getGroupName());
        verify(mockChatRepository).refreshMessages("group123");
        verify(mockTripRepository).refreshTrips("group123");
    }

    @Test
    public void sendMessage_ValidContent_CallsRepositorySendMessage() {
        viewModel.init("group1", "Group 1", "u1");

        viewModel.sendMessage("Hello World");

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(mockChatRepository).sendMessage(captor.capture());

        ChatMessage msg = captor.getValue();
        assertEquals("group1", msg.getGroupId());
        assertEquals("u1", msg.getSenderUserId());
        assertEquals("Hello World", msg.getContent());
        assertEquals("TEXT", msg.getType());
    }

    @Test
    public void shareLocation_CallsRepositorySendLocationMessage() {
        viewModel.init("group1", "Group 1", "u1");

        viewModel.shareLocation(37.4220936, -122.083922, "Googleplex");

        verify(mockChatRepository).sendLocationMessage(
                eq("group1"), eq("u1"), eq(37.4220936), eq(-122.083922), eq("Googleplex")
        );
    }

    @Test
    public void addSharedLocationToTrip_LocationMessage_CallsRepositoryAddStop() {
        // Arrange
        ChatMessage msg = new ChatMessage(
                "msg1", "g1", "u1", null, "Googleplex", "LOCATION", 
                123456L, "c1", 37.422, -122.084, "Googleplex", false, true
        );

        // Act
        viewModel.addSharedLocationToTrip("trip1", msg);

        // Assert
        ArgumentCaptor<TripStop> captor = ArgumentCaptor.forClass(TripStop.class);
        verify(mockTripRepository).addStopToTrip(eq("trip1"), captor.capture());

        TripStop stop = captor.getValue();
        assertEquals("Googleplex", stop.getTitle());
        assertEquals(37.422, stop.getLatitude(), 0.0001);
        assertEquals(-122.084, stop.getLongitude(), 0.0001);
    }

    @Test
    public void refreshMessages_CallsRepositoryRefresh() {
        viewModel.init("group1", "Group 1", "u1");

        viewModel.refreshMessages();

        verify(mockChatRepository, org.mockito.Mockito.times(2)).refreshMessages("group1"); // Twice total: init + here
        verify(mockTripRepository, org.mockito.Mockito.times(2)).refreshTrips("group1");
    }

    @Test
    public void sendMessage_AiModeFailureCode_InsertsAiErrorMessageAndDoesNotInsertCard() {
        viewModel.init("group1", "Group 1", "u1");
        AiTripDraft placeholderDraft = new AiTripDraft("", "", Collections.emptyList());
        MutableLiveData<AiTripDraftResult> aiResult = new MutableLiveData<>(
            new AiTripDraftResult(placeholderDraft, Collections.emptyList(), Collections.emptyList(), "AI_FAILURE")
        );
        when(mockChatRepository.draftTripFromQuery("draft me a weekend trip")).thenReturn(aiResult);

        viewModel.enterAiDraftMode();
        viewModel.sendMessage("draft me a weekend trip");

        verify(mockChatRepository).draftTripFromQuery("draft me a weekend trip");

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(mockChatRepository, org.mockito.Mockito.times(2)).insertLocalMessage(captor.capture());
        List<ChatMessage> inserted = captor.getAllValues();

        assertEquals("Drafting a trip...", inserted.get(0).getContent());
        assertTrue(inserted.get(1).getContent().contains("Errors drafting trip: AI_FAILURE"));
    }

    @Test
    public void sendMessage_AiModeSuccess_SendsTripCreatedCardToRepository() {
        viewModel.init("group1", "Group 1", "u1");

        Place place = new Place("place1", "Cafe", "Addr", 4.5, new Location(10.0, 20.0));
        AiTripDraftStop stop = new AiTripDraftStop(
            "place1",
            place,
            90,
            "Morning coffee",
            "2026-01-01T09:00:00Z");
        AiTripDraft draft = new AiTripDraft("Weekend plan", "Relaxed trip", Collections.singletonList(stop));
        MutableLiveData<AiTripDraftResult> aiResult = new MutableLiveData<>(
                new AiTripDraftResult(draft, Collections.singletonList(place), Collections.emptyList(), null)
        );
        when(mockChatRepository.draftTripFromQuery("draft me a weekend trip")).thenReturn(aiResult);

        viewModel.enterAiDraftMode();
        viewModel.sendMessage("draft me a weekend trip");

        ArgumentCaptor<ChatMessage> localCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(mockChatRepository, org.mockito.Mockito.times(2)).insertLocalMessage(localCaptor.capture());
        List<ChatMessage> localInserted = localCaptor.getAllValues();
        assertEquals("Drafting a trip...", localInserted.get(0).getContent());
        assertEquals("Drafted trip", localInserted.get(1).getContent());

        ArgumentCaptor<ChatMessage> sendCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(mockChatRepository).sendMessage(sendCaptor.capture());
        ChatMessage message = sendCaptor.getValue();
        assertEquals("TRIP_CREATED_CARD", message.getType());
        assertTrue(message.getContent().contains("\"isSaved\":false"));
        assertTrue(message.getContent().contains("\"currentTripId\""));
        assertTrue(message.getContent().contains("candidatePlaces"));
        assertTrue(message.getContent().contains("\"latitude\":10.0"));
        assertTrue(message.getContent().contains("\"longitude\":20.0"));
        assertTrue(message.getContent().contains("\"plannedDateTime\":\"2026-01-01T09:00:00Z\""));
        assertEquals("u1", message.getSenderUserId());
    }

    @Test
    public void sendMessage_AiSuggestModeSuccess_SendsSuggestedPlacesCardToRepository() {
        viewModel.init("group1", "Group 1", "u1");

        Place place = new Place("place1", "Cafe", "Addr", 4.5, new Location(10.0, 20.0));
        AiPlaceSuggestion suggestion = new AiPlaceSuggestion(place, 0);
        MutableLiveData<AiPlaceSuggestionResult> aiResult = new MutableLiveData<>(
                new AiPlaceSuggestionResult(Collections.singletonList(suggestion),
                        Collections.emptyList(), null)
        );
        when(mockPlaceRepository.suggestPlacesFromQuery("best cafes"))
                .thenReturn(aiResult);

        viewModel.enterAiSuggestPlacesMode();
        viewModel.sendMessage("best cafes");

        ArgumentCaptor<ChatMessage> localCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(mockChatRepository, org.mockito.Mockito.times(2)).insertLocalMessage(localCaptor.capture());
        List<ChatMessage> inserted = localCaptor.getAllValues();
        assertEquals("Suggesting places...", inserted.get(0).getContent());
        assertEquals("Suggested places", inserted.get(1).getContent());

        ArgumentCaptor<ChatMessage> sendCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(mockChatRepository).sendMessage(sendCaptor.capture());
        ChatMessage message = sendCaptor.getValue();
        assertEquals("AI_SUGGESTED_PLACES_CARD", message.getType());
        assertTrue(message.getContent().contains("\"places\""));
        assertTrue(message.getContent().contains("\"name\":\"Cafe\""));
        assertTrue(message.getContent().contains("\"latitude\":10.0"));
        assertEquals("u1", message.getSenderUserId());
    }

    @Test
    public void sendMessage_AiSuggestModeFailure_InsertsAiErrorMessageAndDoesNotInsertCard() {
        viewModel.init("group1", "Group 1", "u1");

        MutableLiveData<AiPlaceSuggestionResult> aiResult = new MutableLiveData<>(
                new AiPlaceSuggestionResult(Collections.emptyList(),
                        Collections.emptyList(), "OFFLINE")
        );
        when(mockPlaceRepository.suggestPlacesFromQuery("hidden gems"))
                .thenReturn(aiResult);

        viewModel.enterAiSuggestPlacesMode();
        viewModel.sendMessage("hidden gems");

        verify(mockPlaceRepository).suggestPlacesFromQuery("hidden gems");

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(mockChatRepository, org.mockito.Mockito.times(2)).insertLocalMessage(captor.capture());
        List<ChatMessage> inserted = captor.getAllValues();

        assertEquals("Suggesting places...", inserted.get(0).getContent());
        assertTrue(inserted.get(1).getContent().contains("Errors suggesting places: OFFLINE"));
    }

    @Test
    public void onSaveTripCardAsNew_persistsDraftAndMarksSaved() {
        TripPlan currentTrip = new TripPlan(
                "trip-current",
                "group1",
                "Current",
                "",
                0L,
                0L,
                Collections.emptyList(),
                Arrays.asList("u1", "u2")
        );
        viewModel.init("group1", "Group 1", "u1");
        tripsLiveData.setValue(Collections.singletonList(currentTrip));
        assertTrue(viewModel.isCurrentUserHostForCurrentTrip());

        doAnswer(invocation -> {
            ITripRepository.OperationCallback callback = invocation.getArgument(7);
            callback.onComplete(true);
            return null;
        }).when(mockTripRepository).saveDraftTrip(
                anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyList(), any());

        String payload = "{"
                + "\"tripId\":\"ai-draft-123\","
                + "\"title\":\"Weekend plan\","
                + "\"summary\":\"Relaxed trip\","
                + "\"stops\":[{\"name\":\"Cafe\",\"address\":\"Addr\",\"note\":\"\",\"latitude\":10.0,\"longitude\":20.0}]"
                + "}";

        viewModel.onSaveTripCardAsNew("ai-draft-123", payload);
        String saveMessage = viewModel.getSnackbarMessage().getValue();
        assertTrue("save snackbar=" + saveMessage,
                saveMessage == null || saveMessage.contains("saved"));

        verify(mockTripRepository).saveDraftTrip(
                eq("ai-draft-123"),
                eq("group1"),
                eq("Weekend plan"),
                eq("Relaxed trip"),
                anyLong(),
                anyLong(),
                anyList(),
                any());

        Set<String> saved = viewModel.getSavedTripCardIds().getValue();
        assertTrue(saved != null && saved.contains("ai-draft-123"));
    }

    @Test
    public void onOverrideTripCard_usesCurrentTripId() {
        TripPlan currentTrip = new TripPlan(
                "trip-current",
                "group1",
                "Current",
                "",
                0L,
                0L,
                Collections.emptyList(),
                Arrays.asList("u1", "u2")
        );
        viewModel.init("group1", "Group 1", "u1");
        tripsLiveData.setValue(Collections.singletonList(currentTrip));
        assertTrue(viewModel.isCurrentUserHostForCurrentTrip());

        doAnswer(invocation -> {
            ITripRepository.OperationCallback callback = invocation.getArgument(7);
            callback.onComplete(true);
            return null;
        }).when(mockTripRepository).saveDraftTrip(
                anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyList(), any());

        String payload = "{"
                + "\"tripId\":\"ai-draft-321\","
                + "\"currentTripId\":\"trip-current\","
                + "\"title\":\"Updated plan\","
                + "\"summary\":\"Updated summary\","
                + "\"stops\":[{\"name\":\"Museum\",\"address\":\"Addr\",\"note\":\"\",\"latitude\":10.0,\"longitude\":20.0}]"
                + "}";

        viewModel.onOverrideTripCard("ai-draft-321", payload);
        String overrideMessage = viewModel.getSnackbarMessage().getValue();
        assertTrue("override snackbar=" + overrideMessage,
                overrideMessage == null || overrideMessage.contains("updated"));

        verify(mockTripRepository).saveDraftTrip(
                eq("trip-current"),
                eq("group1"),
                eq("Updated plan"),
                eq("Updated summary"),
                anyLong(),
                anyLong(),
                anyList(),
                any());
    }

    @Test
    public void onSaveTripCardAsNew_memberCannotSave() {
        TripPlan currentTrip = new TripPlan(
                "trip-current",
                "group1",
                "Current",
                "",
                0L,
                0L,
                Collections.emptyList(),
                Arrays.asList("host-user", "u2")
        );
        tripsLiveData.setValue(Collections.singletonList(currentTrip));
        viewModel.init("group1", "Group 1", "u1");

        String payload = "{"
                + "\"tripId\":\"ai-draft-123\","
                + "\"title\":\"Weekend plan\","
                + "\"summary\":\"Relaxed trip\","
                + "\"stops\":[{\"name\":\"Cafe\",\"address\":\"Addr\",\"note\":\"\",\"latitude\":10.0,\"longitude\":20.0}]"
                + "}";

        viewModel.onSaveTripCardAsNew("ai-draft-123", payload);

        org.mockito.Mockito.verify(mockTripRepository, org.mockito.Mockito.never()).saveDraftTrip(
                anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyList(), any());
        assertTrue(viewModel.getSnackbarMessage().getValue().contains("host"));
    }

    @Test
    public void onOverrideTripCard_stalePayloadTripId_fallsBackToCurrentTrip() {
        TripPlan currentTrip = new TripPlan(
                "trip-current",
                "group1",
                "Current",
                "",
                0L,
                0L,
                Collections.emptyList(),
                Arrays.asList("u1", "u2")
        );
        viewModel.init("group1", "Group 1", "u1");
        tripsLiveData.setValue(Collections.singletonList(currentTrip));

        doAnswer(invocation -> {
            ITripRepository.OperationCallback callback = invocation.getArgument(7);
            callback.onComplete(true);
            return null;
        }).when(mockTripRepository).saveDraftTrip(
                anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyList(), any());

        String payload = "{"
                + "\"tripId\":\"ai-draft-xyz\","
                + "\"currentTripId\":\"stale-trip-id\","
                + "\"title\":\"Updated plan\","
                + "\"summary\":\"Updated summary\","
                + "\"stops\":[{\"name\":\"Museum\",\"address\":\"Addr\",\"note\":\"\",\"latitude\":10.0,\"longitude\":20.0}]"
                + "}";

        viewModel.onOverrideTripCard("ai-draft-xyz", payload);

        verify(mockTripRepository).saveDraftTrip(
                eq("trip-current"),
                eq("group1"),
                eq("Updated plan"),
                eq("Updated summary"),
                anyLong(),
                anyLong(),
                anyList(),
                any());
    }
}
