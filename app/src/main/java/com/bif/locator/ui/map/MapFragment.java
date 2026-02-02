package com.bif.locator.ui.map;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.bif.locator.R;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MapFragment extends Fragment implements OnMapReadyCallback {

    private GoogleMap googleMap;
    private TextView startText;
    private MapViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(MapViewModel.class);
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize the Map
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        startText = view.findViewById(R.id.start_text);

        // Go back home button
        Button btnGoBackHome = view.findViewById(R.id.btn_back_home);
        btnGoBackHome.setOnClickListener(v -> {
            Navigation.findNavController(v).navigate(R.id.action_map_to_home);
        });

        // Observe ViewModel
        viewModel.statusText.observe(getViewLifecycleOwner(), text -> startText.setText(text));

        viewModel.searchResult.observe(getViewLifecycleOwner(), location -> {
            if (location != null && googleMap != null) {
                LatLng target = new LatLng(location.latitude, location.longitude);
                googleMap.addMarker(new MarkerOptions()
                        .position(target)
                        .title("Destination")
                        .snippet("Location found")
                );
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 20f));
                viewModel.setStatusText("Location found");
            } else {
                viewModel.setStatusText("Location not found");
            }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;

        if (getArguments() != null) {
            MapFragmentArgs args = MapFragmentArgs.fromBundle(getArguments());
            String location = args.getLocation();

            if (location != null && !location.isEmpty()) {
                viewModel.setStatusText(String.format("Going to location %s...", location));
                viewModel.searchLocation(location);
            } else {
                // Default location: HCMUS
                String defaultLocation = "10.7626636, 106.6823091";
                viewModel.setStatusText(getString(R.string.going_to_hcmus)); // Assuming resource exists or use hardcoded if needed
                viewModel.searchLocation(defaultLocation);
            }
        }
    }
}