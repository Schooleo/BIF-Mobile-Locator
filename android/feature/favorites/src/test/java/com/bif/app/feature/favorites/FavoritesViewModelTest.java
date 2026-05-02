package com.bif.app.feature.favorites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.bif.app.domain.model.Favorite;
import com.bif.app.domain.repository.IFavoriteRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class FavoritesViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private IFavoriteRepository favoriteRepository;

    private FavoritesViewModel viewModel;
    private MutableLiveData<List<Favorite>> allFavoritesLiveData;
    private MutableLiveData<List<Favorite>> searchFavoritesLiveData;

    @Before
    public void setUp() {
        allFavoritesLiveData = new MutableLiveData<>(Collections.emptyList());
        searchFavoritesLiveData = new MutableLiveData<>(Collections.emptyList());
        when(favoriteRepository.getAllFavorites()).thenReturn(allFavoritesLiveData);
        when(favoriteRepository.searchFavorites(anyString())).thenReturn(searchFavoritesLiveData);
        when(favoriteRepository.isOnline()).thenReturn(true);

        viewModel = new FavoritesViewModel(favoriteRepository);
        // Activating MediatorLiveData triggers the initial switchMap → getAllFavorites
        viewModel.favorites.observeForever(ignored -> { });
    }

    // ─── init ──────────────────────────────────────────────────────────────────

    @Test
    public void init_whenCreated_callsGetAllFavorites() {
        // Activating the LiveData (done in setUp) triggers getAllFavorites via switchMap
        verify(favoriteRepository, atLeastOnce()).getAllFavorites();
    }

    @Test
    public void init_repositoryReturnsItems_favoritesListIsPopulated() {
        // Arrange
        Favorite home = makeFavorite("fav-1", "Home", "123 Main St", 5);
        Favorite work = makeFavorite("fav-2", "Work", "456 Corp Blvd", 4);

        // Act
        allFavoritesLiveData.setValue(Arrays.asList(home, work));

        // Assert
        List<Favorite> result = viewModel.favorites.getValue();
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Home", result.get(0).name);
        assertEquals("Work", result.get(1).name);
    }

    @Test
    public void init_repositoryReturnsEmptyList_favoritesIsEmpty() {
        // setUp already emits an empty list from allFavoritesLiveData
        List<Favorite> result = viewModel.favorites.getValue();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ─── filterFavorites ───────────────────────────────────────────────────────

    @Test
    public void filterFavorites_emptyQuery_callsGetAllFavorites() {
        // Arrange: apply a search first, then clear it
        viewModel.filterFavorites("cafe");
        verify(favoriteRepository).searchFavorites("cafe");

        // Act
        viewModel.filterFavorites("");

        // Assert: getAllFavorites must have been used (at least for init + after clear)
        verify(favoriteRepository, atLeastOnce()).getAllFavorites();
        // searchFavorites must NOT be called a second time for the empty query
        verify(favoriteRepository, never()).searchFavorites("");
    }

    @Test
    public void filterFavorites_blankQuery_doesNotCallSearchFavorites() {
        // Act
        viewModel.filterFavorites("   ");

        // Assert: whitespace-only query is treated as empty → no search call
        verify(favoriteRepository, never()).searchFavorites(anyString());
    }

    @Test
    public void filterFavorites_nonEmptyQuery_callsSearchFavorites() {
        // Act
        viewModel.filterFavorites("cafe");

        // Assert
        verify(favoriteRepository).searchFavorites("cafe");
    }

    @Test
    public void filterFavorites_nonEmptyQuery_favoritesUpdatedFromSearchResults() {
        // Arrange
        Favorite cafe = makeFavorite("fav-3", "Café Central", "789 Brew Lane", 4);
        searchFavoritesLiveData.setValue(Collections.singletonList(cafe));

        // Act
        viewModel.filterFavorites("cafe");

        // Assert
        List<Favorite> result = viewModel.favorites.getValue();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Café Central", result.get(0).name);
    }

    @Test
    public void filterFavorites_nullQuery_doesNotCallSearchFavorites() {
        // Act
        viewModel.filterFavorites(null);

        // Assert: null treated as empty → getAllFavorites, never searchFavorites
        verify(favoriteRepository, never()).searchFavorites(anyString());
        verify(favoriteRepository, atLeastOnce()).getAllFavorites();
    }

    @Test
    public void filterFavorites_switchFromQueryToEmpty_favoritesShowsAllItems() {
        // Arrange: activate a search filter first
        viewModel.filterFavorites("gym");
        verify(favoriteRepository).searchFavorites("gym");

        Favorite home = makeFavorite("fav-1", "Home", "123 Main St", 5);
        Favorite gym = makeFavorite("fav-4", "Gym Plus", "321 Fitness Rd", 3);
        allFavoritesLiveData.setValue(Arrays.asList(home, gym));

        // Act: reset the filter
        viewModel.filterFavorites("");

        // Assert: all items now visible
        List<Favorite> result = viewModel.favorites.getValue();
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    public void filterFavorites_multipleSequentialQueries_eachCallsSearchFavorites() {
        // Act
        viewModel.filterFavorites("home");
        viewModel.filterFavorites("work");
        viewModel.filterFavorites("gym");

        // Assert: each distinct non-empty query causes a search call
        verify(favoriteRepository).searchFavorites("home");
        verify(favoriteRepository).searchFavorites("work");
        verify(favoriteRepository).searchFavorites("gym");
    }

    // ─── removeFavoriteItem ────────────────────────────────────────────────────

    @Test
    public void removeFavoriteItem_existingFavorite_callsRepositoryDelete() {
        // Arrange
        Favorite toRemove = makeFavorite("fav-1", "Home", "123 Main St", 5);

        // Act
        viewModel.removeFavoriteItem(toRemove);

        // Assert
        verify(favoriteRepository).deleteFavorite(toRemove);
    }

    @Test
    public void removeFavoriteItem_callsDeleteWithExactSameObject() {
        // Arrange
        Favorite library = makeFavorite("fav-42", "Library", "789 Book Ave", 5);

        // Act
        viewModel.removeFavoriteItem(library);

        // Assert: the repository receives the exact same object
        ArgumentCaptor<Favorite> captor = ArgumentCaptor.forClass(Favorite.class);
        verify(favoriteRepository).deleteFavorite(captor.capture());
        assertEquals("fav-42", captor.getValue().id);
        assertEquals("Library", captor.getValue().name);
        assertEquals("789 Book Ave", captor.getValue().address);
    }

    @Test
    public void removeFavoriteItem_calledMultipleTimes_callsRepositoryEachTime() {
        // Arrange
        Favorite fav1 = makeFavorite("fav-1", "Cafe A", "Addr 1", 3);
        Favorite fav2 = makeFavorite("fav-2", "Cafe B", "Addr 2", 4);

        // Act
        viewModel.removeFavoriteItem(fav1);
        viewModel.removeFavoriteItem(fav2);

        // Assert
        verify(favoriteRepository).deleteFavorite(fav1);
        verify(favoriteRepository).deleteFavorite(fav2);
    }

    @Test
    public void refreshFavorites_whenRepositoryOffline_clearsSyncMessageAndStopsSyncing() {
        doAnswer(invocation -> {
            IFavoriteRepository.SyncCallback callback = invocation.getArgument(0);
            callback.onOffline();
            return null;
        }).when(favoriteRepository).refreshFavorites(any(IFavoriteRepository.SyncCallback.class));

        viewModel.refreshFavorites();

        assertEquals(Boolean.FALSE, viewModel.isSyncing.getValue());
        assertEquals("", viewModel.syncMessage.getValue());
    }

    @Test
    public void refreshFavorites_whenRepositoryError_usesGenericErrorKey() {
        doAnswer(invocation -> {
            IFavoriteRepository.SyncCallback callback = invocation.getArgument(0);
            callback.onError(null);
            return null;
        }).when(favoriteRepository).refreshFavorites(any(IFavoriteRepository.SyncCallback.class));

        viewModel.refreshFavorites();

        assertEquals(Boolean.FALSE, viewModel.isSyncing.getValue());
        assertEquals(IFavoriteRepository.ERROR_REFRESH_FAILED, viewModel.syncMessage.getValue());
    }

    @Test
    public void refreshFavoritesIfStale_afterOfflineResult_doesNotRetryImmediately() {
        when(favoriteRepository.isOnline()).thenReturn(true);
        doAnswer(invocation -> {
            IFavoriteRepository.SyncCallback callback = invocation.getArgument(0);
            callback.onOffline();
            return null;
        }).when(favoriteRepository).refreshFavorites(any(IFavoriteRepository.SyncCallback.class));

        viewModel.refreshFavorites();
        viewModel.refreshFavoritesIfStale();

        verify(favoriteRepository, org.mockito.Mockito.times(1))
                .refreshFavorites(any(IFavoriteRepository.SyncCallback.class));
    }

    @Test
    public void refreshFavorites_whenOffline_skipsRepositoryAndStaysSilent() {
        when(favoriteRepository.isOnline()).thenReturn(false);

        viewModel.refreshFavorites();

        verify(favoriteRepository, never()).refreshFavorites(any(IFavoriteRepository.SyncCallback.class));
        assertEquals(Boolean.FALSE, viewModel.isSyncing.getValue());
        assertEquals("", viewModel.syncMessage.getValue());
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    private Favorite makeFavorite(String id, String name, String address, int rating) {
        Favorite f = new Favorite();
        f.id = id;
        f.name = name;
        f.address = address;
        f.rating = rating;
        return f;
    }
}
