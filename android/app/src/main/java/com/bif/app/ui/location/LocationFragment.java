package com.bif.app.ui.location;

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
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import com.bif.app.R;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class LocationFragment extends Fragment {

    private LocationViewModel viewModel;

    // UI components
    private TextView tvLatitude;
    private TextView tvLongitude;
    private TextView tvStatus;
    private Button btnFetchLocation;
    private Button btnToggleTracking;
    private Button btnGoBackHome;


    // Permission launcher
    private final ActivityResultLauncher<String> requestPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                // Permission granted, fetch location
                viewModel.fetchCurrentLocation();
            } else {
                tvStatus.setText("Permission denied. Cannot access location.");
            }
        });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(LocationViewModel.class);
        return inflater.inflate(R.layout.fragment_location, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Bind UI components
        tvLatitude = view.findViewById(R.id.tv_latitude);
        tvLongitude = view.findViewById(R.id.tv_longitude);
        tvStatus = view.findViewById(R.id.tv_status);
        btnFetchLocation = view.findViewById(R.id.btn_fetch_location);
        btnToggleTracking = view.findViewById(R.id.btn_toggle_tracking);
        btnGoBackHome = view.findViewById(R.id.btn_back_home);

        // Setup button click listeners
        btnFetchLocation.setOnClickListener(v -> onFetchLocationClick());
        btnToggleTracking.setOnClickListener(v -> onToggleTrackingClick());

        NavDirections locationToHome = LocationFragmentDirections.actionLocationToHome();
        btnGoBackHome.setOnClickListener(v -> Navigation.findNavController(v).navigate(locationToHome));

        // Observe LiveData from ViewModel
        observeViewModel();
    }

    private void observeViewModel() {
        // Observe current location
        viewModel.currentLocation.observe(getViewLifecycleOwner(), location -> {
            if (location != null) {
                tvLatitude.setText(String.valueOf(location.latitude));
                tvLongitude.setText(String.valueOf(location.longitude));
            }
        });

        // Observe status text
        viewModel.statusText.observe(getViewLifecycleOwner(), status -> {
            if (status != null) {
                tvStatus.setText(status);
            }
        });

        // Observe tracking state
        viewModel.isTracking.observe(getViewLifecycleOwner(), isTracking -> {
            if (isTracking != null) {
                btnToggleTracking.setText(isTracking ? "Stop Tracking" : "Start Tracking");
            }
        });
    }

    private void onFetchLocationClick() {
        if (checkLocationPermission()) {
            viewModel.fetchCurrentLocation();
        } else {
            requestLocationPermission();
        }
    }

    private void onToggleTrackingClick() {
        if (!checkLocationPermission()) {
            requestLocationPermission();
            return;
        }

        Boolean isTracking = viewModel.isTracking.getValue();
        if (isTracking != null && isTracking) {
            viewModel.stopTracking();
        } else {
            viewModel.startTracking();
        }
    }

    private boolean checkLocationPermission() {
        return ContextCompat.checkSelfPermission(
            requireContext(), 
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Stop tracking when fragment is destroyed
        viewModel.stopTracking();
    }
}