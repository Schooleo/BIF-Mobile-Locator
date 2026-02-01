package com.bif.locator.ui.map;

import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.bif.locator.R;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MapFragment extends Fragment implements OnMapReadyCallback {

    private GoogleMap googleMap;
    private TextView startText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
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
            // Navigate back to the HomeFragment
            Navigation.findNavController(v).navigate(R.id.action_map_to_home);
        });
    }

    // This callback runs when the Google Map is ready to be used.
    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;

        if (getArguments() != null) {
            MapFragmentArgs args = MapFragmentArgs.fromBundle(getArguments());
            String location = args.getLocation();

            assert location != null;
            if (!location.isEmpty()) {
                startText.setText(String.format("Going to location %s...", location));

                goToLocation(location);
            } else {
                startText.setText(R.string.going_to_hcmus);

                // Go to HCMUS - (10.7626636, 106.6823091)
                goToLocation("10.7626636, 106.6823091");
            }
        }
    }

    private void goToLocation(String location) {
        new Thread(() -> {
            Geocoder geocoder = new Geocoder(requireContext());
            try {
                List<Address> results = geocoder.getFromLocationName(location, 1);

                if (results != null && !results.isEmpty()) {
                    Address address = results.get(0);
                    LatLng target = new LatLng(address.getLatitude(), address.getLongitude());

                    requireActivity().runOnUiThread(() -> {
                        startText.setText(String.format("Location found for: %s", location));

                        googleMap.addMarker(new MarkerOptions()
                                .position(target)
                                .title(location)
                                .snippet("You searched for this location!")
                        );

                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 20f));
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }).start();
    }
}