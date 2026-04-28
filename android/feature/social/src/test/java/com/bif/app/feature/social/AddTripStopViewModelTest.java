package com.bif.app.feature.social;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

        Mockito.lenient().when(placeRepository.searchPlaces(anyString(), any(Location.class)))
                .thenReturn(new MutableLiveData<>(Collections.emptyList()));
        Mockito.lenient().when(placeRepository.suggestPlacesFromQuery(
                        anyString(),
                        any(),
                        any(),
                        any()))
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

        assertNotEquals(Boolean.TRUE, viewModel.getAiModeEnabled().getValue());
        assertNotEquals(Boolean.TRUE, viewModel.getAiToggleEnabled().getValue());
    }

    @Test
    public void toggleAiMode_onlineUpdatesHint() {
        when(networkMonitor.isOnline()).thenReturn(true);

        viewModel.toggleAiMode();

        assertEquals(Boolean.TRUE, viewModel.getAiModeEnabled().getValue());
        assertEquals("Describe your vibe...", viewModel.getSearchHint().getValue());
    }

    @Test
    public void aiSearch_failureCodeReturnsEmptyState() {
        viewModel.toggleAiMode();

        MutableLiveData<AiPlaceSuggestionResult> aiResult = new MutableLiveData<>(
                new AiPlaceSuggestionResult(Collections.emptyList(),
                        Collections.emptyList(), "RATE_LIMITED"));
        when(placeRepository.suggestPlacesFromQuery(
                Mockito.eq("green park"),
                any(),
                any(),
                any())).thenReturn(aiResult);

        viewModel.search("green park");

        assertTrue(viewModel.getSearchState().getValue() instanceof AddTripStopViewModel.SearchState.Empty);
    }

    @Test
    public void aiSearch_offlineFailureCodeDisablesAi() {
        viewModel.toggleAiMode();

        MutableLiveData<AiPlaceSuggestionResult> aiResult = new MutableLiveData<>(
                new AiPlaceSuggestionResult(Collections.emptyList(),
                        Collections.emptyList(), "OFFLINE"));
        when(placeRepository.suggestPlacesFromQuery(
                Mockito.eq("hidden cafe"),
                any(),
                any(),
                any())).thenReturn(aiResult);

        viewModel.search("hidden cafe");

        assertNotEquals(Boolean.TRUE, viewModel.getAiModeEnabled().getValue());
        assertNotEquals(Boolean.TRUE, viewModel.getAiToggleEnabled().getValue());
        assertTrue(viewModel.getSearchState().getValue() instanceof AddTripStopViewModel.SearchState.Empty);
    }

    @Test
    public void aiSearch_usesConfiguredBiasContext() {
        viewModel.toggleAiMode();
        viewModel.setAiSearchBias(10.7769, 106.7009, "District 1, Ho Chi Minh City");

        MutableLiveData<AiPlaceSuggestionResult> aiResult = new MutableLiveData<>(
                new AiPlaceSuggestionResult(Collections.emptyList(),
                        Collections.emptyList(), "NO_RESULTS"));
        when(placeRepository.suggestPlacesFromQuery(
                Mockito.eq("coffee"),
                Mockito.eq(10.7769),
                Mockito.eq(106.7009),
                Mockito.eq("District 1, Ho Chi Minh City"))).thenReturn(aiResult);

        viewModel.search("coffee");

        verify(placeRepository).suggestPlacesFromQuery(
                "coffee",
                10.7769,
                106.7009,
                "District 1, Ho Chi Minh City");
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
