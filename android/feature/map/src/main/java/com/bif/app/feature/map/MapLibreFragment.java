package com.bif.app.feature.map;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bif.app.core.utils.UriUtils;
import com.bif.app.domain.model.Favorite;
import com.bif.app.domain.model.Group;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.MapState;
import com.bif.app.domain.model.Place;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.maps.Style;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MapLibreFragment extends Fragment implements OnMapReadyCallback {

    private static final String TAG = "MapLibreFragment";
    private static final String DEFAULT_STYLE_URL =
            "https://demotiles.maplibre.org/style.json";

    private MapView mapView;
    private MapLibreMap mapLibreMap;
    private Marker selectedMarker;
    private BottomSheetBehavior<View> bottomSheetBehavior;
    private MapViewModel viewModel;
    private List<Favorite> currentFavorites = new ArrayList<>();
    private final Map<Long, Place> markerPlaces = new HashMap<>();
    private final List<Marker> searchResultMarkers = new ArrayList<>();
    private Runnable hideHistory = () -> { };

        @Inject
        FusedLocationProviderClient fusedLocationClient;

        private final androidx.activity.result.ActivityResultLauncher<String>
            requestPermissionLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts
                .RequestPermission(),
            isGranted -> {
            if (!isGranted) {
                viewModel.setStatusText(
                    "Permission denied. Cannot show current location.");
            }
            });

    private final MapView.OnDidFailLoadingMapListener
            onDidFailLoadingMapListener =
            errorMessage -> Log.e(TAG, "Map load failed: " + errorMessage);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_map_maplibre, container,
                false);
        mapView = root.findViewById(R.id.maplibre_map_view);
        mapView.onCreate(savedInstanceState);

        View bottomSheet = root.findViewById(R.id.place_detail_sheet);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(MapViewModel.class);

        viewModel.allFavorites.observe(getViewLifecycleOwner(), favorites -> {
            currentFavorites = favorites != null ? favorites : new ArrayList<>();
        });

        viewModel.statusText.observe(getViewLifecycleOwner(), event -> {
            if (event == null) {
                return;
            }
            String text = event.getContentIfNotHandled();
            if (text != null && !text.isEmpty()) {
                Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show();
            }
        });

        setupSearchUi(view);

        ImageButton btnMyLocation = view.findViewById(R.id.btn_my_location);
        if (btnMyLocation != null) {
            btnMyLocation.setOnClickListener(v -> {
                if (bottomSheetBehavior.getState()
                        != BottomSheetBehavior.STATE_HIDDEN) {
                    bottomSheetBehavior.setState(
                            BottomSheetBehavior.STATE_HIDDEN);
                }
                goToMyLocation();
            });
        }

        viewModel.searchResults.observe(getViewLifecycleOwner(), places -> {
            if (mapLibreMap == null) {
                return;
            }
            renderPlaceResults(places);
        });

        viewModel.historySearchResults.observe(getViewLifecycleOwner(), places -> {
            if (mapLibreMap == null) {
                return;
            }
            renderPlaceResults(places);
        });

        if (mapView != null) {
            mapView.addOnDidFailLoadingMapListener(onDidFailLoadingMapListener);
            mapView.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull MapLibreMap mapLibreMap) {
        this.mapLibreMap = mapLibreMap;
        String configuredStyle = BuildConfig.MAPLIBRE_STYLE_URL;
        String styleUrl = TextUtils.isEmpty(configuredStyle)
                ? DEFAULT_STYLE_URL
                : configuredStyle;

        mapLibreMap.setStyle(new Style.Builder().fromUri(styleUrl), style -> {
            CameraPosition camera = new CameraPosition.Builder()
                    .target(new LatLng(10.7769, 106.7009))
                    .zoom(12.0)
                    .build();
            mapLibreMap.setCameraPosition(camera);

            mapLibreMap.addOnMapClickListener(point -> {
                hideHistory.run();
                fetchAddressAndShowDetails(point, null);
                return true;
            });

            mapLibreMap.setOnMarkerClickListener(marker -> {
                Place mappedPlace = markerPlaces.get(marker.getId());
                if (mappedPlace == null) {
                    mappedPlace = new Place(
                            UUID.randomUUID().toString(),
                            marker.getTitle() != null ? marker.getTitle() : "Selected Location",
                            marker.getSnippet() != null ? marker.getSnippet() : "Address unavailable",
                            0.0,
                            new Location(marker.getPosition().getLatitude(),
                                    marker.getPosition().getLongitude())
                    );
                }
                mapLibreMap.animateCamera(
                        CameraUpdateFactory.newLatLng(marker.getPosition()));
                hideHistory.run();
                showPlaceBottomSheet(mappedPlace, requireView());
                return true;
            });

            MapState savedState = viewModel.getLastMapState();
            if (savedState != null) {
                mapLibreMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                        new LatLng(savedState.latitude, savedState.longitude),
                        savedState.zoomLevel));
            } else {
                goToMyLocation();
            }
        });

    }

    private void setupSearchUi(View root) {
        View mapRoot = root.findViewById(R.id.maplibre_root);
        SearchView searchView = root.findViewById(R.id.map_search);
        RecyclerView rvHistory = root.findViewById(R.id.rv_search_history);

        hideHistory = () -> {
            rvHistory.setVisibility(View.GONE);
            searchView.setBackgroundResource(R.drawable.bg_searchbar);
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) requireContext()
                            .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(searchView.getWindowToken(), 0);
            }
            if (mapRoot != null) {
                mapRoot.requestFocus();
            }
        };

        Runnable showHistory = () -> {
            List<String> current = viewModel.searchHistory.getValue();
            if (current != null && !current.isEmpty()) {
                searchView.setBackgroundResource(R.drawable.bg_searchbar_open);
                rvHistory.setVisibility(View.VISIBLE);
            }
        };

        SearchHistoryAdapter historyAdapter = new SearchHistoryAdapter(query -> {
            searchView.setQuery(query, false);
            viewModel.searchForPlacesFromHistory(query);
            hideHistory.run();
        });

        rvHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvHistory.setAdapter(historyAdapter);

        searchView.setOnQueryTextFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                showHistory.run();
            } else {
                hideHistory.run();
            }
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                viewModel.searchForPlaces(query);
                viewModel.setStatusText("Searching for " + query);
                hideHistory.run();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        viewModel.searchHistory.observe(getViewLifecycleOwner(), history -> {
            historyAdapter.submitList(history);
            if (searchView.hasFocus() && history != null && !history.isEmpty()) {
                showHistory.run();
            } else if (history == null || history.isEmpty()) {
                rvHistory.setVisibility(View.GONE);
                searchView.setBackgroundResource(R.drawable.bg_searchbar);
            }
        });
    }

    private void renderPlaceResults(List<Place> places) {
        if (places == null || mapLibreMap == null) {
            return;
        }

        clearSearchResultMarkers();

        if (!places.isEmpty()) {
            LatLng first = null;
            LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
            int includedCount = 0;
            for (Place place : places) {
                if (place.location == null) {
                    continue;
                }
                LatLng position = new LatLng(place.location.latitude,
                        place.location.longitude);
                Marker marker = mapLibreMap.addMarker(
                        new MarkerOptions()
                                .position(position)
                                .title(place.name)
                                .snippet(place.address)
                );
                if (marker != null) {
                    searchResultMarkers.add(marker);
                    markerPlaces.put(marker.getId(), place);
                    if (first == null) {
                        first = position;
                    }
                    boundsBuilder.include(position);
                    includedCount++;
                }
            }

            if (includedCount > 1) {
                mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngBounds(
                        boundsBuilder.build(), 150));
            } else if (first != null) {
                mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(first, 14.0));
            }
        }

        viewModel.notifySearchDone(places.size());
    }

    private void clearSearchResultMarkers() {
        for (Marker marker : searchResultMarkers) {
            if (marker != null) {
                markerPlaces.remove(marker.getId());
                marker.remove();
            }
        }
        searchResultMarkers.clear();
    }

    private void fetchAddressAndShowDetails(LatLng latLng, String providedName) {
        Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());

        new Thread(() -> {
            try {
                List<Address> addresses = geocoder.getFromLocation(
                        latLng.getLatitude(), latLng.getLongitude(), 1);

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

                Place clickedPlace = new Place(
                        UUID.randomUUID().toString(),
                        finalName,
                        addressText != null ? addressText : "Unknown Address",
                        0.0,
                        new Location(latLng.getLatitude(), latLng.getLongitude())
                );

                requireActivity().runOnUiThread(() -> {
                    if (mapLibreMap == null) {
                        return;
                    }

                    if (selectedMarker != null) {
                        markerPlaces.remove(selectedMarker.getId());
                        selectedMarker.remove();
                    }

                    selectedMarker = mapLibreMap.addMarker(
                            new MarkerOptions()
                                    .position(latLng)
                                    .title(clickedPlace.name)
                                    .snippet(clickedPlace.address)
                    );
                        markerPlaces.put(selectedMarker.getId(), clickedPlace);

                    mapLibreMap.animateCamera(CameraUpdateFactory.newLatLng(latLng));
                    showPlaceBottomSheet(clickedPlace, requireView());
                });
            } catch (IOException e) {
                Log.e(TAG, "Geocoding failed", e);
                requireActivity().runOnUiThread(() -> {
                    Place fallbackPlace = new Place(
                            UUID.randomUUID().toString(),
                            providedName != null && !providedName.isEmpty()
                                    ? providedName : "Selected Location",
                            "Address unavailable",
                            0.0,
                            new Location(latLng.getLatitude(),
                                    latLng.getLongitude())
                    );

                    if (mapLibreMap == null) {
                        return;
                    }
                    if (selectedMarker != null) {
                        markerPlaces.remove(selectedMarker.getId());
                        selectedMarker.remove();
                    }
                    selectedMarker = mapLibreMap.addMarker(
                            new MarkerOptions()
                                    .position(latLng)
                                    .title(fallbackPlace.name)
                                    .snippet(fallbackPlace.address)
                    );
                    markerPlaces.put(selectedMarker.getId(), fallbackPlace);
                    mapLibreMap.animateCamera(CameraUpdateFactory.newLatLng(latLng));
                    showPlaceBottomSheet(fallbackPlace, requireView());
                });
            }
        }).start();
    }

    private void showPlaceBottomSheet(Place place, View root) {
        viewModel.cacheViewedPlace(place);

        TextView tvName = root.findViewById(R.id.tv_place_name);
        TextView tvAddress = root.findViewById(R.id.tv_place_address);
        TextView tvRating = root.findViewById(R.id.tv_place_rating);
        ImageButton btnAddFavorite = root.findViewById(R.id.btn_add_favorite);
        com.google.android.material.button.MaterialButton btnSharePlace =
                root.findViewById(R.id.btn_share_place);
        com.google.android.material.button.MaterialButton btnNavigatePlace =
                root.findViewById(R.id.btn_navigate_place);

        tvName.setText(place.name);
        tvAddress.setText(place.address);
        if (place.rating > 0) {
            tvRating.setText(String.format(Locale.getDefault(), "★ %.1f",
                    place.rating));
        } else {
            tvRating.setText(R.string.default_rating);
        }

        btnAddFavorite.setEnabled(true);
        btnSharePlace.setEnabled(true);
        btnNavigatePlace.setEnabled(true);

        updateFavoriteButtonState(btnAddFavorite,
                findFavoriteForPlace(place) != null);

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

        btnNavigatePlace.setOnClickListener(v -> navigateToPlace(place));
        btnSharePlace.setOnClickListener(v -> showShareToGroupDialog(place));

        View layoutExtendedDetails = root.findViewById(R.id.layout_extended_details);
        View layoutContainer = root.findViewById(R.id.layout_container);
        layoutExtendedDetails.post(() -> {
            int dynamicPeekHeight = layoutExtendedDetails.getTop()
                    + layoutContainer.getPaddingBottom()
                    - layoutContainer.getPaddingTop();
            bottomSheetBehavior.setPeekHeight(dynamicPeekHeight);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        });
    }

    private Favorite findFavoriteForPlace(Place place) {
        for (Favorite favorite : currentFavorites) {
            if (place.address != null && !place.address.isEmpty()
                    && place.address.equals(favorite.address)) {
                return favorite;
            }

            if (place.location != null) {
                double latDelta = favorite.latitude - place.location.latitude;
                double lngDelta = favorite.longitude - place.location.longitude;
                if (Math.sqrt(latDelta * latDelta + lngDelta * lngDelta) < 0.0001) {
                    return favorite;
                }
            }
        }
        return null;
    }

    private void updateFavoriteButtonState(ImageButton button, boolean isFavorite) {
        int color = isFavorite
                ? android.graphics.Color.parseColor("#F0B100")
                : android.graphics.Color.parseColor("#2ECC71");
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
    }

    private void navigateToPlace(Place place) {
        String query;
        if (place.location != null) {
            query = place.location.latitude + "," + place.location.longitude;
        } else if (place.address != null && !place.address.isEmpty()) {
            query = place.address;
        } else {
            query = place.name;
        }

        Uri mapUri = UriUtils.buildUri(UriUtils.PathTo.MAP)
                .buildUpon()
                .appendQueryParameter("location", query)
                .build();
        Navigation.findNavController(requireView()).navigate(mapUri);
    }

    private void showShareToGroupDialog(Place place) {
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
                .setItems(groupNames,
                        (dialog, which) -> navigateToGroupChatWithPlace(
                                groups.get(which), place))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void navigateToGroupChatWithPlace(Group group, Place place) {
        String mapLink = buildPlaceMapLink(place);

        Uri destUri = UriUtils.buildUri(UriUtils.PathTo.SOCIAL_CHAT)
                .buildUpon()
                .appendQueryParameter("chatType", "group")
                .appendQueryParameter("chatId", group.getServerId())
                .appendQueryParameter("chatName", group.getName())
                .appendQueryParameter("avatarLetter", group.getAvatarLetter())
                .appendQueryParameter("avatarColor",
                        String.valueOf(group.getAvatarColor()))
                .appendQueryParameter("memberCount",
                        String.valueOf(group.getMemberCount()))
                .appendQueryParameter("sharedPlaceName",
                        place.name != null ? place.name : "")
                .appendQueryParameter("sharedPlaceAddress",
                        place.address != null ? place.address : "")
                .appendQueryParameter("sharedPlaceLink", mapLink)
                .build();

        Navigation.findNavController(requireView()).navigate(destUri);
    }

    private String buildPlaceMapLink(Place place) {
        if (place.location != null) {
            return place.location.latitude + "," + place.location.longitude;
        }
        return place.address != null ? place.address : "";
    }

    private void goToMyLocation() {
        if (mapLibreMap == null) {
            return;
        }

        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            viewModel.setStatusText("Location permission is required.");
            return;
        }

        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            viewModel.setStatusText("Location permission is required.");
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                        new LatLng(location.getLatitude(), location.getLongitude()),
                        15.0));
            } else {
                viewModel.setStatusText("Waiting for GPS signal...");
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mapView != null) {
            mapView.onStart();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    public void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        if (mapLibreMap != null) {
            CameraPosition position = mapLibreMap.getCameraPosition();
            if (position != null && position.target != null) {
                viewModel.saveMapState(position.target.getLatitude(),
                        position.target.getLongitude(), (float) position.zoom);
            }
        }
        super.onPause();
    }

    @Override
    public void onStop() {
        if (mapView != null) {
            mapView.onStop();
        }
        super.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) {
            mapView.onLowMemory();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) {
            mapView.onSaveInstanceState(outState);
        }
    }

    @Override
    public void onDestroyView() {
        if (mapView != null) {
            mapView.removeOnDidFailLoadingMapListener(
                    onDidFailLoadingMapListener);
            mapView.onDestroy();
            mapView = null;
        }
        clearSearchResultMarkers();
        markerPlaces.clear();
        super.onDestroyView();
    }
}
