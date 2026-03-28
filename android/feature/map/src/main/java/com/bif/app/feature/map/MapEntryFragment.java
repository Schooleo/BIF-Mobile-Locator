package com.bif.app.feature.map;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bif.app.core.utils.MapEngine;
import com.bif.app.core.utils.UserPreferences;

public class MapEntryFragment extends Fragment {

    private MapEngine renderedEngine;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map_entry, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        renderEngineIfNeeded();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Re-evaluate in case the user changed engine settings elsewhere.
        renderEngineIfNeeded();
    }

    private void renderEngineIfNeeded() {
        MapEngine selectedEngine = UserPreferences.getMapEngine(requireContext());
        if (renderedEngine == selectedEngine && getChildFragmentManager()
                .findFragmentById(R.id.map_engine_container) != null) {
            return;
        }

        Fragment targetFragment = selectedEngine == MapEngine.OSM
            ? new MapLibreFragment()
                : new MapFragment();

        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.map_engine_container, targetFragment)
                .commit();

        renderedEngine = selectedEngine;
    }
}
