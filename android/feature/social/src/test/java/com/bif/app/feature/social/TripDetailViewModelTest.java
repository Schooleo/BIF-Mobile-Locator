package com.bif.app.feature.social;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.bif.app.domain.model.ChatMessage;
import com.bif.app.domain.model.TripPlan;
import com.bif.app.domain.repository.IChatRepository;
import com.bif.app.domain.repository.ITripRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

public class TripDetailViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private ITripRepository mockTripRepository;

    @Mock
    private IChatRepository mockChatRepository;

    @Mock
    private Context appContext;

    @Mock
    private SharedPreferences sharedPreferences;

    @Mock
    private SharedPreferences.Editor sharedPreferencesEditor;

    private MutableLiveData<java.util.List<ChatMessage>> messagesLiveData;

    private TripDetailViewModel viewModel;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        MutableLiveData<TripPlan> tripLiveData = new MutableLiveData<>();
        messagesLiveData = new MutableLiveData<>();

        when(mockTripRepository.getTripById(anyString())).thenReturn(tripLiveData);
        when(mockChatRepository.getMessagesByGroup(anyString())).thenReturn(messagesLiveData);

        when(appContext.getSharedPreferences(eq("CHAT_READ_STATE"), eq(Context.MODE_PRIVATE)))
                .thenReturn(sharedPreferences);

        when(sharedPreferences.edit()).thenReturn(sharedPreferencesEditor);
        when(sharedPreferencesEditor.putLong(anyString(), anyLong())).thenReturn(sharedPreferencesEditor);

        viewModel = new TripDetailViewModel(mockTripRepository, mockChatRepository, appContext);
        viewModel.getHasUnreadGroupMessages().observeForever(value -> {
        });
    }

    @Test
    public void loadTrip_IncomingMessageAfterLastRead_ShowsUnread() {
        when(sharedPreferences.getLong(eq("group_last_read_trip-1"), eq(0L))).thenReturn(100L);

        viewModel.loadTrip("trip-1");
        messagesLiveData.setValue(Collections.singletonList(
                message("m1", "trip-1", false, 150L)
        ));

        assertEquals(Boolean.TRUE, viewModel.getHasUnreadGroupMessages().getValue());
    }

    @Test
    public void loadTrip_OnlyOutgoingMessages_HidesUnread() {
        when(sharedPreferences.getLong(eq("group_last_read_trip-1"), eq(0L))).thenReturn(0L);

        viewModel.loadTrip("trip-1");
        messagesLiveData.setValue(Collections.singletonList(
                message("m1", "trip-1", true, 150L)
        ));

        assertNotEquals(Boolean.TRUE, viewModel.getHasUnreadGroupMessages().getValue());
    }

    @Test
    public void markGroupChatReadNow_PersistsReadMarker_AndClearsUnread() {
        AtomicLong storedReadAt = new AtomicLong(100L);
        when(sharedPreferences.getLong(eq("group_last_read_trip-1"), eq(0L)))
                .thenAnswer(invocation -> storedReadAt.get());
        when(sharedPreferencesEditor.putLong(eq("group_last_read_trip-1"), anyLong()))
                .thenAnswer(invocation -> {
                    Long written = invocation.getArgument(1);
                    storedReadAt.set(written != null ? written : 0L);
                    return sharedPreferencesEditor;
                });

        viewModel.loadTrip("trip-1");
        messagesLiveData.setValue(Collections.singletonList(
                message("m1", "trip-1", false, 150L)
        ));
        assertEquals(Boolean.TRUE, viewModel.getHasUnreadGroupMessages().getValue());

        viewModel.markGroupChatReadNow();

        verify(sharedPreferencesEditor).putLong(eq("group_last_read_trip-1"), anyLong());
        verify(sharedPreferencesEditor).apply();
        assertNotEquals(Boolean.TRUE, viewModel.getHasUnreadGroupMessages().getValue());
    }

    @Test
    public void refreshTripContent_RefreshesTripAndChatSources() {
        viewModel.loadTrip("trip-1");

        viewModel.refreshTripContent();

        verify(mockTripRepository).refreshTrips("");
        verify(mockChatRepository).refreshMessages("trip-1");
    }

    private ChatMessage message(String id, String groupId, boolean isOutgoing, long sentAt) {
        return new ChatMessage(
                id,
                groupId,
                isOutgoing ? "me" : "friend",
                null,
                "hello",
                "TEXT",
                sentAt,
                "client-" + id,
                0.0,
                0.0,
                "",
                true,
                isOutgoing
        );
    }
}
