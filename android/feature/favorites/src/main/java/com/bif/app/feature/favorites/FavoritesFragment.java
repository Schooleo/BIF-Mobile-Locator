package com.bif.app.feature.favorites;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import com.bif.app.feature.favorites.R;
import com.bif.app.domain.model.Favorite;

import androidx.annotation.Nullable;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class FavoritesFragment extends Fragment
    implements FavoriteAdapter.OnFavoriteClickListener {

    private FavoritesViewModel viewModel;
    private FavoriteAdapter adapter;
    private RecyclerView rvFavorites;
    private TextView tvEmpty;
    private EditText etSearch;
    private ImageButton btnHome;

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
        etSearch = view.findViewById(R.id.et_search);
        btnHome = view.findViewById(R.id.btn_back_home);

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

        btnHome.setOnClickListener(v -> {
            Navigation.findNavController(v).navigate(com.bif.app.core.R.id.action_favorites_to_home);
        });
    }

    @Override
    public void onFavoriteClicked(Favorite favorite) {
        Bundle args = new Bundle();
        args.putInt("favId", favorite.id);
        args.putString("favName", favorite.name);
        args.putString("favAddress", favorite.address);
        args.putString("favDescription", favorite.description);
        args.putString("favNotes", favorite.notes);
        args.putInt("favRating", (int) favorite.rating);
        Navigation.findNavController(requireView())
                .navigate(com.bif.app.core.R.id.action_favorites_to_detail, args);
    }

    @Override
    public void onFavoriteRemoved(Favorite favorite) {
        viewModel.removeFavoriteItem(favorite);
    }
}
