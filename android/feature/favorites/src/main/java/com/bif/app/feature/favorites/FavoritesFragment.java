package com.bif.app.feature.favorites;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bif.app.core.utils.UriUtils;
import com.bif.app.domain.model.Favorite;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class FavoritesFragment extends Fragment
    implements FavoriteAdapter.OnFavoriteClickListener {

    private FavoritesViewModel viewModel;
    private FavoriteAdapter adapter;
    private RecyclerView rvFavorites;
    private TextView tvEmpty;

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
        EditText etSearch = view.findViewById(R.id.et_search);

        adapter = new FavoriteAdapter(this);
        rvFavorites.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvFavorites.setAdapter(adapter);

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
    }

    @Override
    public void onFavoriteClicked(Favorite favorite) {
        // Deep Link
        android.net.Uri destUri = UriUtils.buildUri(UriUtils.PathTo.FAVORITES_DETAIL).buildUpon()
                .appendQueryParameter("favId", String.valueOf(favorite.id))
                .appendQueryParameter("favName", favorite.name != null ? favorite.name : "")
                .appendQueryParameter("favAddress", favorite.address != null ? favorite.address : "")
                .appendQueryParameter("favDescription", favorite.description != null ? favorite.description : "")
                .appendQueryParameter("favNotes", favorite.notes != null ? favorite.notes : "")
                .appendQueryParameter("favRating", String.valueOf(favorite.rating))
                .build();

        Navigation.findNavController(requireView()).navigate(destUri);
    }

    @Override
    public void onFavoriteRemoved(Favorite favorite) {
        viewModel.removeFavoriteItem(favorite);
    }
}
