package com.bif.app.feature.social;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.bif.app.data.sync.core.NetworkMonitor;
import com.bif.app.domain.model.AiPlaceSuggestionResult;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Place;
import com.bif.app.domain.repository.IPlaceRepository;
import com.bif.app.domain.repository.ITripRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Collections;

@RunWith(MockitoJUnitRunner.class)
public class AddTripStopViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private IPlaceRepository placeRepository;

    @Mock
    private ITripRepository tripRepository;

    @Mock
    private NetworkMonitor networkMonitor;

    private MutableLiveData<Boolean> connectivityLiveData;
    private AddTripStopViewModel viewModel;

    @Before
    public void setUp() {
        connectivityLiveData = new MutableLiveData<>(true);
        when(networkMonitor.observeConnectivity()).thenReturn(connectivityLiveData);
        when(networkMonitor.isOnline()).thenReturn(true);

        Mockito.lenient().when(placeRepository.searchPlaces(anyString()))
                .thenReturn(new MutableLiveData<>(Collections.emptyList()));
        Mockito.lenient().when(placeRepository.suggestPlacesFromQuery(anyString()))
                .thenReturn(new MutableLiveData<>(new AiPlaceSuggestionResult(
                        new ArrayList<>(),
                        new ArrayList<>(),
                        null)));

        viewModel = new AddTripStopViewModel(placeRepository, tripRepository, networkMonitor);
    }

    @Test
    public void toggleAiMode_offlineForcesOff() {
        when(networkMonitor.isOnline()).thenReturn(false);

        viewModel.toggleAiMode();

        assertFalse(Boolean.TRUE.equals(viewModel.getAiModeEnabled().getValue()));
        assertFalse(Boolean.TRUE.equals(viewModel.getAiToggleEnabled().getValue()));
    }

    @Test
    public void toggleAiMode_onlineUpdatesHint() {
        when(networkMonitor.isOnline()).thenReturn(true);

        viewModel.toggleAiMode();

        assertTrue(Boolean.TRUE.equals(viewModel.getAiModeEnabled().getValue()));
        assertEquals("Describe your vibe...", viewModel.getSearchHint().getValue());
    }

    @Test
    public void aiSearch_failureCodeReturnsEmptyState() {
        viewModel.toggleAiMode();

        MutableLiveData<AiPlaceSuggestionResult> aiResult = new MutableLiveData<>(
                new AiPlaceSuggestionResult(Collections.emptyList(),
                        Collections.emptyList(), "RATE_LIMITED"));
        when(placeRepository.suggestPlacesFromQuery("green park")).thenReturn(aiResult);

        viewModel.search("green park");

        assertTrue(viewModel.getSearchState().getValue() instanceof AddTripStopViewModel.SearchState.Empty);
    }

    @Test
    public void aiSearch_offlineFailureCodeDisablesAi() {
        viewModel.toggleAiMode();

        MutableLiveData<AiPlaceSuggestionResult> aiResult = new MutableLiveData<>(
                new AiPlaceSuggestionResult(Collections.emptyList(),
                        Collections.emptyList(), "OFFLINE"));
        when(placeRepository.suggestPlacesFromQuery("hidden cafe")).thenReturn(aiResult);

        viewModel.search("hidden cafe");

        assertFalse(Boolean.TRUE.equals(viewModel.getAiModeEnabled().getValue()));
        assertFalse(Boolean.TRUE.equals(viewModel.getAiToggleEnabled().getValue()));
        assertTrue(viewModel.getSearchState().getValue() instanceof AddTripStopViewModel.SearchState.Empty);
    }

    @Test
    public void addStopToTrip_usesTripRepository() {
        viewModel.setTripId("trip-1");

        Place place = new Place("p1", "Central Park", "NY", 4.5,
                new Location(10.0, 106.0));
        AddTripStopViewModel.StopSearchResultItem item =
                new AddTripStopViewModel.StopSearchResultItem(place, 2);

        viewModel.addStopToTrip(item, 1_700_000_000_000L);

        verify(tripRepository).addStopToTrip(Mockito.eq("trip-1"), Mockito.any());
    }
}
