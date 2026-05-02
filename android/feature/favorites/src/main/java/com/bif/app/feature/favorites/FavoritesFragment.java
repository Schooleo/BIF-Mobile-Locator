package com.bif.app.feature.favorites;

import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import com.bif.app.core.utils.AppSnackbar;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bif.app.core.utils.UriUtils;
import com.bif.app.domain.model.Favorite;
import com.bif.app.domain.repository.IFavoriteRepository;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class FavoritesFragment extends Fragment
    implements FavoriteAdapter.OnFavoriteClickListener {

    private FavoritesViewModel viewModel;
    private FavoriteAdapter adapter;
    private RecyclerView rvFavorites;
    private TextView tvEmpty;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(FavoritesViewModel.class);
        return inflater.inflate(R.layout.fragment_favorites, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvFavorites = view.findViewById(R.id.rv_favorites);
        tvEmpty = view.findViewById(R.id.tv_empty);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh);
        EditText etSearch = view.findViewById(R.id.et_search);

        adapter = new FavoriteAdapter(this);
        rvFavorites.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvFavorites.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(() -> viewModel.refreshFavorites());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.filterFavorites(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        viewModel.favorites.observe(getViewLifecycleOwner(), list -> {
            adapter.submitList(list);
            if (list == null || list.isEmpty()) {
                rvFavorites.setVisibility(View.GONE);
                tvEmpty.setVisibility(View.VISIBLE);
            } else {
                rvFavorites.setVisibility(View.VISIBLE);
                tvEmpty.setVisibility(View.GONE);
            }
        });

        viewModel.syncMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.trim().isEmpty()) {
                if (IFavoriteRepository.ERROR_REFRESH_FAILED.equals(message)) {
                    AppSnackbar.show(requireContext(), R.string.favorite_refresh_failed);
                    viewModel.consumeSyncMessage();
                    return;
                }
                AppSnackbar.show(requireContext(), message);
                viewModel.consumeSyncMessage();
            }
        });

        viewModel.isSyncing.observe(getViewLifecycleOwner(), isSyncing ->
                swipeRefreshLayout.setRefreshing(Boolean.TRUE.equals(isSyncing)));
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.refreshFavoritesIfStale();
    }

    @Override
    public void onFavoriteClicked(Favorite favorite) {
        if (favorite == null) {
            return;
        }

        Uri detailUri = UriUtils.buildUri(UriUtils.PathTo.FAVORITES_DETAIL)
                .buildUpon()
                .appendQueryParameter("favId", safeString(favorite.id))
            .appendQueryParameter("favPlaceId", safeString(favorite.placeId))
                .appendQueryParameter("favName", safeString(favorite.name))
                .appendQueryParameter("favAddress", safeString(favorite.address))
                .appendQueryParameter("favDescription", safeString(favorite.description))
                .appendQueryParameter("favNotes", safeString(favorite.notes))
                .appendQueryParameter("favRating", String.valueOf(favorite.rating))
                .appendQueryParameter("favLatitude", String.valueOf(favorite.latitude))
                .appendQueryParameter("favLongitude", String.valueOf(favorite.longitude))
                .build();

        Navigation.findNavController(requireView()).navigate(detailUri);
    }

    @Override
    public void onFavoriteRemoved(Favorite favorite) {
        viewModel.removeFavoriteItem(favorite);
    }

    @NonNull
    private String safeString(@Nullable String value) {
        return value == null ? "" : value;
    }
}
