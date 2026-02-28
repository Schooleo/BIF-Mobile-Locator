package com.bif.app.feature.map;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.bif.app.domain.model.MapState;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import com.google.android.libraries.places.api.Places;

import javax.inject.Inject;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MapFragment extends Fragment implements OnMapReadyCallback {

    private GoogleMap googleMap;
    private TextView startText;
    private MapViewModel viewModel;

    @Inject
    MarkerFactory markerFactory;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                enableMyLocationLayer();
            } else {
                viewModel.setStatusText("Permission denied. Cannot show current location.");
            }
        });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(requireContext(), BuildConfig.PLACES_API_KEY);
        }

        MapsInitializer.initialize(requireContext(), MapsInitializer.Renderer.LATEST, null);
    }

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
        btnGoBackHome.setOnClickListener(v -> Navigation.findNavController(v).navigate(com.bif.app.core.R.id.action_map_to_home));

        // Observe ViewModel
        viewModel.statusText.observe(getViewLifecycleOwner(), text -> startText.setText(text));

        viewModel.searchResult.observe(getViewLifecycleOwner(), location -> {
            if (location != null && googleMap != null) {
                googleMap.clear();

                LatLng target = new LatLng(location.latitude, location.longitude);

                MarkerOptions marker = MarkerFactory.createMarker(target);
                googleMap.addMarker(marker);

                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 15f));
                viewModel.setStatusText("Location found");
            } else {
                viewModel.setStatusText("Location not found");
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();

        if (googleMap != null) {
            CameraPosition position = googleMap.getCameraPosition();
            viewModel.saveMapState(
                position.target.latitude,
                position.target.longitude,
                position.zoom
            );
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;

        enableMyLocationLayer();

        if (getArguments() != null) {
            String location = getArguments().getString("location");

            if (location != null && !location.isEmpty()) {
                viewModel.setStatusText(String.format("Going to location %s...", location));
                viewModel.searchLocation(location);
            }
        }

        MapState savedState = viewModel.getLastMapState();
        if (savedState != null) {
            LatLng target = new LatLng(savedState.latitude, savedState.longitude);
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(target, savedState.zoomLevel));
        } else {
            LatLng hcmus = new LatLng(10.7626636, 106.6823091);
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(hcmus, 10f));
        }
    }

    private void enableMyLocationLayer() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (googleMap != null) {
                googleMap.setMyLocationEnabled(true);
                googleMap.getUiSettings().setMyLocationButtonEnabled(true);
            }
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }
}