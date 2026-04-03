package com.bif.app.feature.map;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bif.app.core.utils.DialogUtils;
import com.bif.app.core.utils.UriUtils;
import com.bif.app.feature.map.BuildConfig;
import com.bif.app.feature.map.R;
import com.bif.app.domain.model.Favorite;
import com.bif.app.domain.model.Group;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.MapState;
import com.bif.app.domain.model.OfflineMapDownloadState;
import com.bif.app.domain.model.Place;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.maplibre.android.MapLibre;
import org.maplibre.android.WellKnownTileServer;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.Property;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.expressions.Expression;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.LineString;
import org.maplibre.geojson.Point;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import com.google.android.material.button.MaterialButton;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MapLibreFragment extends Fragment implements OnMapReadyCallback {

    private static final String TAG = "MapLibreFragment";
    private static final String DEFAULT_STYLE_URL = "https://demotiles.maplibre.org/style.json";
    private static final String NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org";
    private static final String OSM_USER_AGENT = "bif-mobile-app-android/1.0";
    private static final String FAVORITE_SOURCE_ID = "favorite-places-source";
    private static final String FAVORITE_LAYER_ID = "favorite-places-layer";
    private static final String SEARCH_SOURCE_ID = "search-places-source";
    private static final String SEARCH_LAYER_ID = "search-places-layer";
    private static final String SELECTED_SOURCE_ID = "selected-place-source";
    private static final String SELECTED_LAYER_ID = "selected-place-layer";
    private static final String ROUTE_REMAINING_SOURCE_ID = "route-remaining-source";
    private static final String ROUTE_REMAINING_LAYER_ID = "route-remaining-layer";
    private static final String ROUTE_PASSED_SOURCE_ID = "route-passed-source";
    private static final String ROUTE_PASSED_LAYER_ID = "route-passed-layer";
    private static final String ROUTE_TURN_SOURCE_ID = "route-turn-source";
    private static final String ROUTE_TURN_LAYER_ID = "route-turn-layer";
    private static final String USER_ROUTE_SOURCE_ID = "route-user-source";
    private static final String USER_ROUTE_LAYER_ID = "route-user-layer";
    private static final String MARKER_ICON_FAVORITE_ID = "marker-icon-favorite";
    private static final String MARKER_ICON_SEARCH_ID = "marker-icon-search";
    private static final String MARKER_ICON_SELECTED_ID = "marker-icon-selected";
    private static final String TURN_ARROW_ICON_ID = "turn-arrow-icon";
    private static final String USER_ARROW_ICON_ID = "user-arrow-icon";
    private static final String PROP_PLACE_ID = "placeId";
    private static final String PROP_NAME = "name";
    private static final String PROP_ADDRESS = "address";
    private static final String PROP_RATING = "rating";
    private static final String PROP_LAT = "lat";
    private static final String PROP_LNG = "lng";
    private static final String PROP_BEARING = "bearing";
    private static final double VIETNAM_MIN_LAT = 8.56;
    private static final double VIETNAM_MAX_LAT = 23.39;
    private static final double VIETNAM_MIN_LON = 102.14;
    private static final double VIETNAM_MAX_LON = 109.46;

    private MapView mapView;
    private MapLibreMap mapLibreMap;
    private Place selectedPlace;
    private BottomSheetBehavior<View> bottomSheetBehavior;
    private View bottomSheetContainer;
    private View placeDetailSheet;
    private View routeDetailSheet;
    private TextView tvRouteTitle;
    private TextView tvRouteAddress;
    private TextView tvRouteEta;
    private TextView tvRouteDistance;
    private View followRouteBar;
    private TextView tvFollowDistanceLeft;
    private TextView tvFollowTimeLeft;
    private MaterialButton btnStopFollowRoute;
    private MaterialButton btnCancelRoute;
    private MaterialButton btnFollowRoute;
    private ImageButton btnMapCompass;
    private final MapLibreMap.OnCameraMoveListener onCameraMoveListener = this::updateCompassButtonVisibility;
    private final MapLibreMap.OnCameraIdleListener onCameraIdleListener = this::updateCompassButtonVisibility;
    private MapViewModel viewModel;
    private List<Favorite> currentFavorites = new ArrayList<>();
    private Location lastKnownUserLocation;
    private float lastKnownUserBearingDegrees;
    private boolean followLocationUpdatesActive;
    private boolean reopeningPlaceAfterRouteStop;
    @Nullable
    private String renderedRouteGeometryJson;
    @NonNull
    private List<Point> renderedRoutePoints = Collections.emptyList();
    private View downloadCityMapLayout;
    private ImageButton btnDownloadCityMap;
    private CircularProgressIndicator progressDownloadCityMap;
    private boolean isOnlineNow;
    @Nullable
    private OfflineMapDownloadState.Status lastOfflineMapDownloadStatus;
    private Runnable hideHistory = () -> {
    };

    private final Handler locationHandler = new Handler(Looper.getMainLooper());
    private final Runnable locationTimeoutRunnable = () -> {
        stopSingleLocationUpdates();
        viewModel.setStatusText("Unable to get current location.");
    };

    private final LocationListener singleLocationListener = location -> {
        centerMapOnLocation(location);
        stopSingleLocationUpdates();
    };

    private final LocationListener followLocationListener = location -> {
        lastKnownUserLocation = new Location(location.getLatitude(), location.getLongitude());
        float bearing = location.hasBearing() ? location.getBearing() : 0f;
        lastKnownUserBearingDegrees = bearing;
        renderUserLocationIndicator(lastKnownUserLocation, bearing);
        if (viewModel != null) {
            viewModel.updateFollowingLocation(lastKnownUserLocation, bearing);
        }
    };

    private static final class PoiTap {
        final LatLng point;
        final String name;

        PoiTap(@NonNull LatLng point, @Nullable String name) {
            this.point = point;
            this.name = name;
        }
    }

    private final androidx.activity.result.ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (!isGranted) {
                    viewModel.setStatusText(
                            "Permission denied. Cannot show current location.");
                }
            });

    private final MapView.OnDidFailLoadingMapListener onDidFailLoadingMapListener = errorMessage -> Log.e(TAG,
            "Map load failed: " + errorMessage);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_map_maplibre, container,
                false);
        FrameLayout mapContainer = root.findViewById(R.id.maplibre_map_container);
        try {
            initializeMapLibreSdk();
            mapView = new MapView(requireContext());
            mapView.onCreate(savedInstanceState);
            mapContainer.addView(mapView,
                    new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
        } catch (Throwable t) {
            Log.e(TAG, "MapView initialization failed", t);
            mapView = null;
        }

        bottomSheetContainer = root.findViewById(R.id.bottom_sheet_container);
        placeDetailSheet = root.findViewById(R.id.place_detail_sheet);
        routeDetailSheet = root.findViewById(R.id.route_detail_sheet);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetContainer);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                updateFloatingControlsPosition();
                if (viewModel == null) {
                    return;
                }
                if (newState != BottomSheetBehavior.STATE_HIDDEN && reopeningPlaceAfterRouteStop) {
                    reopeningPlaceAfterRouteStop = false;
                }
                RouteSession session = viewModel.getCurrentRouteSession();
                if (newState == BottomSheetBehavior.STATE_HIDDEN && session.isVisible() && !session.following) {
                    viewModel.cancelRoute();
                } else if (newState == BottomSheetBehavior.STATE_HIDDEN && !reopeningPlaceAfterRouteStop) {
                    clearSelectedMarker();
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                updateFloatingControlsPosition();
            }
        });

        return root;
    }

    private void initializeMapLibreSdk() {
        MapLibre.getInstance(requireContext().getApplicationContext(), "",
                WellKnownTileServer.MapLibre);
    }

    @Override
    public void onViewCreated(@NonNull View view,
            @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MapViewModel.class);

        tvRouteTitle = view.findViewById(R.id.tv_route_title);
        tvRouteAddress = view.findViewById(R.id.tv_route_address);
        tvRouteEta = view.findViewById(R.id.tv_route_eta);
        tvRouteDistance = view.findViewById(R.id.tv_route_distance);
        followRouteBar = view.findViewById(R.id.layout_follow_route_bar);
        tvFollowDistanceLeft = view.findViewById(R.id.tv_follow_distance_left);
        tvFollowTimeLeft = view.findViewById(R.id.tv_follow_time_left);
        btnStopFollowRoute = view.findViewById(R.id.btn_stop_follow_route);
        btnCancelRoute = view.findViewById(R.id.btn_cancel_route);
        btnFollowRoute = view.findViewById(R.id.btn_follow_route);
        btnMapCompass = view.findViewById(R.id.btn_map_compass);

        if (btnCancelRoute != null) {
            btnCancelRoute.setOnClickListener(v -> stopRouteAndFocusDestination());
        }
        if (btnFollowRoute != null) {
            btnFollowRoute.setOnClickListener(v -> startFollowingRouteSession());
        }
        if (btnStopFollowRoute != null) {
            btnStopFollowRoute.setOnClickListener(v -> stopRouteAndFocusDestination());
        }
        if (btnMapCompass != null) {
            btnMapCompass.setOnClickListener(v -> resetMapBearingNorth());
        }

        if (mapView == null) {
            viewModel.setStatusText("Map initialization failed on this device.");
        }

        viewModel.allFavorites.observe(getViewLifecycleOwner(), favorites -> {
            currentFavorites = favorites != null ? favorites : new ArrayList<>();
            refreshFavoriteMarkers();
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

        viewModel.routeSession.observe(getViewLifecycleOwner(), this::renderRouteSession);

        setupSearchUi(view);

        ImageButton btnMyLocation = view.findViewById(R.id.btn_my_location);
        if (btnMyLocation != null) {
            btnMyLocation.setOnClickListener(v -> {
                RouteSession session = viewModel.getCurrentRouteSession();
                if (session.following && session.lastKnownLocation != null && !renderedRoutePoints.isEmpty()) {
                    RouteGeometryUtils.RouteProgress progress = RouteGeometryUtils.computeRouteProgress(
                            renderedRoutePoints,
                            session.lastKnownLocation);
                    fitCameraToUserAndRemainingRoute(session.lastKnownLocation, progress.remainingPoints);
                    return;
                }
                if (!viewModel.hasActiveRouteSession()
                        && bottomSheetBehavior.getState() != BottomSheetBehavior.STATE_HIDDEN) {
                    bottomSheetBehavior.setState(
                            BottomSheetBehavior.STATE_HIDDEN);
                }
                goToMyLocation();
            });
        }

        setupOfflineMapDownloadUi(view);

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
            MapStyleUtils.applyPaletteForCurrentMode(requireContext(), style);
            ensurePlaceLayers(style);
            configureCompassAboveMyLocation();
            mapLibreMap.addOnCameraMoveListener(onCameraMoveListener);
            mapLibreMap.addOnCameraIdleListener(onCameraIdleListener);

            CameraPosition camera = new CameraPosition.Builder()
                    .target(new LatLng(10.7769, 106.7009))
                    .zoom(12.0)
                    .build();
            mapLibreMap.setCameraPosition(camera);

            mapLibreMap.addOnMapClickListener(point -> {
                hideHistory.run();
                if (viewModel.hasActiveRouteSession()) {
                    viewModel.setStatusText(getString(R.string.route_active_place_sheet_blocked));
                    return true;
                }

                Place tappedPlace = findRenderedPlaceAt(point);
                if (tappedPlace != null) {
                    selectedPlace = tappedPlace;
                    renderSelectedPlace();
                    animateCameraToSelection(new LatLng(
                            tappedPlace.location.latitude,
                            tappedPlace.location.longitude));
                    showPlaceBottomSheet(tappedPlace, requireView());
                    return true;
                }

                PoiTap stylePoi = findStylePoiAt(point);
                if (stylePoi != null) {
                    fetchAddressAndShowDetails(stylePoi.point, stylePoi.name);
                    return true;
                }

                fetchAddressAndShowDetails(point, null);
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

            refreshFavoriteMarkers();
            renderUserLocationIndicator(lastKnownUserLocation, lastKnownUserBearingDegrees);
            renderRouteSession(viewModel.getCurrentRouteSession());
            updateCompassButtonVisibility();
        });

    }

    private void configureCompassAboveMyLocation() {
        if (mapLibreMap == null) {
            return;
        }
        mapLibreMap.getUiSettings().setCompassEnabled(false);
    }

    private void updateCompassButtonVisibility() {
        if (mapLibreMap == null || btnMapCompass == null) {
            return;
        }
        double bearing = Math.abs(mapLibreMap.getCameraPosition().bearing);
        boolean show = bearing > 1d && bearing < 359d;
        btnMapCompass.setVisibility(show ? View.VISIBLE : View.GONE);
        updateFloatingControlsPosition();
    }

    private void resetMapBearingNorth() {
        if (mapLibreMap == null) {
            return;
        }
        CameraPosition current = mapLibreMap.getCameraPosition();
        CameraPosition target = new CameraPosition.Builder()
                .target(current.target)
                .zoom(current.zoom)
                .tilt(current.tilt)
                .bearing(0.0)
                .build();
        mapLibreMap.animateCamera(CameraUpdateFactory.newCameraPosition(target), 450);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * requireContext().getResources()
                .getDisplayMetrics().density);
    }

    private float dpToPx(float dp) {
        return dp * requireContext().getResources().getDisplayMetrics().density;
    }

    private void animateCameraToSelection(@NonNull LatLng target) {
        if (mapLibreMap == null) {
            return;
        }
        CameraPosition current = mapLibreMap.getCameraPosition();
        double currentZoom = current.zoom;
        double targetZoom = Math.max(currentZoom, 15.5);
        CameraPosition targetCamera = new CameraPosition.Builder()
                .target(target)
                .zoom(targetZoom)
                .build();
        mapLibreMap.animateCamera(
                CameraUpdateFactory.newCameraPosition(targetCamera),
                700);
    }

    private void setupSearchUi(View root) {
        View mapRoot = root.findViewById(R.id.maplibre_root);
        SearchView searchView = root.findViewById(R.id.map_search);
        RecyclerView rvHistory = root.findViewById(R.id.rv_search_history);

        hideHistory = () -> {
            rvHistory.setVisibility(View.GONE);
            searchView.setBackgroundResource(R.drawable.bg_searchbar);
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext()
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

    private void setupOfflineMapDownloadUi(@NonNull View root) {
        downloadCityMapLayout = root.findViewById(R.id.layout_download_city_map);
        btnDownloadCityMap = root.findViewById(R.id.btn_download_city_map);
        progressDownloadCityMap = root.findViewById(R.id.progress_download_city_map);

        if (downloadCityMapLayout == null || btnDownloadCityMap == null || progressDownloadCityMap == null) {
            return;
        }

        btnDownloadCityMap.setOnClickListener(v -> {
            Location downloadLocation = resolveDownloadLocation();
            if (downloadLocation == null) {
                viewModel.setStatusText(getString(R.string.download_map_location_required));
                return;
            }

            if (!isInVietnamBounds(downloadLocation)) {
                viewModel.setStatusText(getString(R.string.route_not_supported_outside_vietnam));
                return;
            }

            DialogUtils.showConfirmDialog(
                    requireContext(),
                    getString(R.string.download_map_title),
                    getString(R.string.download_map_message),
                    getString(R.string.download_map_confirm),
                    getString(android.R.string.cancel),
                    () -> viewModel.downloadOfflineCityMap(downloadLocation));
        });

        LiveData<OfflineMapDownloadState> downloadStateLiveData = viewModel.observeOfflineMapDownloadState();
        downloadStateLiveData.observe(getViewLifecycleOwner(), state -> {
            renderOfflineMapDownloadState(state);
            updateDownloadCityMapVisibility(isOnlineNow, state);
        });

        viewModel.observeOnlineStatus().observe(getViewLifecycleOwner(), isOnline -> {
            isOnlineNow = Boolean.TRUE.equals(isOnline);
            updateDownloadCityMapVisibility(isOnlineNow, downloadStateLiveData.getValue());
        });
    }

    private void renderOfflineMapDownloadState(@Nullable OfflineMapDownloadState state) {
        if (btnDownloadCityMap == null || progressDownloadCityMap == null) {
            return;
        }

        OfflineMapDownloadState effectiveState = state != null
                ? state
                : OfflineMapDownloadState.idle();

        if (effectiveState.status == OfflineMapDownloadState.Status.DOWNLOADING) {
            btnDownloadCityMap.setEnabled(false);
            progressDownloadCityMap.setVisibility(View.VISIBLE);
            progressDownloadCityMap.setIndeterminate(effectiveState.indeterminate);
            if (!effectiveState.indeterminate) {
                progressDownloadCityMap.setProgressCompat(effectiveState.progressPercent, true);
            }
        } else {
            btnDownloadCityMap.setEnabled(true);
            progressDownloadCityMap.setVisibility(View.GONE);
            progressDownloadCityMap.setProgressCompat(0, false);
        }

        if (effectiveState.status == OfflineMapDownloadState.Status.COMPLETED
                && lastOfflineMapDownloadStatus != OfflineMapDownloadState.Status.COMPLETED) {
            Toast.makeText(requireContext(), R.string.download_map_success, Toast.LENGTH_SHORT).show();
        }

        if (effectiveState.status == OfflineMapDownloadState.Status.FAILED
                && lastOfflineMapDownloadStatus != OfflineMapDownloadState.Status.FAILED) {
            String message = effectiveState.errorMessage;
            if (message == null || message.trim().isEmpty()) {
                message = getString(R.string.download_map_failed);
            }
            viewModel.setStatusText(message);
        }

        lastOfflineMapDownloadStatus = effectiveState.status;
    }

    private void updateDownloadCityMapVisibility(boolean isOnline,
            @Nullable OfflineMapDownloadState state) {
        if (downloadCityMapLayout == null) {
            return;
        }

        OfflineMapDownloadState.Status status = state != null
                ? state.status
                : OfflineMapDownloadState.Status.IDLE;

        boolean alreadyDownloaded = status == OfflineMapDownloadState.Status.COMPLETED
                || status == OfflineMapDownloadState.Status.ALREADY_DOWNLOADED;
        boolean visible = !alreadyDownloaded
                && (isOnline || status == OfflineMapDownloadState.Status.DOWNLOADING);
        downloadCityMapLayout.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void renderPlaceResults(List<Place> places) {
        if (places == null || mapLibreMap == null) {
            return;
        }

        List<Feature> searchFeatures = new ArrayList<>();

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
                searchFeatures.add(createFeatureForPlace(position, place));
                if (first == null) {
                    first = position;
                }
                boundsBuilder.include(position);
                includedCount++;
            }

            if (includedCount > 1) {
                mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngBounds(
                        boundsBuilder.build(), 150));
            } else if (first != null) {
                mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(first, 14.0));
            }
        }

        setFeatures(SEARCH_SOURCE_ID, searchFeatures);

        viewModel.notifySearchDone(places.size());
    }

    private void clearSearchResultMarkers() {
        setFeatures(SEARCH_SOURCE_ID, Collections.emptyList());
    }

    private void refreshFavoriteMarkers() {
        if (mapLibreMap == null) {
            return;
        }

        List<Feature> favoriteFeatures = new ArrayList<>();
        for (Favorite favorite : currentFavorites) {
            Place place = new Place(
                    String.valueOf(favorite.id),
                    favorite.name,
                    favorite.address,
                    favorite.rating,
                    new Location(favorite.latitude, favorite.longitude));
            favoriteFeatures.add(createFeatureForPlace(
                    new LatLng(favorite.latitude, favorite.longitude),
                    place));
        }

        setFeatures(FAVORITE_SOURCE_ID, favoriteFeatures);
    }

    private void clearFavoriteMarkers() {
        setFeatures(FAVORITE_SOURCE_ID, Collections.emptyList());
    }

    private void clearSelectedMarker() {
        selectedPlace = null;
        setFeatures(SELECTED_SOURCE_ID, Collections.emptyList());
        updateFloatingControlsPosition();
    }

    private void renderSelectedPlace() {
        if (selectedPlace == null || selectedPlace.location == null) {
            setFeatures(SELECTED_SOURCE_ID, Collections.emptyList());
            return;
        }

        LatLng selectedPosition = new LatLng(selectedPlace.location.latitude,
                selectedPlace.location.longitude);
        setFeatures(SELECTED_SOURCE_ID, Collections.singletonList(
                createFeatureForPlace(selectedPosition, selectedPlace)));
    }

    private void ensurePlaceLayers(@NonNull Style style) {
        addSourceIfMissing(style, FAVORITE_SOURCE_ID);
        addSourceIfMissing(style, SEARCH_SOURCE_ID);
        addSourceIfMissing(style, SELECTED_SOURCE_ID);
        addSourceIfMissing(style, ROUTE_REMAINING_SOURCE_ID);
        addSourceIfMissing(style, ROUTE_PASSED_SOURCE_ID);
        addSourceIfMissing(style, ROUTE_TURN_SOURCE_ID);
        addSourceIfMissing(style, USER_ROUTE_SOURCE_ID);

        addMarkerImages(style);

        addRouteLayersIfMissing(style);

        addSymbolLayerIfMissing(style, FAVORITE_LAYER_ID, FAVORITE_SOURCE_ID,
                MARKER_ICON_FAVORITE_ID, 0.92f);
        addSymbolLayerIfMissing(style, SEARCH_LAYER_ID, SEARCH_SOURCE_ID,
                MARKER_ICON_SEARCH_ID, 1.0f);
        addSymbolLayerIfMissing(style, SELECTED_LAYER_ID, SELECTED_SOURCE_ID,
                MARKER_ICON_SELECTED_ID, 1.08f);
        addRotatingSymbolLayerIfMissing(style, ROUTE_TURN_LAYER_ID, ROUTE_TURN_SOURCE_ID,
                TURN_ARROW_ICON_ID, 0.72f);
        addRotatingSymbolLayerIfMissing(style, USER_ROUTE_LAYER_ID, USER_ROUTE_SOURCE_ID,
                USER_ARROW_ICON_ID, 0.92f);
    }

    private void addSourceIfMissing(@NonNull Style style, @NonNull String sourceId) {
        if (style.getSource(sourceId) == null) {
            style.addSource(new GeoJsonSource(sourceId,
                    FeatureCollection.fromFeatures(Collections.emptyList())));
        }
    }

    private void addRouteLayersIfMissing(@NonNull Style style) {
        if (style.getLayer(ROUTE_PASSED_LAYER_ID) == null) {
            LineLayer passedLayer = new LineLayer(ROUTE_PASSED_LAYER_ID, ROUTE_PASSED_SOURCE_ID);
            passedLayer.setProperties(
                    PropertyFactory.lineColor("#166534"),
                    PropertyFactory.lineWidth(6.2f),
                    PropertyFactory.lineOpacity(0.95f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND));
            style.addLayer(passedLayer);
        }

        if (style.getLayer(ROUTE_REMAINING_LAYER_ID) == null) {
            LineLayer remainingLayer = new LineLayer(ROUTE_REMAINING_LAYER_ID, ROUTE_REMAINING_SOURCE_ID);
            remainingLayer.setProperties(
                    PropertyFactory.lineColor("#2ECC71"),
                    PropertyFactory.lineWidth(6.0f),
                    PropertyFactory.lineOpacity(0.94f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND));
            style.addLayer(remainingLayer);
        }
    }

    private void addSymbolLayerIfMissing(@NonNull Style style,
            @NonNull String layerId,
            @NonNull String sourceId,
            @NonNull String iconId,
            float iconScale) {
        if (style.getLayer(layerId) != null) {
            return;
        }
        SymbolLayer layer = new SymbolLayer(layerId, sourceId);
        layer.setProperties(
                PropertyFactory.iconImage(iconId),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconSize(iconScale),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconOpacity(1.0f));
        style.addLayer(layer);
    }

    private void addRotatingSymbolLayerIfMissing(@NonNull Style style,
            @NonNull String layerId,
            @NonNull String sourceId,
            @NonNull String iconId,
            float iconScale) {
        if (style.getLayer(layerId) != null) {
            return;
        }
        SymbolLayer layer = new SymbolLayer(layerId, sourceId);
        layer.setProperties(
                PropertyFactory.iconImage(iconId),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
                PropertyFactory.iconSize(iconScale),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
                PropertyFactory.iconRotate(Expression.get(PROP_BEARING)));
        style.addLayer(layer);
    }

    private void addMarkerImages(@NonNull Style style) {
        style.addImage(MARKER_ICON_FAVORITE_ID,
                loadMarkerBitmap(R.drawable.ic_marker, "#F1C40F"));
        style.addImage(MARKER_ICON_SEARCH_ID,
                loadMarkerBitmap(R.drawable.ic_marker, "#F39C12"));
        style.addImage(MARKER_ICON_SELECTED_ID,
                loadMarkerBitmap(R.drawable.ic_marker, "#E74C3C"));
        style.addImage(TURN_ARROW_ICON_ID, createTurnArrowBitmap());
        style.addImage(USER_ARROW_ICON_ID, createUserNavigationBitmap());
    }

    @NonNull
    private Bitmap loadMarkerBitmap(int drawableRes, @Nullable String tintColor) {
        Drawable drawable = AppCompatResources.getDrawable(requireContext(), drawableRes);
        if (drawable == null) {
            return Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        }

        Drawable mutable = drawable.mutate();
        if (!TextUtils.isEmpty(tintColor)) {
            mutable = DrawableCompat.wrap(mutable);
            DrawableCompat.setTint(mutable, Color.parseColor(tintColor));
        }

        return drawableToBitmap(mutable);
    }

    @NonNull
    private Bitmap drawableToBitmap(@NonNull Drawable drawable) {
        int width = Math.max(drawable.getIntrinsicWidth(), 48);
        int height = Math.max(drawable.getIntrinsicHeight(), 48);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    @NonNull
    private Bitmap createTurnArrowBitmap() {
        int size = dpToPx(28);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setColor(resolveThemeColor(android.R.attr.textColorPrimary, Color.WHITE));
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeWidth(dpToPx(4));

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(Color.parseColor("#2ECC71"));
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeJoin(Paint.Join.ROUND);
        fill.setStrokeCap(Paint.Cap.ROUND);
        fill.setStrokeWidth(dpToPx(2.2f));

        Path path = new Path();
        path.moveTo(size * 0.22f, size * 0.78f);
        path.lineTo(size * 0.52f, size * 0.50f);
        path.lineTo(size * 0.52f, size * 0.68f);
        path.lineTo(size * 0.78f, size * 0.68f);
        path.lineTo(size * 0.78f, size * 0.32f);

        canvas.drawPath(path, stroke);
        canvas.drawPath(path, fill);
        return bitmap;
    }

    @NonNull
    private Bitmap createUserNavigationBitmap() {
        Drawable borderDrawable = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_navigation);
        Drawable fillDrawable = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_navigation);
        if (borderDrawable == null || fillDrawable == null) {
            return Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        }

        Drawable border = DrawableCompat.wrap(borderDrawable.mutate());
        Drawable fill = DrawableCompat.wrap(fillDrawable.mutate());
        DrawableCompat.setTint(border, resolveThemeColor(android.R.attr.textColorPrimary, Color.WHITE));
        DrawableCompat.setTint(fill, Color.parseColor("#2ECC71"));

        int size = dpToPx(40);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        int borderInset = dpToPx(4);
        border.setBounds(borderInset, borderInset, size - borderInset, size - borderInset);
        border.draw(canvas);

        int fillInset = dpToPx(8);
        fill.setBounds(fillInset, fillInset, size - fillInset, size - fillInset);
        fill.draw(canvas);
        return bitmap;
    }

    private int resolveThemeColor(int attrResId, int fallbackColor) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        if (requireContext().getTheme().resolveAttribute(attrResId, typedValue, true)) {
            if (typedValue.resourceId != 0) {
                return ContextCompat.getColor(requireContext(), typedValue.resourceId);
            }
            return typedValue.data;
        }
        return fallbackColor;
    }

    private void setFeatures(@NonNull String sourceId,
            @NonNull List<Feature> features) {
        if (mapLibreMap == null) {
            return;
        }

        Style style = mapLibreMap.getStyle();
        if (style == null) {
            return;
        }

        GeoJsonSource source = style.getSourceAs(sourceId);
        if (source != null) {
            source.setGeoJson(FeatureCollection.fromFeatures(features));
        }
    }

    private void clearRouteFeatures() {
        renderedRouteGeometryJson = null;
        renderedRoutePoints = Collections.emptyList();
        setFeatures(ROUTE_REMAINING_SOURCE_ID, Collections.emptyList());
        setFeatures(ROUTE_PASSED_SOURCE_ID, Collections.emptyList());
        setFeatures(ROUTE_TURN_SOURCE_ID, Collections.emptyList());
    }

    private void renderRouteSession(@Nullable RouteSession session) {
        RouteSession effectiveSession = session != null ? session : RouteSession.idle();
        renderRouteSheet(effectiveSession);

        if (mapLibreMap == null) {
            return;
        }

        if (!effectiveSession.hasRoute()) {
            clearRouteFeatures();
            renderUserLocationIndicator(lastKnownUserLocation, lastKnownUserBearingDegrees);
            return;
        }

        String geometryJson = effectiveSession.route != null ? effectiveSession.route.getGeometryJson() : null;
        if (geometryJson == null || geometryJson.trim().isEmpty()) {
            clearRouteFeatures();
            renderUserLocationIndicator(lastKnownUserLocation, lastKnownUserBearingDegrees);
            return;
        }

        if (!TextUtils.equals(renderedRouteGeometryJson, geometryJson)) {
            Feature routeFeature = parseRouteFeature(geometryJson);
            if (routeFeature == null || !(routeFeature.geometry() instanceof LineString)) {
                Log.w(TAG, "Route geometry could not be parsed for drawing. payload=" + truncateForLog(geometryJson));
                clearRouteFeatures();
                return;
            }
            renderedRouteGeometryJson = geometryJson;
            renderedRoutePoints = ((LineString) routeFeature.geometry()).coordinates();
            if (!effectiveSession.following) {
                fitCameraToRouteVisibleArea(routeFeature);
            }
        }

        if (renderedRoutePoints.size() < 2) {
            clearRouteFeatures();
            return;
        }

        List<Point> passedPoints = Collections.emptyList();
        List<Point> remainingPoints = renderedRoutePoints;
        List<Feature> userFeatures = Collections.emptyList();
        Location knownLocation = effectiveSession.lastKnownLocation != null
                ? effectiveSession.lastKnownLocation
                : lastKnownUserLocation;

        if (knownLocation != null) {
            RouteGeometryUtils.RouteProgress progress = RouteGeometryUtils.computeRouteProgress(
                    renderedRoutePoints,
                    knownLocation);
            passedPoints = progress.passedPoints;
            remainingPoints = progress.remainingPoints;
            float userBearing = effectiveSession.lastBearingDegrees != 0f
                    ? effectiveSession.lastBearingDegrees
                    : progress.segmentBearing;
            userFeatures = Collections.singletonList(createBearingFeature(
                    Point.fromLngLat(
                            knownLocation.longitude,
                            knownLocation.latitude),
                    userBearing));
            if (effectiveSession.following) {
                fitCameraToUserAndRemainingRoute(knownLocation, remainingPoints);
            }
            if (effectiveSession.following && tvFollowDistanceLeft != null) {
                tvFollowDistanceLeft
                        .setText(formatDistance(RouteGeometryUtils.polylineDistanceMeters(remainingPoints)));
            }
            if (effectiveSession.following && tvFollowTimeLeft != null && effectiveSession.route != null) {
                double remainingDistance = RouteGeometryUtils.polylineDistanceMeters(remainingPoints);
                double totalDistance = Math.max(1d, effectiveSession.route.getDistanceMeters());
                double remainingSeconds = effectiveSession.route.getDurationSeconds()
                        * Math.min(1d, Math.max(0d, remainingDistance / totalDistance));
                tvFollowTimeLeft.setText(formatDuration(remainingSeconds));
            }
        }

        setFeatures(ROUTE_PASSED_SOURCE_ID, passedPoints.size() >= 2
                ? Collections.singletonList(Feature.fromGeometry(LineString.fromLngLats(passedPoints)))
                : Collections.emptyList());
        setFeatures(ROUTE_REMAINING_SOURCE_ID, remainingPoints.size() >= 2
                ? Collections.singletonList(Feature.fromGeometry(LineString.fromLngLats(remainingPoints)))
                : Collections.emptyList());
        setFeatures(ROUTE_TURN_SOURCE_ID, Collections.emptyList());
        if (userFeatures.isEmpty()) {
            renderUserLocationIndicator(lastKnownUserLocation, lastKnownUserBearingDegrees);
        } else {
            setFeatures(USER_ROUTE_SOURCE_ID, userFeatures);
        }
    }

    private void renderUserLocationIndicator(@Nullable Location location, float bearing) {
        if (location == null) {
            setFeatures(USER_ROUTE_SOURCE_ID, Collections.emptyList());
            return;
        }
        setFeatures(USER_ROUTE_SOURCE_ID, Collections.singletonList(
                createBearingFeature(
                        Point.fromLngLat(location.longitude, location.latitude),
                        bearing)));
    }

    private void renderRouteSheet(@NonNull RouteSession session) {
        if (placeDetailSheet == null || routeDetailSheet == null || bottomSheetBehavior == null) {
            return;
        }

        if (!session.isVisible()) {
            placeDetailSheet.setVisibility(View.GONE);
            routeDetailSheet.setVisibility(View.GONE);
            if (followRouteBar != null) {
                followRouteBar.setVisibility(View.GONE);
            }
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            stopFollowLocationUpdates();
            updateFloatingControlsPosition();
            return;
        }

        if (session.following) {
            routeDetailSheet.setVisibility(View.GONE);
            placeDetailSheet.setVisibility(View.GONE);
            if (followRouteBar != null) {
                followRouteBar.setVisibility(View.VISIBLE);
            }
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        } else {
            showRouteSheetOnly();
            if (followRouteBar != null) {
                followRouteBar.setVisibility(View.GONE);
            }
        }
        Place destination = session.destinationPlace;
        if (tvRouteTitle != null) {
            String title = destination != null && !TextUtils.isEmpty(destination.name)
                    ? getString(R.string.route_sheet_title) + " to " + destination.name
                    : getString(R.string.route_sheet_title);
            tvRouteTitle.setText(title);
        }
        if (tvRouteAddress != null) {
            tvRouteAddress.setText(destination != null && !TextUtils.isEmpty(destination.address)
                    ? destination.address
                    : "");
        }
        if (tvRouteEta != null) {
            tvRouteEta.setText(!TextUtils.isEmpty(session.durationText)
                    ? "Estimated: " + session.durationText
                    : "Estimated: --");
        }
        if (tvRouteDistance != null) {
            tvRouteDistance.setText(!TextUtils.isEmpty(session.distanceText)
                    ? "Distance: " + session.distanceText
                    : "Distance: --");
        }
        if (tvFollowDistanceLeft != null && !TextUtils.isEmpty(session.distanceText)) {
            tvFollowDistanceLeft.setText(session.distanceText);
        }
        if (tvFollowTimeLeft != null && !TextUtils.isEmpty(session.durationText)) {
            tvFollowTimeLeft.setText(session.durationText);
        }
        if (btnFollowRoute != null) {
            boolean canFollow = session.hasRoute();
            btnFollowRoute.setEnabled(canFollow && !session.following);
            btnFollowRoute.setText(session.following
                    ? R.string.following_route
                    : R.string.follow_route);
        }

        if (session.hasRoute()) {
            ensureFollowLocationUpdates();
        } else {
            stopFollowLocationUpdates();
        }
        updateFloatingControlsPosition();
    }

    private void showPlaceSheetOnly() {
        if (placeDetailSheet != null) {
            placeDetailSheet.setVisibility(View.VISIBLE);
        }
        if (routeDetailSheet != null) {
            routeDetailSheet.setVisibility(View.GONE);
        }
        if (followRouteBar != null) {
            followRouteBar.setVisibility(View.GONE);
        }
        updateFloatingControlsPosition();
    }

    private void showRouteSheetOnly() {
        if (placeDetailSheet != null) {
            placeDetailSheet.setVisibility(View.GONE);
        }
        if (routeDetailSheet != null) {
            routeDetailSheet.setVisibility(View.VISIBLE);
        }
        if (bottomSheetBehavior != null) {
            routeDetailSheet.post(() -> {
                if (routeDetailSheet.getHeight() > 0) {
                    bottomSheetBehavior.setPeekHeight(routeDetailSheet.getHeight());
                }
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                updateFloatingControlsPosition();
            });
        }
    }

    private void startFollowingRouteSession() {
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            viewModel.setStatusText(getString(R.string.follow_route_location_required));
            return;
        }

        viewModel.startFollowingRoute();
        ensureFollowLocationUpdates();
    }

    private void stopRouteAndFocusDestination() {
        RouteSession session = viewModel != null ? viewModel.getCurrentRouteSession() : RouteSession.idle();
        Place destination = session.destinationPlace;
        reopeningPlaceAfterRouteStop = true;
        viewModel.cancelRoute();
        if (destination != null && destination.location != null) {
            selectedPlace = destination;
            renderSelectedPlace();
            animateCameraToSelection(new LatLng(
                    destination.location.latitude,
                    destination.location.longitude));
            if (getView() != null) {
                showPlaceBottomSheet(destination, requireView());
            }
        } else {
            reopeningPlaceAfterRouteStop = false;
        }
    }

    private void updateFloatingControlsPosition() {
        View root = getView();
        if (root == null) {
            return;
        }

        ImageButton btnMyLocation = root.findViewById(R.id.btn_my_location);
        if (btnMyLocation == null) {
            return;
        }

        root.post(() -> {
            int rootHeight = root.getHeight();
            if (rootHeight <= 0) {
                return;
            }

            int myLocationDefaultY = rootHeight - dpToPx(100) - btnMyLocation.getHeight();
            int anchorTop = rootHeight;
            if (bottomSheetBehavior != null && bottomSheetBehavior.getState() != BottomSheetBehavior.STATE_HIDDEN
                    && bottomSheetContainer != null) {
                anchorTop = Math.min(anchorTop, bottomSheetContainer.getTop());
            }

            int targetMyLocationY = anchorTop < rootHeight
                    ? anchorTop - btnMyLocation.getHeight() - dpToPx(16)
                    : myLocationDefaultY;
            btnMyLocation.setY(Math.min(myLocationDefaultY, targetMyLocationY));
            if (btnMapCompass != null) {
                int compassDefaultY = myLocationDefaultY - dpToPx(60);
                int targetCompassY = (int) btnMyLocation.getY() - btnMapCompass.getHeight() - dpToPx(12);
                btnMapCompass.setY(Math.min(compassDefaultY, targetCompassY));
            }
        });
    }

    private void ensureFollowLocationUpdates() {
        if (followLocationUpdatesActive || viewModel == null || !viewModel.getCurrentRouteSession().hasRoute()) {
            return;
        }
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationManager locationManager = requireContext().getSystemService(LocationManager.class);
        if (locationManager == null) {
            return;
        }

        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1.5f,
                    followLocationListener,
                    Looper.getMainLooper());
        } catch (Exception ignored) {
            // ignore
        }

        try {
            locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1500L,
                    2.0f,
                    followLocationListener,
                    Looper.getMainLooper());
        } catch (Exception ignored) {
            // ignore
        }

        android.location.Location lastKnown = getBestLastKnownLocation(locationManager);
        if (lastKnown != null) {
            lastKnownUserLocation = new Location(lastKnown.getLatitude(), lastKnown.getLongitude());
            viewModel.updateFollowingLocation(
                    lastKnownUserLocation,
                    lastKnown.hasBearing() ? lastKnown.getBearing() : 0f);
        }
        followLocationUpdatesActive = true;
    }

    private void stopFollowLocationUpdates() {
        if (!followLocationUpdatesActive || getContext() == null) {
            return;
        }

        LocationManager manager = requireContext().getSystemService(LocationManager.class);
        if (manager == null) {
            followLocationUpdatesActive = false;
            return;
        }

        try {
            manager.removeUpdates(followLocationListener);
        } catch (Exception ignored) {
            // Safe no-op.
        }
        followLocationUpdatesActive = false;
    }

    @NonNull
    private List<Feature> createTurnFeatures(@NonNull List<RouteGeometryUtils.TurnMarker> markers) {
        List<Feature> features = new ArrayList<>();
        for (RouteGeometryUtils.TurnMarker marker : markers) {
            features.add(createBearingFeature(marker.location, marker.bearing));
        }
        return features;
    }

    @NonNull
    private Feature createBearingFeature(@NonNull Point point, float bearing) {
        Feature feature = Feature.fromGeometry(point);
        feature.addNumberProperty(PROP_BEARING, bearing);
        return feature;
    }

    @NonNull
    private String formatDistance(double distanceMeters) {
        double safeDistance = Math.max(0d, distanceMeters);
        if (safeDistance < 1000d) {
            return String.format(Locale.getDefault(), "%.0f m", safeDistance);
        }
        return String.format(Locale.getDefault(), "%.1f km", safeDistance / 1000d);
    }

    @NonNull
    private String formatDuration(double durationSeconds) {
        long totalMinutes = Math.max(1L, Math.round(durationSeconds / 60d));
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        if (hours <= 0L) {
            return String.format(Locale.getDefault(), "%d min", totalMinutes);
        }
        if (minutes == 0L) {
            return String.format(Locale.getDefault(), "%d hr", hours);
        }
        return String.format(Locale.getDefault(), "%d hr %d min", hours, minutes);
    }

    private void fitCameraToUserAndRemainingRoute(@NonNull Location userLocation,
            @NonNull List<Point> remainingPoints) {
        if (mapLibreMap == null || remainingPoints.isEmpty()) {
            return;
        }

        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        boundsBuilder.include(new LatLng(userLocation.latitude, userLocation.longitude));
        for (Point point : remainingPoints) {
            boundsBuilder.include(new LatLng(point.latitude(), point.longitude()));
        }

        try {
            mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngBounds(
                    boundsBuilder.build(),
                    dpToPx(48),
                    dpToPx(120),
                    dpToPx(48),
                    dpToPx(156)));
        } catch (Exception ignored) {
            // Ignore degenerate bounds.
        }
    }

    @Nullable
    private Feature parseRouteFeature(@NonNull String geometryJson) {
        JSONObject raw = parseJsonObject(geometryJson);
        if (raw == null) {
            return null;
        }

        JSONObject geometry = extractLineStringGeometry(raw);
        List<Point> points;
        if (geometry != null) {
            points = extractLineStringPoints(geometry);
        } else {
            String encoded = extractEncodedGeometry(raw);
            points = decodePolylineBestEffort(encoded);
        }

        if (points.size() < 2) {
            return null;
        }

        return Feature.fromGeometry(LineString.fromLngLats(points));
    }

    @Nullable
    private JSONObject extractLineStringGeometry(@NonNull JSONObject raw) {
        if ("LineString".equalsIgnoreCase(raw.optString("type"))) {
            return raw;
        }

        if ("Feature".equalsIgnoreCase(raw.optString("type"))) {
            JSONObject featureGeometry = raw.optJSONObject("geometry");
            if (featureGeometry != null) {
                JSONObject extracted = extractLineStringGeometry(featureGeometry);
                if (extracted != null) {
                    return extracted;
                }
            }
        }

        if ("FeatureCollection".equalsIgnoreCase(raw.optString("type"))) {
            org.json.JSONArray features = raw.optJSONArray("features");
            if (features != null) {
                for (int i = 0; i < features.length(); i++) {
                    JSONObject feature = features.optJSONObject(i);
                    if (feature == null) {
                        continue;
                    }
                    JSONObject featureGeometry = feature.optJSONObject("geometry");
                    if (featureGeometry == null) {
                        continue;
                    }
                    JSONObject extracted = extractLineStringGeometry(featureGeometry);
                    if (extracted != null) {
                        return extracted;
                    }
                }
            }
        }

        JSONObject nestedGeometry = raw.optJSONObject("geometry");
        if (nestedGeometry != null && "LineString".equalsIgnoreCase(nestedGeometry.optString("type"))) {
            return nestedGeometry;
        }

        String nestedGeometryString = optNullableString(raw, "geometry");
        if (!nestedGeometryString.isBlank()) {
            JSONObject nestedGeometryJson = parseJsonObject(nestedGeometryString);
            if (nestedGeometryJson != null) {
                JSONObject extracted = extractLineStringGeometry(nestedGeometryJson);
                if (extracted != null) {
                    return extracted;
                }
            }
        }

        org.json.JSONArray routes = raw.optJSONArray("routes");
        if (routes != null && routes.length() > 0) {
            JSONObject firstRoute = routes.optJSONObject(0);
            if (firstRoute != null) {
                JSONObject routeGeometry = firstRoute.optJSONObject("geometry");
                if (routeGeometry != null && "LineString".equalsIgnoreCase(routeGeometry.optString("type"))) {
                    return routeGeometry;
                }

                String routeGeometryString = optNullableString(firstRoute, "geometry");
                if (!routeGeometryString.isBlank()) {
                    JSONObject routeGeometryJson = parseJsonObject(routeGeometryString);
                    if (routeGeometryJson != null
                            && "LineString".equalsIgnoreCase(routeGeometryJson.optString("type"))) {
                        return routeGeometryJson;
                    }
                }
            }
        }

        org.json.JSONArray paths = raw.optJSONArray("paths");
        if (paths != null && paths.length() > 0) {
            JSONObject firstPath = paths.optJSONObject(0);
            if (firstPath != null) {
                JSONObject points = firstPath.optJSONObject("points");
                if (points != null && "LineString".equalsIgnoreCase(points.optString("type"))) {
                    return points;
                }
            }
        }

        JSONObject points = raw.optJSONObject("points");
        if (points != null && "LineString".equalsIgnoreCase(points.optString("type"))) {
            return points;
        }

        return null;
    }

    @Nullable
    private JSONObject parseJsonObject(@Nullable String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return null;
        }

        try {
            Object decoded = new JSONTokener(rawJson).nextValue();
            if (decoded instanceof JSONObject) {
                return (JSONObject) decoded;
            }
            if (decoded instanceof String) {
                String nested = ((String) decoded).trim();
                if (nested.startsWith("{")) {
                    return new JSONObject(nested);
                }
            }
        } catch (JSONException ignored) {
            return null;
        }

        return null;
    }

    @NonNull
    private List<Point> extractLineStringPoints(@NonNull JSONObject geometry) {
        if (!"LineString".equalsIgnoreCase(geometry.optString("type"))) {
            return Collections.emptyList();
        }

        Object coordinatesValue = geometry.opt("coordinates");
        if (coordinatesValue instanceof org.json.JSONArray) {
            return parseCoordinateArray((org.json.JSONArray) coordinatesValue);
        }

        if (coordinatesValue instanceof String) {
            return decodePolylineBestEffort((String) coordinatesValue);
        }

        return Collections.emptyList();
    }

    @Nullable
    private String extractEncodedGeometry(@NonNull JSONObject raw) {
        String geometry = optNullableString(raw, "geometry");
        if (!geometry.isBlank() && !geometry.trim().startsWith("{")) {
            return geometry;
        }

        String points = optNullableString(raw, "points");
        if (!points.isBlank() && !points.trim().startsWith("{")) {
            return points;
        }

        org.json.JSONArray routes = raw.optJSONArray("routes");
        if (routes != null && routes.length() > 0) {
            JSONObject firstRoute = routes.optJSONObject(0);
            if (firstRoute != null) {
                String encoded = optNullableString(firstRoute, "geometry");
                if (!encoded.isBlank() && !encoded.trim().startsWith("{")) {
                    return encoded;
                }
            }
        }

        org.json.JSONArray paths = raw.optJSONArray("paths");
        if (paths != null && paths.length() > 0) {
            JSONObject firstPath = paths.optJSONObject(0);
            if (firstPath != null) {
                String encoded = optNullableString(firstPath, "points");
                if (!encoded.isBlank() && !encoded.trim().startsWith("{")) {
                    return encoded;
                }
            }
        }

        return null;
    }

    @NonNull
    private String optNullableString(@NonNull JSONObject object, @NonNull String key) {
        if (!object.has(key) || object.isNull(key)) {
            return "";
        }
        return object.optString(key, "");
    }

    @NonNull
    private List<Point> parseCoordinateArray(@NonNull org.json.JSONArray coordinates) {
        if (coordinates.length() < 2) {
            return Collections.emptyList();
        }

        List<Point> points = new ArrayList<>();
        for (int i = 0; i < coordinates.length(); i++) {
            Object coordinate = coordinates.opt(i);
            Point parsed = null;

            if (coordinate instanceof org.json.JSONArray) {
                parsed = parseCoordinatePair((org.json.JSONArray) coordinate);
            } else if (coordinate instanceof JSONObject) {
                parsed = parseCoordinateObject((JSONObject) coordinate);
            }

            if (parsed != null) {
                points.add(parsed);
            }
        }

        return points;
    }

    @Nullable
    private Point parseCoordinatePair(@NonNull org.json.JSONArray coordinatePair) {
        if (coordinatePair.length() < 2) {
            return null;
        }

        double first = coordinatePair.optDouble(0, Double.NaN);
        double second = coordinatePair.optDouble(1, Double.NaN);
        if (Double.isNaN(first) || Double.isNaN(second)) {
            return null;
        }

        double lon = first;
        double lat = second;

        // Handle [lat, lon] payloads by swapping when needed.
        if (Math.abs(lat) > 90.0 && Math.abs(lon) <= 90.0) {
            lon = second;
            lat = first;
        }

        if (Math.abs(lat) > 90.0 || Math.abs(lon) > 180.0) {
            return null;
        }

        return Point.fromLngLat(lon, lat);
    }

    @Nullable
    private Point parseCoordinateObject(@NonNull JSONObject coordinateObject) {
        Double lat = firstValidNumber(
                coordinateObject.opt("lat"),
                coordinateObject.opt("latitude"));
        Double lon = firstValidNumber(
                coordinateObject.opt("lon"),
                coordinateObject.opt("lng"),
                coordinateObject.opt("longitude"));

        if (lat == null || lon == null) {
            return null;
        }

        if (Math.abs(lat) > 90.0 || Math.abs(lon) > 180.0) {
            return null;
        }

        return Point.fromLngLat(lon, lat);
    }

    @Nullable
    private Double firstValidNumber(@Nullable Object... candidates) {
        if (candidates != null) {
            for (Object candidate : candidates) {
                if (candidate == null) {
                    continue;
                }
                if (candidate instanceof Number) {
                    return ((Number) candidate).doubleValue();
                }
                if (candidate instanceof String) {
                    try {
                        return Double.parseDouble(((String) candidate).trim());
                    } catch (NumberFormatException ignored) {
                        // continue checking remaining candidates
                    }
                }
            }
        }
        return null;
    }

    @NonNull
    private List<Point> decodePolylineBestEffort(@Nullable String encodedPolyline) {
        if (encodedPolyline == null || encodedPolyline.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<Point> precision5 = decodePolyline(encodedPolyline, 1e5);
        if (isValidRoutePoints(precision5)) {
            return precision5;
        }

        List<Point> precision6 = decodePolyline(encodedPolyline, 1e6);
        if (isValidRoutePoints(precision6)) {
            return precision6;
        }

        return Collections.emptyList();
    }

    private boolean isValidRoutePoints(@NonNull List<Point> points) {
        return points.size() >= 2;
    }

    @NonNull
    private List<Point> decodePolyline(@NonNull String encoded, double precision) {
        List<Point> path = new ArrayList<>();
        int index = 0;
        int latitude = 0;
        int longitude = 0;

        try {
            while (index < encoded.length()) {
                int[] latResult = decodePolylineValue(encoded, index);
                if (latResult == null) {
                    break;
                }
                latitude += latResult[0];
                index = latResult[1];

                int[] lonResult = decodePolylineValue(encoded, index);
                if (lonResult == null) {
                    break;
                }
                longitude += lonResult[0];
                index = lonResult[1];

                double lat = latitude / precision;
                double lon = longitude / precision;
                if (Math.abs(lat) <= 90.0 && Math.abs(lon) <= 180.0) {
                    path.add(Point.fromLngLat(lon, lat));
                }
            }
        } catch (Exception ignored) {
            return Collections.emptyList();
        }

        return path;
    }

    @Nullable
    private int[] decodePolylineValue(@NonNull String encoded, int startIndex) {
        int result = 0;
        int shift = 0;
        int index = startIndex;

        while (index < encoded.length()) {
            int b = encoded.charAt(index++) - 63;
            result |= (b & 0x1f) << shift;
            shift += 5;
            if (b < 0x20) {
                int delta = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
                return new int[] { delta, index };
            }
        }

        return null;
    }

    @NonNull
    private String truncateForLog(@NonNull String value) {
        int maxLength = 300;
        return value.length() > maxLength
                ? value.substring(0, maxLength) + "..."
                : value;
    }

    private void fitCameraToRoute(@NonNull Feature routeFeature) {
        if (!(routeFeature.geometry() instanceof LineString) || mapLibreMap == null) {
            return;
        }

        List<Point> routePoints = ((LineString) routeFeature.geometry()).coordinates();
        if (routePoints.size() < 2) {
            return;
        }

        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        for (Point point : routePoints) {
            boundsBuilder.include(new LatLng(point.latitude(), point.longitude()));
        }

        try {
            mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 180));
        } catch (Exception ignored) {
            // Ignore camera update failures for degenerate bounds.
        }
    }

    private void fitCameraToRouteVisibleArea(@NonNull Feature routeFeature) {
        if (!(routeFeature.geometry() instanceof LineString) || mapLibreMap == null) {
            return;
        }

        List<Point> routePoints = ((LineString) routeFeature.geometry()).coordinates();
        if (routePoints.size() < 2) {
            return;
        }

        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        for (Point point : routePoints) {
            boundsBuilder.include(new LatLng(point.latitude(), point.longitude()));
        }

        int bottomPadding = dpToPx(180);
        if (routeDetailSheet != null && routeDetailSheet.getVisibility() == View.VISIBLE
                && routeDetailSheet.getHeight() > 0) {
            bottomPadding = routeDetailSheet.getHeight() + dpToPx(28);
        }

        try {
            mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngBounds(
                    boundsBuilder.build(),
                    dpToPx(48),
                    dpToPx(96),
                    dpToPx(48),
                    bottomPadding));
        } catch (Exception ignored) {
            // Ignore camera update failures for degenerate bounds.
        }
    }

    @NonNull
    private Feature createFeatureForPlace(@NonNull LatLng position,
            @NonNull Place place) {
        Feature feature = Feature.fromGeometry(Point.fromLngLat(
                position.getLongitude(),
                position.getLatitude()));
        feature.addStringProperty(PROP_PLACE_ID,
                place.id != null ? place.id : UUID.randomUUID().toString());
        feature.addStringProperty(PROP_NAME,
                place.name != null ? place.name : "Selected Location");
        feature.addStringProperty(PROP_ADDRESS,
                place.address != null ? place.address : "Address unavailable");
        feature.addNumberProperty(PROP_RATING, place.rating);
        feature.addNumberProperty(PROP_LAT, position.getLatitude());
        feature.addNumberProperty(PROP_LNG, position.getLongitude());
        return feature;
    }

    @Nullable
    private Place findRenderedPlaceAt(@NonNull LatLng point) {
        if (mapLibreMap == null) {
            return null;
        }

        PointF screenPoint = mapLibreMap.getProjection().toScreenLocation(point);
        List<Feature> features = mapLibreMap.queryRenderedFeatures(screenPoint,
                SELECTED_LAYER_ID,
                FAVORITE_LAYER_ID,
                SEARCH_LAYER_ID);
        if (features.isEmpty()) {
            return null;
        }

        return featureToPlace(features.get(0));
    }

    @Nullable
    private PoiTap findStylePoiAt(@NonNull LatLng tapPoint) {
        if (mapLibreMap == null) {
            return null;
        }

        PointF screenPoint = mapLibreMap.getProjection().toScreenLocation(tapPoint);
        List<Feature> features = mapLibreMap.queryRenderedFeatures(screenPoint);
        if (features.isEmpty()) {
            return null;
        }

        for (Feature feature : features) {
            if (feature == null || !(feature.geometry() instanceof Point)) {
                continue;
            }
            Point poiPoint = (Point) feature.geometry();
            String poiName = firstNonEmptyProperty(feature);
            return new PoiTap(
                    new LatLng(poiPoint.latitude(), poiPoint.longitude()),
                    poiName);
        }

        return null;
    }

    @Nullable
    private String firstNonEmptyProperty(@NonNull Feature feature) {
        for (String key : new String[] {
                "name",
                "name_en",
                "name:en",
                "name:latin",
                "name:vi",
                "ref",
                "class"
        }) {
            if (!feature.hasProperty(key)) {
                continue;
            }
            String value = feature.getStringProperty(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    @Nullable
    private Place featureToPlace(@NonNull Feature feature) {
        if (!feature.hasProperty(PROP_LAT) || !feature.hasProperty(PROP_LNG)) {
            return null;
        }

        Number lat = feature.getNumberProperty(PROP_LAT);
        Number lng = feature.getNumberProperty(PROP_LNG);
        if (lat == null || lng == null) {
            return null;
        }

        String id = feature.hasProperty(PROP_PLACE_ID)
                ? feature.getStringProperty(PROP_PLACE_ID)
                : UUID.randomUUID().toString();
        String name = feature.hasProperty(PROP_NAME)
                ? feature.getStringProperty(PROP_NAME)
                : "Selected Location";
        String address = feature.hasProperty(PROP_ADDRESS)
                ? feature.getStringProperty(PROP_ADDRESS)
                : "Address unavailable";
        Number ratingNumber = feature.hasProperty(PROP_RATING)
                ? feature.getNumberProperty(PROP_RATING)
                : 0.0;
        double rating = ratingNumber != null ? ratingNumber.doubleValue() : 0.0;

        return new Place(
                id,
                name,
                address,
                rating,
                new Location(lat.doubleValue(), lng.doubleValue()));
    }

    private void fetchAddressAndShowDetails(LatLng latLng,
            @Nullable String preferredName) {
        Place quickPlace = buildInstantTapPlace(latLng, preferredName);
        if (mapLibreMap != null) {
            selectedPlace = quickPlace;
            renderSelectedPlace();
            animateCameraToSelection(latLng);
            showPlaceBottomSheet(quickPlace, requireView());
        }

        if (!isOnlineNow) {
            return;
        }

        new Thread(() -> {
            try {
                List<Address> addresses = reverseGeocodeWithOsmFirst(
                        latLng.getLatitude(), latLng.getLongitude());

                String finalName = preferredName;
                String addressText = "Unknown Address";

                if (!addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    addressText = address.getAddressLine(0);

                    if (TextUtils.isEmpty(finalName)) {
                        finalName = address.getFeatureName();
                        if (finalName != null && finalName.equals(addressText)) {
                            finalName = null;
                        }
                    }
                }

                if (TextUtils.isEmpty(finalName)) {
                    finalName = quickPlace.name;
                }

                Place clickedPlace = new Place(
                        UUID.randomUUID().toString(),
                        finalName,
                        addressText != null ? addressText : quickPlace.address,
                        0.0,
                        new Location(latLng.getLatitude(), latLng.getLongitude()));

                requireActivity().runOnUiThread(() -> {
                    if (mapLibreMap == null) {
                        return;
                    }

                    if (!isCurrentSelection(latLng)) {
                        return;
                    }

                    selectedPlace = clickedPlace;
                    renderSelectedPlace();
                    showPlaceBottomSheet(clickedPlace, requireView());
                });
            } catch (Exception e) {
                Log.e(TAG, "Geocoding failed", e);
            }
        }).start();
    }

    @NonNull
    private Place buildInstantTapPlace(@NonNull LatLng latLng, @Nullable String preferredName) {
        String resolvedName = !TextUtils.isEmpty(preferredName)
                ? preferredName
                : "Selected Location";
        String resolvedAddress = String.format(
                Locale.getDefault(),
                "%.5f, %.5f",
                latLng.getLatitude(),
                latLng.getLongitude());

        return new Place(
                UUID.randomUUID().toString(),
                resolvedName,
                resolvedAddress,
                0.0,
                new Location(latLng.getLatitude(), latLng.getLongitude()));
    }

    private boolean isCurrentSelection(@NonNull LatLng latLng) {
        return selectedPlace != null
                && selectedPlace.location != null
                && Math.abs(selectedPlace.location.latitude - latLng.getLatitude()) < 1e-6
                && Math.abs(selectedPlace.location.longitude - latLng.getLongitude()) < 1e-6;
    }

    private List<Address> reverseGeocodeWithOsmFirst(double latitude,
            double longitude) {
        List<Address> nominatimResults = reverseGeocodeWithNominatim(
                latitude, longitude);
        if (!nominatimResults.isEmpty()) {
            return nominatimResults;
        }

        Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
        try {
            List<Address> fallback = geocoder.getFromLocation(
                    latitude, longitude, 1);
            return fallback != null ? fallback : new ArrayList<>();
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private List<Address> reverseGeocodeWithNominatim(double latitude,
            double longitude) {
        HttpURLConnection connection = null;
        try {
            String requestUrl = NOMINATIM_BASE_URL
                    + "/reverse?format=jsonv2&addressdetails=1&lat="
                    + latitude + "&lon=" + longitude;

            connection = (HttpURLConnection) URI.create(requestUrl)
                    .toURL()
                    .openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", OSM_USER_AGENT);
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(5000);

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return new ArrayList<>();
            }

            try (InputStream inputStream = connection.getInputStream()) {
                String json = readUtf8(inputStream);
                return getAddresses(latitude, longitude, json);
            }
        } catch (Exception e) {
            return new ArrayList<>();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @NonNull
    private static List<Address> getAddresses(double latitude, double longitude, String json) throws JSONException {
        JSONObject obj = new JSONObject(json);
        String displayName = obj.optString("display_name", "");
        String name = obj.optString("name", "");

        Address address = new Address(Locale.getDefault());
        address.setLatitude(latitude);
        address.setLongitude(longitude);
        if (!displayName.isBlank()) {
            address.setAddressLine(0, displayName);
        }
        if (!name.isBlank()) {
            address.setFeatureName(name);
        }

        List<Address> results = new ArrayList<>();
        results.add(address);
        return results;
    }

    private String readUtf8(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private void showPlaceBottomSheet(Place place, View root) {
        if (viewModel.hasActiveRouteSession()) {
            viewModel.setStatusText(getString(R.string.route_active_place_sheet_blocked));
            return;
        }

        viewModel.cacheViewedPlace(place);
        showPlaceSheetOnly();

        TextView tvName = root.findViewById(R.id.tv_place_name);
        TextView tvAddress = root.findViewById(R.id.tv_place_address);
        TextView tvRating = root.findViewById(R.id.tv_place_rating);
        ImageButton btnAddFavorite = root.findViewById(R.id.btn_add_favorite);
        MaterialButton btnSharePlace = root.findViewById(R.id.btn_share_place);
        MaterialButton btnRoutePlace = root.findViewById(R.id.btn_navigate_place);

        tvName.setText(place.name);
        tvAddress.setText(place.address);
        if (place.rating > 0) {
            tvRating.setText(String.format(Locale.getDefault(), "* %.1f",
                    place.rating));
        } else {
            tvRating.setText(R.string.default_rating);
        }

        btnAddFavorite.setEnabled(true);
        btnSharePlace.setEnabled(true);
        btnRoutePlace.setEnabled(true);

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

        btnRoutePlace.setOnClickListener(v -> routeToPlace(place));
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

    private void routeToPlace(Place place) {
        if (place == null || place.location == null) {
            viewModel.setStatusText(getString(R.string.route_location_required));
            return;
        }

        Location origin = resolveRoutingOrigin();
        if (origin == null) {
            showRouteSheetOnly();
            renderRouteSheet(RouteSession.error(place, getString(R.string.route_location_required)));
            viewModel.setStatusText(getString(R.string.route_location_required));
            return;
        }

        showRouteSheetOnly();

        if (!isInVietnamBounds(origin) || !isInVietnamBounds(place.location)) {
            clearRouteFeatures();
            renderRouteSheet(RouteSession.error(place, getString(R.string.route_not_supported_outside_vietnam)));
            viewModel.setStatusText(getString(R.string.route_not_supported_outside_vietnam));
            return;
        }

        lastKnownUserLocation = origin;
        renderRouteSheet(RouteSession.loading(place));
        viewModel.beginRoutePreview(place, origin, place.location);
    }

    private boolean isInVietnamBounds(@NonNull Location location) {
        return location.latitude >= VIETNAM_MIN_LAT
                && location.latitude <= VIETNAM_MAX_LAT
                && location.longitude >= VIETNAM_MIN_LON
                && location.longitude <= VIETNAM_MAX_LON;
    }

    @Nullable
    private Location resolveRoutingOrigin() {
        if (lastKnownUserLocation != null) {
            return lastKnownUserLocation;
        }

        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        LocationManager manager = requireContext().getSystemService(LocationManager.class);
        if (manager == null) {
            return null;
        }

        android.location.Location bestLastKnown = getBestLastKnownLocation(manager);
        if (bestLastKnown == null) {
            return null;
        }

        lastKnownUserLocation = new Location(
                bestLastKnown.getLatitude(),
                bestLastKnown.getLongitude());
        lastKnownUserBearingDegrees = bestLastKnown.hasBearing()
                ? bestLastKnown.getBearing()
                : lastKnownUserBearingDegrees;
        renderUserLocationIndicator(lastKnownUserLocation, lastKnownUserBearingDegrees);
        return lastKnownUserLocation;
    }

    @Nullable
    private Location resolveDownloadLocation() {
        Location origin = resolveRoutingOrigin();
        if (origin != null) {
            return origin;
        }

        if (mapLibreMap == null || mapLibreMap.getCameraPosition() == null
                || mapLibreMap.getCameraPosition().target == null) {
            return null;
        }

        LatLng target = mapLibreMap.getCameraPosition().target;
        return new Location(target.getLatitude(), target.getLongitude());
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
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            viewModel.setStatusText("Location permission is required.");
            return;
        }

        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            viewModel.setStatusText("Location permission is required.");
            return;
        }

        LocationManager locationManager = requireContext()
                .getSystemService(LocationManager.class);
        if (locationManager == null) {
            viewModel.setStatusText("Location service unavailable.");
            return;
        }

        android.location.Location location = getBestLastKnownLocation(locationManager);

        if (location != null) {
            centerMapOnLocation(location);
        } else {
            viewModel.setStatusText("Getting your current location...");
            requestSingleLocationUpdate(locationManager);
        }
    }

    @RequiresPermission(allOf = { Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION })
    @Nullable
    private android.location.Location getBestLastKnownLocation(@NonNull LocationManager locationManager) {
        android.location.Location best = null;
        String[] providers = new String[] {
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
        };

        for (String provider : providers) {
            try {
                android.location.Location candidate = locationManager.getLastKnownLocation(provider);
                if (candidate == null) {
                    continue;
                }
                if (best == null || candidate.getTime() > best.getTime()) {
                    best = candidate;
                }
            } catch (Exception ignored) {
                // Ignore provider-specific issues and continue with remaining providers.
            }
        }

        return best;
    }

    @RequiresPermission(allOf = { Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION })
    private void requestSingleLocationUpdate(@NonNull LocationManager locationManager) {
        stopSingleLocationUpdates();

        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0L,
                    0f,
                    singleLocationListener,
                    Looper.getMainLooper());
        } catch (Exception ignored) {
            // Provider may be disabled; network provider fallback still applies.
        }

        try {
            locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    0L,
                    0f,
                    singleLocationListener,
                    Looper.getMainLooper());
        } catch (Exception ignored) {
            // If both providers fail, timeout handler will show a message.
        }

        locationHandler.postDelayed(locationTimeoutRunnable, 8000L);
    }

    private void centerMapOnLocation(@NonNull android.location.Location location) {
        lastKnownUserLocation = new Location(location.getLatitude(), location.getLongitude());
        lastKnownUserBearingDegrees = location.hasBearing() ? location.getBearing() : lastKnownUserBearingDegrees;
        renderUserLocationIndicator(lastKnownUserLocation, lastKnownUserBearingDegrees);
        if (mapLibreMap == null) {
            return;
        }
        mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                new LatLng(location.getLatitude(), location.getLongitude()),
                15.0));
    }

    private void stopSingleLocationUpdates() {
        locationHandler.removeCallbacks(locationTimeoutRunnable);
        if (getContext() == null) {
            return;
        }

        LocationManager manager = requireContext().getSystemService(LocationManager.class);
        if (manager == null) {
            return;
        }

        try {
            manager.removeUpdates(singleLocationListener);
        } catch (Exception ignored) {
            // Safe no-op if listener is not registered.
        }
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
        if (viewModel != null && viewModel.getCurrentRouteSession().hasRoute()) {
            ensureFollowLocationUpdates();
        }
    }

    @Override
    public void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        if (mapLibreMap != null) {
            CameraPosition position = mapLibreMap.getCameraPosition();
            viewModel.saveMapState(Objects.requireNonNull(position.target).getLatitude(),
                    position.target.getLongitude(), (float) position.zoom);
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
        stopSingleLocationUpdates();
        if (mapView != null) {
            mapView.removeOnDidFailLoadingMapListener(
                    onDidFailLoadingMapListener);
            if (mapLibreMap != null) {
                mapLibreMap.removeOnCameraMoveListener(onCameraMoveListener);
                mapLibreMap.removeOnCameraIdleListener(onCameraIdleListener);
            }
            mapView.onDestroy();
            mapView = null;
        }
        clearFavoriteMarkers();
        clearSearchResultMarkers();
        clearSelectedMarker();
        clearRouteFeatures();
        stopFollowLocationUpdates();
        bottomSheetContainer = null;
        placeDetailSheet = null;
        routeDetailSheet = null;
        tvRouteTitle = null;
        tvRouteAddress = null;
        tvRouteEta = null;
        tvRouteDistance = null;
        followRouteBar = null;
        tvFollowDistanceLeft = null;
        tvFollowTimeLeft = null;
        btnStopFollowRoute = null;
        btnMapCompass = null;
        btnCancelRoute = null;
        btnFollowRoute = null;
        downloadCityMapLayout = null;
        btnDownloadCityMap = null;
        progressDownloadCityMap = null;
        lastOfflineMapDownloadStatus = null;
        super.onDestroyView();
    }
}
