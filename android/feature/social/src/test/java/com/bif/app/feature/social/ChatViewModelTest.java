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
import com.bif.app.domain.model.AiTripDraft;
import com.bif.app.domain.model.AiTripDraftResult;
import com.bif.app.domain.model.AiTripDraftStop;
import com.bif.app.domain.model.ChatMessage;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Place;
import com.bif.app.domain.model.TripStop;
import com.bif.app.domain.repository.IChatRepository;
import com.bif.app.domain.repository.ITripRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class ChatViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private IChatRepository mockChatRepository;

    @Mock
    private ITripRepository mockTripRepository;

    @Mock
    private NetworkMonitor networkMonitor;

    private MutableLiveData<Boolean> connectivityLiveData;

    private ChatViewModel viewModel;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        connectivityLiveData = new MutableLiveData<>(true);
        
        when(mockChatRepository.getMessagesByGroup(any())).thenReturn(new MutableLiveData<>());
        when(mockTripRepository.getTripsByGroup(any())).thenReturn(new MutableLiveData<>());
        when(networkMonitor.isOnline()).thenReturn(true);
        when(networkMonitor.observeConnectivity()).thenReturn(connectivityLiveData);
        
        viewModel = new ChatViewModel(mockChatRepository, mockTripRepository, networkMonitor);
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
    }

    @Test
    public void sendMessage_AiModeFailureCode_ShowsSnackbarAndDoesNotInsertCard() {
        viewModel.init("group1", "Group 1", "u1");
        AiTripDraft placeholderDraft = new AiTripDraft("", "", Collections.emptyList());
        MutableLiveData<AiTripDraftResult> aiResult = new MutableLiveData<>(
            new AiTripDraftResult(placeholderDraft, Collections.emptyList(), Collections.emptyList(), "AI_FAILURE")
        );
        when(mockChatRepository.draftTripFromQuery("draft me a weekend trip")).thenReturn(aiResult);

        AtomicReference<String> snackbar = new AtomicReference<>();
        viewModel.getSnackbarMessage().observeForever(snackbar::set);

        viewModel.enterAiDraftMode();
        viewModel.sendMessage("draft me a weekend trip");

        verify(mockChatRepository).draftTripFromQuery("draft me a weekend trip");
        verify(mockChatRepository, org.mockito.Mockito.never()).insertLocalMessage(any());
        String snackbarMessage = snackbar.get();
        assertTrue(snackbarMessage != null && snackbarMessage.contains("AI_FAILURE"));
    }

    @Test
    public void sendMessage_AiModeSuccess_InsertsTripCreatedCardLocally() {
        viewModel.init("group1", "Group 1", "u1");

        Place place = new Place("place1", "Cafe", "Addr", 4.5, new Location(10.0, 20.0));
        AiTripDraftStop stop = new AiTripDraftStop("place1", place, 90, "Morning coffee");
        AiTripDraft draft = new AiTripDraft("Weekend plan", "Relaxed trip", Collections.singletonList(stop));
        MutableLiveData<AiTripDraftResult> aiResult = new MutableLiveData<>(
                new AiTripDraftResult(draft, Collections.singletonList(place), Collections.emptyList(), null)
        );
        when(mockChatRepository.draftTripFromQuery("draft me a weekend trip")).thenReturn(aiResult);

        viewModel.enterAiDraftMode();
        viewModel.sendMessage("draft me a weekend trip");

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(mockChatRepository).insertLocalMessage(captor.capture());

        ChatMessage message = captor.getValue();
        assertEquals("TRIP_CREATED_CARD", message.getType());
        assertTrue(message.getContent().contains("\"isSaved\":false"));
        assertTrue(message.getContent().contains("candidatePlaces"));
        assertTrue(message.getContent().contains("\"latitude\":10.0"));
        assertTrue(message.getContent().contains("\"longitude\":20.0"));
    }

        @Test
        public void onSaveTripCard_persistsDraftBeforeUpdatingSavedIds() {
        viewModel.init("group1", "Group 1", "u1");

        Place placeWithoutLocation = new Place("place1", "Cafe", "Addr", 4.5, null);
        AiTripDraftStop stop = new AiTripDraftStop("place1", placeWithoutLocation, 90, "Morning coffee");
        AiTripDraft draft = new AiTripDraft("Weekend plan", "Relaxed trip", Collections.singletonList(stop));
        MutableLiveData<AiTripDraftResult> aiResult = new MutableLiveData<>(
            new AiTripDraftResult(draft, Collections.singletonList(placeWithoutLocation), Collections.emptyList(), null)
        );
        when(mockChatRepository.draftTripFromQuery("draft me a weekend trip")).thenReturn(aiResult);

        doAnswer(invocation -> {
            ITripRepository.OperationCallback callback = invocation.getArgument(7);
            callback.onComplete(true);
            return null;
        }).when(mockTripRepository).saveDraftTrip(
            anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyList(), any());

        viewModel.enterAiDraftMode();
        viewModel.sendMessage("draft me a weekend trip");

        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(mockChatRepository).insertLocalMessage(messageCaptor.capture());

        String payload = messageCaptor.getValue().getContent();
        String marker = "\"tripId\":\"";
        int start = payload.indexOf(marker);
        assertTrue(start >= 0);
        int valueStart = start + marker.length();
        int valueEnd = payload.indexOf('"', valueStart);
        assertTrue(valueEnd > valueStart);
        String draftTripId = payload.substring(valueStart, valueEnd);

        viewModel.onSaveTripCard(draftTripId);

        verify(mockTripRepository).saveDraftTrip(
            eq(draftTripId),
            eq("group1"),
            eq("Weekend plan"),
            eq("Relaxed trip"),
            anyLong(),
            eq(0L),
            anyList(),
            any());

        Set<String> saved = viewModel.getSavedTripCardIds().getValue();
        assertTrue(saved != null && saved.contains(draftTripId));
    }
}
