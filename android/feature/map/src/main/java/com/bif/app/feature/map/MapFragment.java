package com.bif.app.feature.map;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bif.app.domain.model.Favorite;
import com.bif.app.domain.model.Group;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.MapState;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MapFragment extends Fragment implements OnMapReadyCallback {

    private GoogleMap googleMap;
    private MapViewModel viewModel;
    private BottomSheetBehavior<View> bottomSheetBehavior;

    private List<Favorite> currentFavorites = new ArrayList<>();
    private List<Marker> favoriteMarkers = new ArrayList<>();
    private List<Marker> temporaryMarkers = new ArrayList<>();

    @Inject
    FusedLocationProviderClient fusedLocationClient;

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
            Places.initialize(requireContext(), BuildConfig.PLACES_API_KEY);
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

        // Set up the Search Bar
        SearchView searchView = view.findViewById(R.id.map_search);
        if (searchView != null) {
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    viewModel.searchForPlaces(query);
                    viewModel.setStatusText("Searching for " + query);
                    searchView.clearFocus();
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    return false;
                }
            });
        }

        // Set up the My Location Button
        ImageButton btnMyLocation = view.findViewById(R.id.btn_my_location);
        if (btnMyLocation != null) {
            btnMyLocation.setOnClickListener(v -> goToMyLocation());
        }

        // Set up the Hidden Bottom Sheet (Place details)
        View bottomSheet = view.findViewById(R.id.place_detail_sheet);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        // Show Toast for search statuses
        viewModel.statusText.observe(getViewLifecycleOwner(), text -> {
            if (text != null && !text.isEmpty()) {
                android.widget.Toast.makeText(requireContext(), text, android.widget.Toast.LENGTH_SHORT).show();
                viewModel.setStatusText(""); // Consume the event so it is not re-shown on navigation
            }
        });

        // Update favorites marker to changes
        viewModel.allFavorites.observe(getViewLifecycleOwner(), favorites -> {
            this.currentFavorites = favorites != null ? favorites : new ArrayList<>();
            updateFavoriteMarkers();
        });

        // Observe the Multiple Places Search Results
        viewModel.searchResults.observe(getViewLifecycleOwner(), places -> {
            if (googleMap == null) return;

            clearTemporaryMarkers();

            if (places != null && !places.isEmpty()) {
                com.google.android.gms.maps.model.LatLngBounds.Builder boundsBuilder =
                        new com.google.android.gms.maps.model.LatLngBounds.Builder();

                for (com.bif.app.domain.model.Place place : places) {
                    if (place.location != null) {
                        LatLng latLng = new LatLng(place.location.latitude, place.location.longitude);

                        MarkerOptions markerOptions = new MarkerOptions()
                                .position(latLng)
                                .title(place.name)
                                .snippet(place.address); // Shows address under the name

                        Marker marker = googleMap.addMarker(markerOptions);
                        if (marker != null) {
                            marker.setTag(place);
                            temporaryMarkers.add(marker);
                        }
                        boundsBuilder.include(latLng);
                    }
                }

                // Animate camera to fit all the new markers
                int padding = 150; // offset from edges of the map in pixels
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), padding));
                viewModel.setStatusText("Found " + places.size() + " places.");

            } else {
                viewModel.setStatusText("No places found.");
            }
        });

        // Observe Single Location Search Result
        viewModel.searchResult.observe(getViewLifecycleOwner(), location -> {
            if (location != null && googleMap != null) {
                clearTemporaryMarkers();

                LatLng target = new LatLng(location.latitude, location.longitude);

                MarkerOptions markerOpt = MarkerFactory.createMarker(target);
                Marker marker = googleMap.addMarker(markerOpt);

                if(marker != null) {
                    temporaryMarkers.add(marker); // [MỚI] Thêm vào list quản lý
                }

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

        int nightModeFlags = requireContext()
                .getResources()
                .getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;

        // Load Dark Mode Maps Style
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            try {
                boolean success = googleMap.setMapStyle(
                        MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style_dark)
                );
                if (!success) {
                    Log.e("MapFragment", "Style parsing failed.");
                }
            } catch (Resources.NotFoundException e) {
                Log.e("MapFragment", "Can't find style. Error: ", e);
            }
        }

        // Set up POI Clicks
        googleMap.setOnPoiClickListener(poi -> fetchAddressAndShowDetails(poi.latLng, poi.name));

        // Set up General Map Clicks
        googleMap.setOnMapClickListener(latLng -> fetchAddressAndShowDetails(latLng, null));

        // Set up Marker Clicks
        googleMap.setOnMarkerClickListener(marker -> {
            Object tag = marker.getTag();
            if (tag instanceof com.bif.app.domain.model.Place) {
                com.bif.app.domain.model.Place clickedPlace = (com.bif.app.domain.model.Place) tag;

                googleMap.animateCamera(CameraUpdateFactory.newLatLng(marker.getPosition()));

                showPlaceBottomSheet(clickedPlace, requireView());
            }
            else if (tag instanceof Favorite) {
                // Convert Favorite to Place to re-use in UI BottomSheet
                Favorite fav = (Favorite) tag;
                Location loc = new Location(fav.latitude, fav.longitude);
                com.bif.app.domain.model.Place mappedPlace = new com.bif.app.domain.model.Place(
                        String.valueOf(fav.id), fav.name, fav.address, fav.rating, loc
                );
                googleMap.animateCamera(CameraUpdateFactory.newLatLng(marker.getPosition()));
                showPlaceBottomSheet(mappedPlace, requireView());
            }
            return true;
        });

        enableMyLocationLayer();
        updateFavoriteMarkers();

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
            goToMyLocation();
        }
    }

    private void enableMyLocationLayer() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (googleMap != null) {
                googleMap.setMyLocationEnabled(true);
                googleMap.getUiSettings().setMyLocationButtonEnabled(false);
            }
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void goToMyLocation() {
        if (googleMap == null) return;

        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) {
            viewModel.setStatusText("Location permission is required.");
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                LatLng myLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myLatLng, 15f));
            } else {
                viewModel.setStatusText("Waiting for GPS signal...");
            }
        });
    }

    private void showPlaceBottomSheet(com.bif.app.domain.model.Place place, View root) {
        TextView tvName = root.findViewById(R.id.tv_place_name);
        TextView tvAddress = root.findViewById(R.id.tv_place_address);
        TextView tvRating = root.findViewById(R.id.tv_place_rating);
        ImageButton btnAddFavorite = root.findViewById(R.id.btn_add_favorite);
        com.google.android.material.button.MaterialButton btnSharePlace = root.findViewById(R.id.btn_share_place);
        com.google.android.material.button.MaterialButton btnNavigatePlace = root.findViewById(R.id.btn_navigate_place);

        tvName.setText(place.name);
        tvAddress.setText(place.address);

        if (place.rating > 0) {
            tvRating.setText(String.format(Locale.getDefault(), "★ %.1f", place.rating));
        } else {
            tvRating.setText(R.string.default_rating);
        }

        // TODO: Set up Place Images
        // RecyclerView rvImages = root.findViewById(R.id.rv_place_images);
        // PlaceImageAdapter imageAdapter = new PlaceImageAdapter(place.getImages());
        // rvImages.setAdapter(imageAdapter);

        // Dynamic Peek Height
        View layoutExtendedDetails = root.findViewById(R.id.layout_extended_details);
        View layoutContainer = root.findViewById(R.id.layout_container);
        layoutExtendedDetails.post(() -> {
            int dynamicPeekHeight = layoutExtendedDetails.getTop() + layoutContainer.getPaddingBottom() - layoutContainer.getPaddingTop();

            bottomSheetBehavior.setPeekHeight(dynamicPeekHeight);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        });

        // Set initial button state based on whether the place is already a favorite
        updateFavoriteButtonState(btnAddFavorite, findFavoriteForPlace(place) != null);

        // Handle "Add to Favorites"
        btnAddFavorite.setOnClickListener(v -> {
            Favorite existing = findFavoriteForPlace(place);
            if (existing != null) {
                viewModel.removeFromFavorites(existing);
                viewModel.setStatusText(place.name + " removed from Favorites!");
                updateFavoriteButtonState(btnAddFavorite, false);
            } else {
                viewModel.addToFavorites(place);
                viewModel.setStatusText(place.name + " added to Favorites!");
                updateFavoriteButtonState(btnAddFavorite, true);
            }
        });

        btnSharePlace.setOnClickListener(v -> showShareToGroupDialog(place));
        btnNavigatePlace.setOnClickListener(v -> navigateToPlace(place));
    }

    private void showShareToGroupDialog(com.bif.app.domain.model.Place place) {
        List<Group> groups = viewModel.allGroups.getValue();
        if (groups == null || groups.isEmpty()) {
            viewModel.setStatusText(getString(R.string.no_group_available));
            return;
        }

        String[] groupNames = new String[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            groupNames[i] = groups.get(i).getName();
        }

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.select_group_to_share)
                .setItems(groupNames, (dialog, which) -> navigateToGroupChatWithPlace(groups.get(which), place))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void navigateToGroupChatWithPlace(Group group, com.bif.app.domain.model.Place place) {
        String mapLink = buildPlaceMapLink(place);

        android.net.Uri destUri = com.bif.app.core.utils.UriUtils.buildUri(com.bif.app.core.utils.UriUtils.PathTo.SOCIAL_CHAT)
                .buildUpon()
                .appendQueryParameter("chatType", "group")
                .appendQueryParameter("chatId", group.getServerId())
                .appendQueryParameter("chatName", group.getName())
                .appendQueryParameter("avatarLetter", group.getAvatarLetter())
                .appendQueryParameter("avatarColor", String.valueOf(group.getAvatarColor()))
                .appendQueryParameter("memberCount", String.valueOf(group.getMemberCount()))
                .appendQueryParameter("sharedPlaceName", place.name != null ? place.name : "")
                .appendQueryParameter("sharedPlaceAddress", place.address != null ? place.address : "")
                .appendQueryParameter("sharedPlaceLink", mapLink)
                .build();

        androidx.navigation.Navigation.findNavController(requireView()).navigate(destUri);
    }

    private String buildPlaceMapLink(com.bif.app.domain.model.Place place) {
        if (place.location != null) {
            return place.location.latitude + "," + place.location.longitude;
        }
        return place.address != null ? place.address : "";
    }

    private void navigateToPlace(com.bif.app.domain.model.Place place) {
        String query;
        if (place.location != null) {
            query = place.location.latitude + "," + place.location.longitude;
        } else if (place.address != null && !place.address.isEmpty()) {
            query = place.address;
        } else {
            query = place.name;
        }

        android.net.Uri mapUri = com.bif.app.core.utils.UriUtils.buildUri(com.bif.app.core.utils.UriUtils.PathTo.MAP)
                .buildUpon()
                .appendQueryParameter("location", query)
                .build();
        androidx.navigation.Navigation.findNavController(requireView()).navigate(mapUri);
    }

    /* Returns the Favorite matching the given place (by address or proximity), or null. */
    private Favorite findFavoriteForPlace(com.bif.app.domain.model.Place place) {
        for (Favorite fav : currentFavorites) {
            if (place.address != null && !place.address.isEmpty()
                    && place.address.equals(fav.address)) {
                return fav;
            }
            if (place.location != null) {
                double lat = fav.latitude - place.location.latitude;
                double lng = fav.longitude - place.location.longitude;
                if (Math.sqrt(lat * lat + lng * lng) < 0.0001) {
                    return fav;
                }
            }
        }
        return null;
    }

    /** Tints the favorite button yellow when saved, green when not. */
    private void updateFavoriteButtonState(ImageButton btn, boolean isFavorite) {
        int color = isFavorite
                ? android.graphics.Color.parseColor("#F0B100")   // yellow = saved
                : android.graphics.Color.parseColor("#2ECC71");  // green  = not saved
        btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
    }

    private void fetchAddressAndShowDetails(LatLng latLng, String providedName) {
        Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());

        new Thread(() -> {
            try {
                List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);

                String finalName = providedName;
                String addressText = "Unknown Address";

                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    addressText = address.getAddressLine(0);

                    if (finalName == null || finalName.isEmpty()) {
                        finalName = address.getFeatureName();

                        if (finalName == null || finalName.equals(addressText)) {
                            finalName = "Selected Location";
                        }
                    }
                } else if (finalName == null || finalName.isEmpty()) {
                    finalName = "Selected Location";
                }

                Location loc = new Location(latLng.latitude, latLng.longitude);

                // Create the generic Place object
                com.bif.app.domain.model.Place clickedPlace = new com.bif.app.domain.model.Place(
                        UUID.randomUUID().toString(), // Fake ID
                        finalName,
                        addressText != null ? addressText : "Unknown Address",
                        0.0, // Default rating
                        loc
                );

                // Switch to Main Thread to update the UI
                requireActivity().runOnUiThread(() -> {
                    clearTemporaryMarkers();
                    Marker marker = googleMap.addMarker(MarkerFactory.createMarker(latLng, clickedPlace.name, clickedPlace.address));

                    if (marker != null) {
                        marker.setTag(clickedPlace);
                        temporaryMarkers.add(marker);
                    }

                    googleMap.animateCamera(CameraUpdateFactory.newLatLng(latLng));
                    showPlaceBottomSheet(clickedPlace, requireView());
                    viewModel.setStatusText("");
                });

            } catch (IOException e) {
                Log.e("MapFragment", "Geocoding failed", e);

                // Fallback if Geocoder completely fails
                requireActivity().runOnUiThread(() -> {
                    if (providedName != null && !providedName.isEmpty()) {
                        com.bif.app.domain.model.Location loc = new com.bif.app.domain.model.Location(latLng.latitude, latLng.longitude);
                        com.bif.app.domain.model.Place fallbackPlace = new com.bif.app.domain.model.Place(
                                UUID.randomUUID().toString(), providedName, "Address unavailable", 0.0, loc);

                        clearTemporaryMarkers();
                        Marker marker = googleMap.addMarker(MarkerFactory.createMarker(latLng, fallbackPlace.name, fallbackPlace.address));
                        if (marker != null) {
                            marker.setTag(fallbackPlace);
                            temporaryMarkers.add(marker);
                        }

                        googleMap.animateCamera(CameraUpdateFactory.newLatLng(latLng));
                        showPlaceBottomSheet(fallbackPlace, requireView());
                    } else {
                        viewModel.setStatusText("Network error: Could not load address.");
                        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                    }
                });
            }
        }).start();
    }

    // Delete all temporary markers when search/click
    private void clearTemporaryMarkers() {
        for (Marker m : temporaryMarkers) {
            if (m != null) m.remove();
        }
        temporaryMarkers.clear();
    }

    // Return Favorite Markers
    private void updateFavoriteMarkers() {
        if (googleMap == null) return;

        // Delete old markers
        for (Marker m : favoriteMarkers) {
            if (m != null) m.remove();
        }
        favoriteMarkers.clear();

        // Redraw from latest data
        for (Favorite fav : currentFavorites) {
            LatLng pos = new LatLng(fav.latitude, fav.longitude);
            MarkerOptions opt = MarkerFactory.createFavoriteMarker(pos, fav.name);
            Marker m = googleMap.addMarker(opt);
            if (m != null) {
                m.setTag(fav); // Tag là Favorite
                favoriteMarkers.add(m);
            }
        }
    }
}