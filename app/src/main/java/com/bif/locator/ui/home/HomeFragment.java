package com.bif.locator.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;

import com.bif.locator.ui.home.HomeFragmentDirections.ActionHomeToMap;

import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.bif.locator.R;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class HomeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageButton searchButton = view.findViewById(R.id.btn_search_map);
        EditText locationInput = view.findViewById(R.id.location_input);

        searchButton.setOnClickListener(v -> {
            String location = locationInput.getText().toString();

            ActionHomeToMap action = HomeFragmentDirections.actionHomeToMap(location);

            Navigation.findNavController(v).navigate(action);
        });

        Button btnNavMap = view.findViewById(R.id.btn_nav_map);
        ActionHomeToMap homeToMap = HomeFragmentDirections.actionHomeToMap("");
        btnNavMap.setOnClickListener(v -> Navigation.findNavController(v).navigate(homeToMap));

        Button btnNavLoc = view.findViewById(R.id.btn_nav_loc);
        NavDirections homeToLocation = HomeFragmentDirections.actionHomeToLocation();
        btnNavLoc.setOnClickListener(v -> Navigation.findNavController(v).navigate(homeToLocation));

        Button btnNavSocial = view.findViewById(R.id.btn_nav_social);
        NavDirections homeToSocial = HomeFragmentDirections.actionHomeToSocial();
        btnNavSocial.setOnClickListener(v -> Navigation.findNavController(v).navigate(homeToSocial));
    }
}
