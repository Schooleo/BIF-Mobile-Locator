package com.bif.locator.ui.favorites;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class FavoritesFragment extends Fragment {

    private FavoritesViewModel viewModel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(FavoritesViewModel.class);
        return null;
    }
}
