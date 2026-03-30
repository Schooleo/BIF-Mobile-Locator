package com.bif.app.feature.social;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.bif.app.domain.model.ChatMessage;
import com.bif.app.domain.model.TripStop;
import com.bif.app.domain.repository.IChatRepository;
import com.bif.app.domain.repository.ITripRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ChatViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private IChatRepository mockChatRepository;

    @Mock
    private ITripRepository mockTripRepository;

    private ChatViewModel viewModel;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        
        when(mockChatRepository.getMessagesByGroup(any())).thenReturn(new MutableLiveData<>());
        when(mockTripRepository.getTripsByGroup(any())).thenReturn(new MutableLiveData<>());
        
        viewModel = new ChatViewModel(mockChatRepository, mockTripRepository);
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
}
