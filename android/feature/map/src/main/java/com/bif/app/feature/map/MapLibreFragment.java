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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import com.bif.app.core.utils.AppSnackbar;
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
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bif.app.core.utils.DialogUtils;
import com.bif.app.core.utils.UriUtils;
import com.bif.app.domain.model.Favorite;
import com.bif.app.domain.model.Group;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.MapState;
import com.bif.app.domain.model.OfflineMapDownloadState;
import com.bif.app.domain.model.Place;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.json.JSONException;
import org.json.JSONArray;
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
import org.maplibre.android.style.expressions.Expression;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.Property;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.SymbolLayer;
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
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.text.SimpleDateFormat;

import dagger.hilt.android.AndroidEntryPoint;
import timber.log.Timber;

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
    private static final String TRIP_STOP_SOURCE_ID = "trip-stop-source";
    private static final String TRIP_STOP_LAYER_ID = "trip-stop-layer";
    private static final String TRIP_ROUTE_SOURCE_ID = "trip-route-source";
    private static final String TRIP_ROUTE_LAYER_ID = "trip-route-layer";
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
    private static final String PROP_ICON = "icon";
    private static final String PROP_ORDER = "order";
    private static final String PROP_SELECTED = "selected";
    private static final String ARG_LOCATION = "location";
    private static final String ARG_FOCUS_NAME = "focusName";
    private static final String ARG_FOCUS_ADDRESS = "focusAddress";
    private static final String ARG_FOCUS_PLACE_ID = "focusPlaceId";
    private static final String ARG_FOCUS_RATING = "focusRating";
    private static final String ARG_TRIP_STOPS_JSON = "tripStopsJson";
    private static final String ARG_SOURCE_TRIP_ID = "sourceTripId";
    private static final String ARG_SOURCE_TRIP_TITLE = "sourceTripTitle";
    private static final double VIETNAM_MIN_LAT = 8.56;
    private static final double VIETNAM_MAX_LAT = 23.39;
    private static final double VIETNAM_MIN_LON = 102.14;
    private static final double VIETNAM_MAX_LON = 109.46;
    private static final double LOCAL_SEARCH_RADIUS_KM = 15.0;
    private static final double PRIMARY_CLUSTER_RADIUS_KM = 35.0;
    private static final double LOCAL_MIN_ZOOM = 13.0;
    private static final double REMOTE_RESULT_ZOOM = 15.0;
    private static final long REMOTE_TOAST_COOLDOWN_MS = 2500L;
    private static final double SEARCH_MIN_LAT = 8.0;
    private static final double SEARCH_MAX_LAT = 24.0;
    private static final double SEARCH_MIN_LNG = 102.0;
    private static final double SEARCH_MAX_LNG = 110.0;
    private static final double USER_INDICATOR_MIN_VISIBLE_ZOOM = 10.8;
    private static final float HEADING_MIN_DELTA_DEGREES = 1.0f;
    private static final long HEADING_MIN_INTERVAL_MS = 60L;

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
    @Nullable
    private View searchContainer;
    @Nullable
    private ImageButton btnMyLocation;
    @Nullable
    private ImageButton btnTripRouteBack;

    // Review and rating related views
    private RecyclerView rvReviews;
    private com.facebook.shimmer.ShimmerFrameLayout shimmerReviews;
    private com.google.android.material.chip.ChipGroup chipGroupFilters;
    private ReviewAdapter reviewAdapter;
    private List<ReviewItem> allReviews = new ArrayList<>();
    private androidx.recyclerview.widget.LinearSnapHelper snapHelper;

    private final MapLibreMap.OnCameraMoveListener onCameraMoveListener = this::onMapCameraChanged;
    private final MapLibreMap.OnCameraIdleListener onCameraIdleListener = this::onMapCameraChanged;
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
    private LinearProgressIndicator progressSearchPlaces;
    private boolean isOnlineNow;
    @Nullable
    private OfflineMapDownloadState.Status lastOfflineMapDownloadStatus;
    private boolean styleLoadRequested;
    private boolean emulatorRenderModeOptimized;
    private boolean floatingControlsUpdateScheduled;
    @Nullable
    private String lastRemoteToastArea;
    private long lastRemoteToastAtMs;
    @Nullable
    private String lastSearchCameraSignature;
    @Nullable
    private SearchView mapSearchView;
    @Nullable
    private RecyclerView mapSearchHistoryView;
    private Runnable hideHistory = () -> {
    };
    private boolean suppressQueryTextChange;
    private boolean rvReviewsTouchListenerRegistered = false;
    @Nullable
    private Place pendingNavigationPlace;
    @Nullable
    private String pendingNavigationQuery;
    @Nullable
    private String pendingTripStopsJson;
    private boolean tripRouteModeRequested;
    @Nullable
    private String sourceTripId;
    @Nullable
    private String sourceTripTitle;
    private boolean navigationRequestHandled;
    @Nullable
    private Boolean userIndicatorVisibleForZoom;
    @Nullable
    private SensorManager sensorManager;
    @Nullable
    private Sensor headingSensor;
    private boolean headingUpdatesActive;
    private float deviceHeadingDegrees = Float.NaN;
    private long lastHeadingUpdateAtMs;
    private final float[] headingRotationMatrix = new float[9];
    private final float[] headingRemappedMatrix = new float[9];
    private final float[] headingOrientation = new float[3];
    @Nullable
    private View layoutTripStopDetail;
    @Nullable
    private TextView tvTripStopOrderBadge;
    @Nullable
    private TextView tvTripStopTitle;
    @Nullable
    private TextView tvTripStopAddress;
    @Nullable
    private TextView tvTripStopNote;
    @Nullable
    private TextView tvTripStopTime;
    @Nullable
    private ImageButton btnTripStopClose;
    @Nullable
    private ImageButton btnTripStopPrev;
    @Nullable
    private ImageButton btnTripStopNext;
    @Nullable
    private View layoutTripStopNavArrows;

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
        syncSearchUserLocation(lastKnownUserLocation);
        float bearing = resolveLiveHeadingOr(location.hasBearing() ? location.getBearing() : 0f);
        lastKnownUserBearingDegrees = bearing;
        renderUserLocationIndicator(lastKnownUserLocation, bearing);
        if (viewModel != null) {
            viewModel.updateFollowingLocation(lastKnownUserLocation, bearing);
        }
    };

    private final SensorEventListener headingSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor == null || event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) {
                return;
            }

            float heading = computeHeadingDegrees(event.values);
            if (!Float.isFinite(heading)) {
                return;
            }

            if (shouldSkipHeadingUpdate(heading)) {
                return;
            }

            lastHeadingUpdateAtMs = System.currentTimeMillis();
            deviceHeadingDegrees = heading;
            lastKnownUserBearingDegrees = heading;

            if (lastKnownUserLocation != null) {
                renderUserLocationIndicator(lastKnownUserLocation, heading);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // No-op.
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

        private final MapView.OnDidFailLoadingMapListener onDidFailLoadingMapListener =
            errorMessage -> Timber.tag(TAG).e("Map load failed: %s", errorMessage);

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
            Timber.tag(TAG).e(t, "MapView initialization failed");
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
        viewModel.clearPendingStatusText();
        syncSearchUserLocation(lastKnownUserLocation);
        captureNavigationRequest();

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
        searchContainer = view.findViewById(R.id.search_container);
        btnMyLocation = view.findViewById(R.id.btn_my_location);
        btnTripRouteBack = view.findViewById(R.id.btn_trip_route_back);
        layoutTripStopDetail = view.findViewById(R.id.layout_trip_stop_detail);
        tvTripStopOrderBadge = view.findViewById(R.id.tv_trip_stop_order_badge);
        tvTripStopTitle = view.findViewById(R.id.tv_trip_stop_title);
        tvTripStopAddress = view.findViewById(R.id.tv_trip_stop_address);
        tvTripStopNote = view.findViewById(R.id.tv_trip_stop_note);
        tvTripStopTime = view.findViewById(R.id.tv_trip_stop_time);
        btnTripStopClose = view.findViewById(R.id.btn_trip_stop_close);
        btnTripStopPrev = view.findViewById(R.id.btn_trip_stop_prev);
        btnTripStopNext = view.findViewById(R.id.btn_trip_stop_next);
        layoutTripStopNavArrows = view.findViewById(R.id.layout_trip_stop_nav_arrows);

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
        if (btnTripRouteBack != null) {
            btnTripRouteBack.setOnClickListener(v -> navigateBackFromTripRouteMode());
        }
        if (btnTripStopClose != null) {
            btnTripStopClose.setOnClickListener(v -> viewModel.dismissTripStopSelection());
        }
        if (btnTripStopPrev != null) {
            btnTripStopPrev.setOnClickListener(v -> viewModel.selectPreviousTripStop());
        }
        if (btnTripStopNext != null) {
            btnTripStopNext.setOnClickListener(v -> viewModel.selectNextTripStop());
        }

        if (mapView == null) {
            viewModel.setStatusText("Map initialization failed on this device.");
        }

        // Initialize adapter
        reviewAdapter = new ReviewAdapter(new ReviewAdapter.OnReviewInteractionListener() {
            @Override
            public void onAddReviewClicked() {
                showAddReviewDialog(null);
            }

            @Override
            public void onEditReviewClicked(com.bif.app.domain.model.Review review) {
                showAddReviewDialog(review);
            }
        });

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
                AppSnackbar.show(requireContext(), text);
            }
        });

        viewModel.routeSession.observe(getViewLifecycleOwner(), this::renderRouteSession);
        observeTripOverlayState();

        setupSearchUi(view);
        applyTripRouteModeUi(view);
        installTripRouteModeBackHandler();
        progressSearchPlaces = view.findViewById(R.id.progress_search_places);

        viewModel.isSearchingPlaces.observe(getViewLifecycleOwner(), searching -> {
            if (progressSearchPlaces == null) {
                return;
            }
            progressSearchPlaces.setVisibility(Boolean.TRUE.equals(searching)
                    ? View.VISIBLE
                    : View.GONE);
        });

        if (btnMyLocation != null) {
            btnMyLocation.setOnClickListener(v -> {
                RouteSession session = viewModel.getCurrentRouteSession();
            if (session.hasRoute()) {
                Location routeAnchorLocation = session.lastKnownLocation != null
                    ? session.lastKnownLocation
                    : lastKnownUserLocation;
                List<Point> routePoints = resolveRoutePointsForSession(session);
                if (routeAnchorLocation != null && routePoints.size() >= 2) {
                    RouteGeometryUtils.RouteProgress progress = RouteGeometryUtils.computeRouteProgress(
                    routePoints,
                    routeAnchorLocation);
                List<Point> remainingPoints = progress.remainingPoints.size() >= 2
                    ? progress.remainingPoints
                    : routePoints;
                fitCameraToUserAndRemainingRoute(routeAnchorLocation, remainingPoints);
                return;
                }
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

        viewModel.currentPlaceReviews.observe(getViewLifecycleOwner(), reviews -> {
            updateReviewList(reviews, viewModel.currentMyReview.getValue());
        });

        viewModel.currentMyReview.observe(getViewLifecycleOwner(), myReview -> {
            updateReviewList(viewModel.currentPlaceReviews.getValue(), myReview);
        });

        if (mapView != null) {
            mapView.addOnDidFailLoadingMapListener(onDidFailLoadingMapListener);
            mapView.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull MapLibreMap mapLibreMap) {
        if (styleLoadRequested) {
            Timber.tag(TAG).d("Map style already requested; skipping duplicate setStyle call");
            return;
        }

        this.mapLibreMap = mapLibreMap;
        styleLoadRequested = true;
        optimizeRenderModeForEmulatorIfNeeded();

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

                if (isTripRouteModeActive()) {
                    return true;
                }

                MapViewModel.TripStopOverlay tappedStop = findTripStopAt(point);
                if (tappedStop != null) {
                    List<MapViewModel.TripStopOverlay> stops = viewModel.tripStopOverlay.getValue();
                    if (stops != null && !stops.isEmpty()) {
                        int selectedIndex = 0;
                        for (int i = 0; i < stops.size(); i++) {
                            if (stops.get(i).orderIndex == tappedStop.orderIndex) {
                                selectedIndex = i;
                                break;
                            }
                        }
                        viewModel.selectTripStop(selectedIndex);
                    }
                    return true;
                }

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
            applyPendingNavigationRequest();
        });

    }

    private void optimizeRenderModeForEmulatorIfNeeded() {
        if (mapView == null || emulatorRenderModeOptimized || !isRunningOnEmulator()) {
            return;
        }

        emulatorRenderModeOptimized = true;
        boolean applied = tryApplyRenderModeByField("RENDERMODE_WHEN_DIRTY");
        if (!applied) {
            applied = tryApplyRenderModeByField("RENDERMODE_CONTINUOUS");
        }

        if (applied) {
            Timber.tag(TAG).d("Emulator render mode optimization applied");
        } else {
            Timber.tag(TAG).d("Emulator render mode optimization not available on this MapView");
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            mapView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            Timber.tag(TAG).d("Applied software layer for emulator API 29 or lower");
        }
    }

    private boolean tryApplyRenderModeByField(@NonNull String fieldName) {
        if (mapView == null) {
            return false;
        }

        try {
            Class<?> mapViewClass = mapView.getClass();
            java.lang.reflect.Field modeField = mapViewClass.getField(fieldName);
            int mode = modeField.getInt(null);
            java.lang.reflect.Method setRenderMode = mapViewClass.getMethod("setRenderMode", int.class);
            setRenderMode.invoke(mapView, mode);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isRunningOnEmulator() {
        String fingerprint = safeLower(Build.FINGERPRINT);
        String model = safeLower(Build.MODEL);
        String brand = safeLower(Build.BRAND);
        String device = safeLower(Build.DEVICE);
        String product = safeLower(Build.PRODUCT);

        return fingerprint.startsWith("generic")
                || fingerprint.contains("emulator")
                || fingerprint.contains("sdk_gphone")
                || model.contains("emulator")
                || model.contains("android sdk built for x86")
                || model.contains("sdk_gphone")
                || (brand.startsWith("generic") && device.startsWith("generic"))
                || product.contains("sdk")
                || product.contains("emulator");
    }

    @NonNull
    private String safeLower(@Nullable String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }

    private void captureNavigationRequest() {
        if (navigationRequestHandled) {
            return;
        }

        navigationRequestHandled = true;
        Bundle args = getArguments();
        if (args == null) {
            return;
        }

        String locationQuery = args.getString(ARG_LOCATION, "").trim();
        String focusName = args.getString(ARG_FOCUS_NAME, "").trim();
        String focusAddress = args.getString(ARG_FOCUS_ADDRESS, "").trim();
        String focusPlaceId = args.getString(ARG_FOCUS_PLACE_ID, "").trim();
        String focusRatingRaw = args.getString(ARG_FOCUS_RATING, "").trim();
        String tripStopsJson = args.getString(ARG_TRIP_STOPS_JSON, "").trim();
        sourceTripId = args.getString(ARG_SOURCE_TRIP_ID, "").trim();
        sourceTripTitle = args.getString(ARG_SOURCE_TRIP_TITLE, "").trim();

        if (!tripStopsJson.isEmpty()) {
            pendingTripStopsJson = tripStopsJson;
            tripRouteModeRequested = true;
        }

        Point coordinatePoint = parseCoordinateQuery(locationQuery);
        if (coordinatePoint != null) {
            double lat = coordinatePoint.latitude();
            double lng = coordinatePoint.longitude();
            double resolvedRating = parseFocusRating(focusRatingRaw);
            String resolvedName = !focusName.isEmpty()
                    ? focusName
                    : getString(R.string.default_place_name);
            String resolvedAddress = !focusAddress.isEmpty()
                    ? focusAddress
                    : String.format(Locale.getDefault(), "%.5f, %.5f", lat, lng);
            String resolvedPlaceId = !focusPlaceId.isEmpty()
                ? focusPlaceId
                : buildStablePlaceId(lat, lng);
            pendingNavigationPlace = new Place(
                resolvedPlaceId,
                    resolvedName,
                    resolvedAddress,
                resolvedRating,
                    new Location(lat, lng));
            pendingNavigationQuery = null;
            return;
        }

        if (!locationQuery.isEmpty()) {
            pendingNavigationQuery = locationQuery;
        }
    }

    private double parseFocusRating(@Nullable String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return 0.0;
        }
        try {
            double parsed = Double.parseDouble(rawValue.trim());
            return Double.isFinite(parsed) ? parsed : 0.0;
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    @Nullable
    private Point parseCoordinateQuery(@Nullable String rawQuery) {
        if (rawQuery == null) {
            return null;
        }

        String query = rawQuery.trim();
        if (query.isEmpty()) {
            return null;
        }

        String[] parts = query.split(",");
        if (parts.length != 2) {
            return null;
        }

        try {
            double first = Double.parseDouble(parts[0].trim());
            double second = Double.parseDouble(parts[1].trim());
            if (Math.abs(first) > 90.0 || Math.abs(second) > 180.0) {
                return null;
            }
            return Point.fromLngLat(second, first);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void applyPendingNavigationRequest() {
        if (mapLibreMap == null) {
            return;
        }

        if (pendingTripStopsJson != null && !pendingTripStopsJson.trim().isEmpty()) {
            List<MapViewModel.TripStopOverlay> parsedStops = parseTripStopsJson(pendingTripStopsJson);
            if (!parsedStops.isEmpty()) {
                viewModel.setTripStops(parsedStops);
            }
            pendingTripStopsJson = null;
        }

        if (pendingNavigationPlace != null && pendingNavigationPlace.location != null) {
            selectedPlace = pendingNavigationPlace;
            renderSelectedPlace();
            animateCameraToSelection(new LatLng(
                    pendingNavigationPlace.location.latitude,
                    pendingNavigationPlace.location.longitude));
            if (getView() != null) {
                showPlaceBottomSheet(pendingNavigationPlace, requireView());
            }
            pendingNavigationPlace = null;
            pendingNavigationQuery = null;
            return;
        }

        if (pendingNavigationQuery != null && !pendingNavigationQuery.trim().isEmpty()) {
            viewModel.searchForPlaces(pendingNavigationQuery.trim());
            pendingNavigationQuery = null;
        }
    }

    private void observeTripOverlayState() {
        viewModel.tripStopOverlay.observe(getViewLifecycleOwner(), stops -> {
            if (stops == null || stops.isEmpty()) {
                clearTripStopOverlayUi();
                return;
            }

            renderTripStopMarkers(stops);
            fitCameraToTripStops(stops);
            showTripStopNavArrows(true, stops.size() > 1);
        });

        viewModel.tripRouteLegs.observe(getViewLifecycleOwner(), legs -> {
            if (legs == null || legs.isEmpty()) {
                setFeatures(TRIP_ROUTE_SOURCE_ID, Collections.emptyList());
                return;
            }
            renderTripRouteLegs(legs);
        });

        viewModel.selectedTripStopIndex.observe(getViewLifecycleOwner(), index -> {
            List<MapViewModel.TripStopOverlay> stops = viewModel.tripStopOverlay.getValue();
            if (stops == null || stops.isEmpty() || index == null || index < 0 || index >= stops.size()) {
                if (stops != null && !stops.isEmpty()) {
                    renderTripStopMarkers(stops);
                }
                if (layoutTripStopDetail != null) {
                    layoutTripStopDetail.setVisibility(View.GONE);
                }
                return;
            }

            MapViewModel.TripStopOverlay stop = stops.get(index);
            renderTripStopMarkers(stops);
            showTripStopDetailCard(stop);
            animateCameraToTripStop(stop);
        });
    }

    @NonNull
    private List<MapViewModel.TripStopOverlay> parseTripStopsJson(@NonNull String json) {
        List<MapViewModel.TripStopOverlay> parsed = new ArrayList<>();
        try {
            JSONArray items = new JSONArray(json);
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) {
                    continue;
                }

                double lat = item.optDouble("lat", Double.NaN);
                double lng = item.optDouble("lng", Double.NaN);
                if (!Double.isFinite(lat) || !Double.isFinite(lng)) {
                    continue;
                }
                if (Double.compare(lat, 0.0d) == 0 && Double.compare(lng, 0.0d) == 0) {
                    continue;
                }

                int order = item.optInt("order", parsed.size() + 1);
                String title = item.optString("title", "");
                String address = item.optString("address", "");
                String note = item.optString("note", "");
                long time = item.optLong("time", 0L);

                parsed.add(new MapViewModel.TripStopOverlay(order, lat, lng, title, address, note, time));
            }
        } catch (JSONException ignored) {
            return Collections.emptyList();
        }

        Collections.sort(parsed, Comparator.comparingInt(stop -> stop.orderIndex));
        List<MapViewModel.TripStopOverlay> normalized = new ArrayList<>();
        for (int i = 0; i < parsed.size(); i++) {
            MapViewModel.TripStopOverlay stop = parsed.get(i);
            normalized.add(new MapViewModel.TripStopOverlay(
                i + 1,
                stop.latitude,
                stop.longitude,
                stop.title,
                stop.address,
                stop.note,
                stop.timeMillis));
        }
        return normalized;
    }

    private void clearTripStopOverlayUi() {
        setFeatures(TRIP_STOP_SOURCE_ID, Collections.emptyList());
        setFeatures(TRIP_ROUTE_SOURCE_ID, Collections.emptyList());
        showTripStopNavArrows(false, false);
        if (layoutTripStopDetail != null) {
            layoutTripStopDetail.setVisibility(View.GONE);
        }
    }

    private void applyTripRouteModeUi(@NonNull View root) {
        if (btnTripRouteBack != null) {
            btnTripRouteBack.setVisibility(isTripRouteModeActive() ? View.VISIBLE : View.GONE);
        }

        if (!isTripRouteModeActive()) {
            return;
        }

        hideHistory.run();

        if (mapSearchView != null) {
            suppressQueryTextChange = true;
            try {
                mapSearchView.setQuery("", false);
                mapSearchView.clearFocus();
            } finally {
                suppressQueryTextChange = false;
            }
        }

        if (searchContainer == null) {
            searchContainer = root.findViewById(R.id.search_container);
        }

        if (searchContainer != null) {
            searchContainer.setVisibility(View.GONE);
        }
        if (mapSearchHistoryView != null) {
            mapSearchHistoryView.setVisibility(View.GONE);
        }
        if (btnMyLocation != null) {
            btnMyLocation.setVisibility(View.GONE);
        }
        if (btnMapCompass != null) {
            btnMapCompass.setVisibility(View.GONE);
        }
        if (downloadCityMapLayout != null) {
            downloadCityMapLayout.setVisibility(View.GONE);
        }
        if (followRouteBar != null) {
            followRouteBar.setVisibility(View.GONE);
        }
        if (bottomSheetBehavior != null) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        }
        if (bottomSheetContainer != null) {
            bottomSheetContainer.setVisibility(View.GONE);
        }
    }

    private void installTripRouteModeBackHandler() {
        if (!isTripRouteModeActive()) {
            return;
        }

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        navigateBackFromTripRouteMode();
                    }
                });
    }

    private boolean isTripRouteModeActive() {
        return tripRouteModeRequested;
    }

    private void navigateBackFromTripRouteMode() {
        View currentView = getView();
        if (currentView == null) {
            return;
        }

        NavController navController = Navigation.findNavController(currentView);
        if (navController.popBackStack()) {
            return;
        }

        if (sourceTripId != null && !sourceTripId.trim().isEmpty()) {
            Uri tripDetailUri = UriUtils.buildUri(UriUtils.PathTo.TRIP_DETAIL).buildUpon()
                    .appendQueryParameter("tripId", sourceTripId)
                    .appendQueryParameter("tripTitle", sourceTripTitle == null ? "" : sourceTripTitle)
                    .build();
            navController.navigate(tripDetailUri);
            return;
        }

        navController.navigateUp();
    }

    private void renderTripStopMarkers(@NonNull List<MapViewModel.TripStopOverlay> stops) {
        if (!isMapStyleReady()) {
            return;
        }

        Style style = mapLibreMap.getStyle();
        if (style == null) {
            return;
        }

        Integer selectedIndexValue = viewModel.selectedTripStopIndex.getValue();
        int selectedIndex = selectedIndexValue != null ? selectedIndexValue : -1;

        List<Feature> features = new ArrayList<>();
        for (int i = 0; i < stops.size(); i++) {
            MapViewModel.TripStopOverlay stop = stops.get(i);
            boolean isSelected = i == selectedIndex;
            String iconId = "trip-stop-icon-" + stop.orderIndex + (isSelected ? "-selected" : "");
            if (style.getImage(iconId) == null) {
                style.addImage(iconId, createNumberedMarkerBitmap(stop.orderIndex, isSelected));
            }

            Feature feature = Feature.fromGeometry(Point.fromLngLat(stop.longitude, stop.latitude));
            feature.addStringProperty(PROP_ICON, iconId);
            feature.addNumberProperty(PROP_ORDER, stop.orderIndex);
            feature.addNumberProperty(PROP_LAT, stop.latitude);
            feature.addNumberProperty(PROP_LNG, stop.longitude);
            feature.addBooleanProperty(PROP_SELECTED, isSelected);
            features.add(feature);
        }

        setFeatures(TRIP_STOP_SOURCE_ID, features);
    }

    private void renderTripRouteLegs(@NonNull List<MapViewModel.TripLegRoute> legs) {
        List<Feature> features = new ArrayList<>();
        for (MapViewModel.TripLegRoute leg : legs) {
            if (leg == null || leg.geometryJson == null || leg.geometryJson.trim().isEmpty()) {
                continue;
            }
            Feature routeFeature = parseRouteFeature(leg.geometryJson);
            if (routeFeature != null) {
                features.add(routeFeature);
            }
        }
        setFeatures(TRIP_ROUTE_SOURCE_ID, features);
    }

    @NonNull
    private Bitmap createNumberedMarkerBitmap(int number, boolean isSelected) {
        Bitmap base = loadMarkerBitmap(R.drawable.ic_marker, "#2ECC71");
        float markerScale = isSelected ? 1.50f : 1.30f;
        int width = Math.max(1, Math.round(base.getWidth() * markerScale));
        int height = Math.max(1, Math.round(base.getHeight() * markerScale));
        Bitmap mutable = Bitmap.createScaledBitmap(base, width, height, true)
                .copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutable);

        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setColor(Color.BLACK);
        strokePaint.setFakeBoldText(true);
        strokePaint.setTextAlign(Paint.Align.CENTER);
        strokePaint.setTextSize(dpToPx(isSelected ? 14f : 12f));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dpToPx(isSelected ? 3f : 2f));

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setFakeBoldText(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(dpToPx(isSelected ? 14f : 12f));

        String text = String.valueOf(number);
        float x = mutable.getWidth() / 2f;
        float y = dpToPx(isSelected ? 22f : 19f);
        canvas.drawText(text, x, y, strokePaint);
        canvas.drawText(text, x, y, textPaint);
        return mutable;
    }

    private void fitCameraToTripStops(@NonNull List<MapViewModel.TripStopOverlay> stops) {
        if (!isMapStyleReady() || stops.isEmpty()) {
            return;
        }

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (MapViewModel.TripStopOverlay stop : stops) {
            builder.include(new LatLng(stop.latitude, stop.longitude));
        }

        try {
            mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 120));
        } catch (Exception ignored) {
            // Ignore bounds errors for nearly-identical points.
        }
    }

    private void showTripStopNavArrows(boolean visible, boolean enabled) {
        if (layoutTripStopNavArrows != null) {
            layoutTripStopNavArrows.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (btnTripStopPrev != null) {
            btnTripStopPrev.setEnabled(enabled);
            btnTripStopPrev.setAlpha(enabled ? 1.0f : 0.45f);
        }
        if (btnTripStopNext != null) {
            btnTripStopNext.setEnabled(enabled);
            btnTripStopNext.setAlpha(enabled ? 1.0f : 0.45f);
        }
    }

    private void showTripStopDetailCard(@NonNull MapViewModel.TripStopOverlay stop) {
        if (layoutTripStopDetail == null
                || tvTripStopOrderBadge == null
                || tvTripStopTitle == null
                || tvTripStopAddress == null
                || tvTripStopNote == null
                || tvTripStopTime == null) {
            return;
        }

        tvTripStopOrderBadge.setText(String.valueOf(stop.orderIndex));
        tvTripStopTitle.setText(stop.title == null || stop.title.trim().isEmpty()
                ? getString(R.string.default_place_name)
                : stop.title);

        boolean hasAddress = stop.address != null && !stop.address.trim().isEmpty();
        tvTripStopAddress.setText(hasAddress ? stop.address : "");
        tvTripStopAddress.setVisibility(hasAddress ? View.VISIBLE : View.GONE);

        boolean hasNote = stop.note != null && !stop.note.trim().isEmpty();
        tvTripStopNote.setText(hasNote ? stop.note : "");
        tvTripStopNote.setVisibility(hasNote ? View.VISIBLE : View.GONE);

        if (stop.timeMillis > 0L) {
            SimpleDateFormat dayFormat = new SimpleDateFormat(
                    getString(R.string.trip_stop_time_day_pattern),
                    Locale.getDefault());
            SimpleDateFormat clockFormat = new SimpleDateFormat(
                    getString(R.string.trip_stop_time_clock_pattern),
                    Locale.getDefault());
            Date date = new Date(stop.timeMillis);
            tvTripStopTime.setText(getString(
                    R.string.trip_stop_time_format,
                    dayFormat.format(date),
                    clockFormat.format(date)));
            tvTripStopTime.setVisibility(View.VISIBLE);
        } else {
            tvTripStopTime.setVisibility(View.GONE);
        }

        layoutTripStopDetail.setVisibility(View.VISIBLE);
    }

    private void animateCameraToTripStop(@NonNull MapViewModel.TripStopOverlay stop) {
        if (!isMapStyleReady()) {
            return;
        }
        CameraPosition current = mapLibreMap.getCameraPosition();
        double zoom = current != null ? current.zoom : 15.0;
        mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                new LatLng(stop.latitude, stop.longitude),
                zoom), 450);
    }

    @Nullable
    private MapViewModel.TripStopOverlay findTripStopAt(@NonNull LatLng point) {
        if (mapLibreMap == null) {
            return null;
        }

        PointF screenPoint = mapLibreMap.getProjection().toScreenLocation(point);
        List<Feature> hits = mapLibreMap.queryRenderedFeatures(screenPoint, TRIP_STOP_LAYER_ID);
        if (hits == null || hits.isEmpty()) {
            return null;
        }

        Feature hit = hits.get(0);
        if (hit == null || !hit.hasProperty(PROP_ORDER)) {
            return null;
        }

        Number orderNumber = hit.getNumberProperty(PROP_ORDER);
        if (orderNumber == null) {
            return null;
        }

        int order = orderNumber.intValue();
        List<MapViewModel.TripStopOverlay> stops = viewModel.tripStopOverlay.getValue();
        if (stops == null || stops.isEmpty()) {
            return null;
        }

        for (MapViewModel.TripStopOverlay stop : stops) {
            if (stop.orderIndex == order) {
                return stop;
            }
        }
        return null;
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

        if (isTripRouteModeActive()) {
            btnMapCompass.setVisibility(View.GONE);
            return;
        }

        double bearing = Math.abs(mapLibreMap.getCameraPosition().bearing);
        boolean show = bearing > 1d && bearing < 359d;
        btnMapCompass.setVisibility(show ? View.VISIBLE : View.GONE);
        updateFloatingControlsPosition();
    }

    private void onMapCameraChanged() {
        updateCompassButtonVisibility();
        refreshUserIndicatorVisibilityForZoom();
    }

    private void refreshUserIndicatorVisibilityForZoom() {
        if (lastKnownUserLocation == null) {
            return;
        }

        boolean shouldShow = shouldShowUserIndicatorForCurrentZoom();
        if (userIndicatorVisibleForZoom != null && userIndicatorVisibleForZoom == shouldShow) {
            return;
        }

        if (shouldShow) {
            renderUserLocationIndicator(lastKnownUserLocation, lastKnownUserBearingDegrees);
            return;
        }

        userIndicatorVisibleForZoom = false;
        setFeatures(USER_ROUTE_SOURCE_ID, Collections.emptyList());
    }

    private boolean shouldShowUserIndicatorForCurrentZoom() {
        if (mapLibreMap == null || mapLibreMap.getCameraPosition() == null) {
            return true;
        }
        return mapLibreMap.getCameraPosition().zoom >= USER_INDICATOR_MIN_VISIBLE_ZOOM;
    }

    private void resetMapBearingNorth() {
        if (!isMapStyleReady()) {
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
        if (!isMapStyleReady()) {
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
        mapSearchView = root.findViewById(R.id.map_search);
        mapSearchHistoryView = root.findViewById(R.id.rv_search_history);
        SearchView searchView = mapSearchView;
        RecyclerView rvHistory = mapSearchHistoryView;

        if (searchView == null || rvHistory == null) {
            hideHistory = () -> {
            };
            return;
        }

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
            suppressQueryTextChange = true;
            try {
                searchView.setQuery(query, false);
            } finally {
                suppressQueryTextChange = false;
            }
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
                if (suppressQueryTextChange) {
                    return true;
                }
                viewModel.searchForPlacesLive(newText);
                if (TextUtils.isEmpty(newText)) {
                    clearSearchResultMarkers();
                }
                return true;
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
            AppSnackbar.show(requireContext(), R.string.download_map_success);
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

        if (isTripRouteModeActive()) {
            downloadCityMapLayout.setVisibility(View.GONE);
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
        if (mapLibreMap == null) {
            return;
        }

        if (isTripRouteModeActive()) {
            updateSearchMarkersSource(Collections.emptyList());
            viewModel.notifySearchDone(0);
            return;
        }

        if (viewModel != null && viewModel.hasActiveRouteSession()) {
            updateSearchMarkersSource(Collections.emptyList());
            viewModel.notifySearchDone(0);
            return;
        }

        if (places == null || places.isEmpty()) {
            updateSearchMarkersSource(Collections.emptyList());
            viewModel.notifySearchDone(0);
            return;
        }

        List<Place> locatedPlaces = new ArrayList<>();
        for (Place place : places) {
            if (place != null && isValidLocation(place.location)) {
                locatedPlaces.add(place);
            }
        }

        if (locatedPlaces.isEmpty()) {
            updateSearchMarkersSource(Collections.emptyList());
            viewModel.notifySearchDone(0);
            return;
        }

        boolean hasUserLocation = lastKnownUserLocation != null
            && isValidLocation(lastKnownUserLocation);
        if (hasUserLocation) {
            sortPlacesByUserProximity(locatedPlaces, lastKnownUserLocation);
        }

        Place topResult = locatedPlaces.get(0);
        Place clusterAnchor = resolvePrimaryClusterAnchor(
                locatedPlaces,
                topResult,
                hasUserLocation ? lastKnownUserLocation : null);
        List<Place> primaryCluster = selectPrimaryCluster(locatedPlaces, clusterAnchor);
        List<Feature> searchFeatures = new ArrayList<>();
        for (Place place : primaryCluster) {
            if (place.location == null) {
                continue;
            }
            LatLng position = new LatLng(place.location.latitude, place.location.longitude);
            searchFeatures.add(createFeatureForPlace(position, place));
        }

        updateSearchMarkersSource(searchFeatures);

        double anchorDistanceFromUserKm = hasUserLocation
            ? distanceKm(lastKnownUserLocation, clusterAnchor.location)
            : Double.MAX_VALUE;
        boolean isLocalSearch = hasUserLocation
            && anchorDistanceFromUserKm <= LOCAL_SEARCH_RADIUS_KM;

        Place signaturePlace = isLocalSearch ? clusterAnchor : topResult;

        String cameraSignature = buildSearchCameraSignature(
            signaturePlace,
            isLocalSearch,
            lastKnownUserLocation);
        if (cameraSignature != null && cameraSignature.equals(lastSearchCameraSignature)) {
            viewModel.notifySearchDone(places.size());
            return;
        }

        if (isLocalSearch) {
            focusCameraForLocalSearch(clusterAnchor, lastKnownUserLocation, primaryCluster);
        } else {
            focusCameraForRemoteSearch(topResult, hasUserLocation);
        }

        lastSearchCameraSignature = cameraSignature;

        viewModel.notifySearchDone(places.size());
    }

    private void sortPlacesByUserProximity(@NonNull List<Place> places,
            @NonNull Location userLocation) {
        Collections.sort(places, (left, right) -> {
            double leftDistance = distanceKm(userLocation,
                    left != null ? left.location : null);
            double rightDistance = distanceKm(userLocation,
                    right != null ? right.location : null);
            int byDistance = Double.compare(leftDistance, rightDistance);
            if (byDistance != 0) {
                return byDistance;
            }

            double leftRating = left != null ? left.rating : 0.0d;
            double rightRating = right != null ? right.rating : 0.0d;
            return Double.compare(rightRating, leftRating);
        });
    }

    @NonNull
    private Place resolvePrimaryClusterAnchor(@NonNull List<Place> locatedPlaces,
            @NonNull Place topResult,
            @Nullable Location userLocation) {
        if (!isValidLocation(userLocation)) {
            return topResult;
        }

        Place nearest = topResult;
        double nearestDistanceKm = distanceKm(userLocation, topResult.location);
        for (Place place : locatedPlaces) {
            if (place.location == null) {
                continue;
            }

            double candidateDistanceKm = distanceKm(userLocation, place.location);
            if (candidateDistanceKm < nearestDistanceKm) {
                nearest = place;
                nearestDistanceKm = candidateDistanceKm;
            }
        }
        return nearest;
    }

    @NonNull
    private List<Place> selectPrimaryCluster(@NonNull List<Place> locatedPlaces,
            @NonNull Place clusterAnchor) {
        if (clusterAnchor.location == null) {
            return locatedPlaces;
        }

        List<Place> cluster = new ArrayList<>();
        for (Place place : locatedPlaces) {
            if (place.location == null) {
                continue;
            }
            if (distanceKm(clusterAnchor.location, place.location)
                    <= PRIMARY_CLUSTER_RADIUS_KM) {
                cluster.add(place);
            }
        }

        if (cluster.isEmpty()) {
            cluster.add(clusterAnchor);
        }
        return cluster;
    }

    private void focusCameraForLocalSearch(@NonNull Place clusterAnchor,
            @Nullable Location userLocation,
            @NonNull List<Place> primaryCluster) {
        if (!isMapStyleReady() || primaryCluster.isEmpty()) {
            return;
        }

        Location anchorLocation = clusterAnchor.location;
        if (!isValidLocation(anchorLocation)) {
            return;
        }

        double userToAnchorKm = distanceKm(userLocation, anchorLocation);
        double targetZoom = userToAnchorKm <= 0.8d ? Math.max(LOCAL_MIN_ZOOM, 15.2d) : LOCAL_MIN_ZOOM;
        LatLng target = new LatLng(anchorLocation.latitude, anchorLocation.longitude);

        mapLibreMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(target, targetZoom),
                520);

        View root = getView();
        if (root != null) {
            root.postDelayed(() -> {
                if (mapLibreMap == null) {
                    return;
                }
                if (!isMapStyleReady()) {
                    return;
                }
                CameraPosition current = mapLibreMap.getCameraPosition();
                if (current != null && current.zoom < LOCAL_MIN_ZOOM) {
                    mapLibreMap.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(current.target,
                                    LOCAL_MIN_ZOOM),
                            250);
                }
            }, 600L);
        }
    }

    private void focusCameraForRemoteSearch(@NonNull Place topResult,
            boolean showHint) {
        if (!isMapStyleReady() || topResult.location == null
                || !isValidLocation(topResult.location)) {
            return;
        }

        LatLng target = new LatLng(topResult.location.latitude,
                topResult.location.longitude);
        mapLibreMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(target, REMOTE_RESULT_ZOOM),
                550);

        if (showHint) {
            showRemoteSearchToast(topResult);
        }
    }

    private void showRemoteSearchToast(@NonNull Place topResult) {
        String area = resolveResultArea(topResult);
        long now = System.currentTimeMillis();
        if (area.equals(lastRemoteToastArea)
                && now - lastRemoteToastAtMs < REMOTE_TOAST_COOLDOWN_MS) {
            return;
        }

        lastRemoteToastArea = area;
        lastRemoteToastAtMs = now;
        AppSnackbar.show(requireContext(), getString(R.string.search_results_in_area, area));
    }

    @NonNull
    private String resolveResultArea(@NonNull Place place) {
        String haystack = ((place.name != null ? place.name : "") + " "
                + (place.address != null ? place.address : ""))
                .toLowerCase(Locale.ROOT);

        String[] patterns = getResources().getStringArray(R.array.search_area_patterns);
        for (String patternDef : patterns) {
            String[] parts = patternDef.split("\\|");
            if (parts.length == 2 && haystack.contains(parts[0])) {
                return parts[1];
            }
        }

        if (place.address != null && !place.address.isBlank()) {
            String[] parts = place.address.split(",");
            String candidate = parts.length > 0
                    ? parts[parts.length - 1].trim()
                    : place.address.trim();
            if (!candidate.isEmpty()) {
                return candidate;
            }
        }

        if (place.name != null && !place.name.isBlank()) {
            return place.name;
        }
        return "selected area";
    }

    private double distanceKm(@Nullable Location from, @Nullable Location to) {
        if (from == null || to == null) {
            return Double.MAX_VALUE;
        }

        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(to.latitude - from.latitude);
        double dLon = Math.toRadians(to.longitude - from.longitude);
        double lat1 = Math.toRadians(from.latitude);
        double lat2 = Math.toRadians(to.latitude);

        double sinHalfLat = Math.sin(dLat / 2.0);
        double sinHalfLon = Math.sin(dLon / 2.0);
        double a = sinHalfLat * sinHalfLat
                + Math.cos(lat1) * Math.cos(lat2) * sinHalfLon * sinHalfLon;
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return earthRadiusKm * c;
    }

    private void clearSearchResultMarkers() {
        updateSearchMarkersSource(new ArrayList<>());
        lastSearchCameraSignature = null;
    }

    private void updateSearchMarkersSource(@NonNull List<Feature> features) {
        if (!isMapStyleReady()) {
            return;
        }

        Style style = mapLibreMap.getStyle();
        if (style == null) {
            return;
        }

        GeoJsonSource source = style.getSourceAs(SEARCH_SOURCE_ID);
        if (source != null) {
            source.setGeoJson(FeatureCollection.fromFeatures(features));
        }
    }

    private void refreshFavoriteMarkers() {
        if (mapLibreMap == null) {
            return;
        }

        if (isTripRouteModeActive()) {
            clearFavoriteMarkers();
            return;
        }

        if (viewModel != null && viewModel.hasActiveRouteSession()) {
            clearFavoriteMarkers();
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
        addSourceIfMissing(style, TRIP_STOP_SOURCE_ID);
        addSourceIfMissing(style, TRIP_ROUTE_SOURCE_ID);

        addMarkerImages(style);

        addRouteLayersIfMissing(style);
        addTripOverlayLayersIfMissing(style);

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

    private void addTripOverlayLayersIfMissing(@NonNull Style style) {
        if (style.getLayer(TRIP_ROUTE_LAYER_ID) == null) {
            LineLayer routeLayer = new LineLayer(TRIP_ROUTE_LAYER_ID, TRIP_ROUTE_SOURCE_ID);
            routeLayer.setProperties(
                    PropertyFactory.lineColor("#2ECC71"),
                    PropertyFactory.lineWidth(5.0f),
                    PropertyFactory.lineOpacity(0.85f),
                    PropertyFactory.lineDasharray(new Float[] { 2f, 1.5f }),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND));
            style.addLayer(routeLayer);
        }

        if (style.getLayer(TRIP_STOP_LAYER_ID) == null) {
            SymbolLayer stopLayer = new SymbolLayer(TRIP_STOP_LAYER_ID, TRIP_STOP_SOURCE_ID);
            stopLayer.setProperties(
                    PropertyFactory.iconImage(Expression.get(PROP_ICON)),
                    PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconSize(1.0f));
            style.addLayer(stopLayer);
        }
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

        int size = dpToPx(44);
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
        if (!isMapStyleReady()) {
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

        syncDestinationMarkerForRoute(effectiveSession);

        String geometryJson = effectiveSession.route != null ? effectiveSession.route.getGeometryJson() : null;
        if (geometryJson == null || geometryJson.trim().isEmpty()) {
            clearRouteFeatures();
            renderUserLocationIndicator(lastKnownUserLocation, lastKnownUserBearingDegrees);
            return;
        }

        if (!TextUtils.equals(renderedRouteGeometryJson, geometryJson)) {
            Feature routeFeature = parseRouteFeature(geometryJson);
            if (routeFeature == null || !(routeFeature.geometry() instanceof LineString)) {
                Timber.tag(TAG).w("Route geometry could not be parsed for drawing. payload=%s",
                    truncateForLog(geometryJson));
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
                float userBearing = Float.isFinite(effectiveSession.lastBearingDegrees)
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

    private void syncDestinationMarkerForRoute(@NonNull RouteSession session) {
        if (!session.hasRoute() || session.destinationPlace == null || session.destinationPlace.location == null) {
            return;
        }
        selectedPlace = session.destinationPlace;
        renderSelectedPlace();
    }

    @NonNull
    private List<Point> resolveRoutePointsForSession(@NonNull RouteSession session) {
        if (!session.hasRoute() || session.route == null) {
            return Collections.emptyList();
        }

        String geometryJson = session.route.getGeometryJson();
        if (geometryJson == null || geometryJson.trim().isEmpty()) {
            return Collections.emptyList();
        }

        if (TextUtils.equals(renderedRouteGeometryJson, geometryJson)
                && renderedRoutePoints.size() >= 2) {
            return renderedRoutePoints;
        }

        Feature routeFeature = parseRouteFeature(geometryJson);
        if (routeFeature == null || !(routeFeature.geometry() instanceof LineString)) {
            return Collections.emptyList();
        }

        List<Point> points = ((LineString) routeFeature.geometry()).coordinates();
        return points.size() >= 2 ? points : Collections.emptyList();
    }

    private void renderUserLocationIndicator(@Nullable Location location, float bearing) {
        if (location == null) {
            userIndicatorVisibleForZoom = false;
            setFeatures(USER_ROUTE_SOURCE_ID, Collections.emptyList());
            return;
        }

        if (!shouldShowUserIndicatorForCurrentZoom()) {
            if (!Boolean.FALSE.equals(userIndicatorVisibleForZoom)) {
                setFeatures(USER_ROUTE_SOURCE_ID, Collections.emptyList());
            }
            userIndicatorVisibleForZoom = false;
            return;
        }

        userIndicatorVisibleForZoom = true;
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

        RouteSession current = viewModel.getCurrentRouteSession();
        if (!current.hasRoute()) {
            return;
        }

        clearMapArtifactsForActiveRoute();

        viewModel.startFollowingRoute();
        ensureFollowLocationUpdates();
    }

    private void clearMapArtifactsForActiveRoute() {
        clearFavoriteMarkers();
        clearSearchResultMarkers();

        if (mapSearchView != null) {
            suppressQueryTextChange = true;
            try {
                mapSearchView.setQuery("", false);
                mapSearchView.clearFocus();
            } finally {
                suppressQueryTextChange = false;
            }
        }

        hideHistory.run();
        viewModel.searchForPlacesLive("");
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
        if (isTripRouteModeActive()) {
            return;
        }

        View root = getView();
        if (root == null) {
            return;
        }

        if (floatingControlsUpdateScheduled) {
            return;
        }

        ImageButton btnMyLocation = root.findViewById(R.id.btn_my_location);
        if (btnMyLocation == null) {
            return;
        }

        floatingControlsUpdateScheduled = true;

        root.post(() -> {
            floatingControlsUpdateScheduled = false;

            View currentRoot = getView();
            if (currentRoot == null) {
                return;
            }

            ImageButton currentMyLocation = currentRoot.findViewById(R.id.btn_my_location);
            if (currentMyLocation == null) {
                return;
            }

            int rootHeight = currentRoot.getHeight();
            if (rootHeight <= 0) {
                return;
            }

            int myLocationDefaultY = rootHeight - dpToPx(100) - currentMyLocation.getHeight();
            int anchorTop = rootHeight;
            if (bottomSheetBehavior != null && bottomSheetBehavior.getState() != BottomSheetBehavior.STATE_HIDDEN
                    && bottomSheetContainer != null) {
                anchorTop = Math.min(anchorTop, bottomSheetContainer.getTop());
            }

            int targetMyLocationY = anchorTop < rootHeight
                    ? anchorTop - currentMyLocation.getHeight() - dpToPx(16)
                    : myLocationDefaultY;
            float nextMyLocationY = Math.min(myLocationDefaultY, targetMyLocationY);
            if (Math.abs(currentMyLocation.getY() - nextMyLocationY) > 0.5f) {
                currentMyLocation.setY(nextMyLocationY);
            }

            if (btnMapCompass != null) {
                int compassDefaultY = myLocationDefaultY - dpToPx(60);
                int targetCompassY = (int) currentMyLocation.getY() - btnMapCompass.getHeight() - dpToPx(12);
                float nextCompassY = Math.min(compassDefaultY, targetCompassY);
                if (Math.abs(btnMapCompass.getY() - nextCompassY) > 0.5f) {
                    btnMapCompass.setY(nextCompassY);
                }
            }
        });
    }

    private void ensureFollowLocationUpdates() {
        if (followLocationUpdatesActive) {
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
            syncSearchUserLocation(lastKnownUserLocation);
            float seededBearing = resolveLiveHeadingOr(
                    lastKnown.hasBearing() ? lastKnown.getBearing() : lastKnownUserBearingDegrees);
            lastKnownUserBearingDegrees = seededBearing;
            renderUserLocationIndicator(lastKnownUserLocation, seededBearing);
            if (viewModel != null) {
                viewModel.updateFollowingLocation(lastKnownUserLocation, seededBearing);
            }
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
        if (!isMapStyleReady() || remainingPoints.isEmpty()) {
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

    private boolean isValidLocation(@Nullable Location location) {
        if (location == null) {
            return false;
        }

        double lat = location.latitude;
        double lng = location.longitude;
        return Double.isFinite(lat)
                && Double.isFinite(lng)
                && Double.compare(lat, 0.0d) != 0
                && Double.compare(lng, 0.0d) != 0
                && lat >= SEARCH_MIN_LAT
                && lat <= SEARCH_MAX_LAT
                && lng >= SEARCH_MIN_LNG
                && lng <= SEARCH_MAX_LNG;
    }

    private boolean isMapStyleReady() {
        return mapLibreMap != null && mapLibreMap.getStyle() != null;
    }

    @Nullable
    private String buildSearchCameraSignature(@NonNull Place topResult,
            boolean isLocalSearch,
            @Nullable Location userLocation) {
        if (topResult.location == null) {
            return null;
        }

        long latBucket = Math.round(topResult.location.latitude * 100000d);
        long lngBucket = Math.round(topResult.location.longitude * 100000d);
        StringBuilder signature = new StringBuilder((isLocalSearch ? "local" : "remote")
                + "|" + latBucket + "|" + lngBucket);
        if (isLocalSearch) {
            String userBucket = "none";
            if (isValidLocation(userLocation)) {
                long userLatBucket = Math.round(userLocation.latitude * 10000d);
                long userLngBucket = Math.round(userLocation.longitude * 10000d);
                userBucket = userLatBucket + "|" + userLngBucket;
            }
            signature.append("|u|").append(userBucket);
        }
        return signature.toString();
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
        double latitude = position.getLatitude();
        double longitude = position.getLongitude();
        Feature feature = Feature.fromGeometry(Point.fromLngLat(
                longitude,
                latitude));
        feature.addStringProperty(PROP_PLACE_ID,
                !TextUtils.isEmpty(place.id)
                        ? place.id
                        : buildStablePlaceId(latitude, longitude));
        feature.addStringProperty(PROP_NAME,
                place.name != null ? place.name : "Selected Location");
        feature.addStringProperty(PROP_ADDRESS,
            normalizeDisplayAddress(place.name, place.address));
        feature.addNumberProperty(PROP_RATING, place.rating);
        feature.addNumberProperty(PROP_LAT, latitude);
        feature.addNumberProperty(PROP_LNG, longitude);
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

        String name = feature.hasProperty(PROP_NAME)
                ? feature.getStringProperty(PROP_NAME)
                : "Selected Location";
        String address = feature.hasProperty(PROP_ADDRESS)
                ? feature.getStringProperty(PROP_ADDRESS)
                : "Address unavailable";
        String id = feature.hasProperty(PROP_PLACE_ID)
                ? feature.getStringProperty(PROP_PLACE_ID)
                : buildStablePlaceId(lat.doubleValue(), lng.doubleValue());
        Number ratingNumber = feature.hasProperty(PROP_RATING)
                ? feature.getNumberProperty(PROP_RATING)
                : 0.0;
        double rating = ratingNumber != null ? ratingNumber.doubleValue() : 0.0;

        return new Place(
                id,
                name,
            normalizeDisplayAddress(name, address),
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
                        buildStablePlaceId(latLng.getLatitude(), latLng.getLongitude()),
                        finalName,
                    normalizeDisplayAddress(finalName,
                        addressText != null ? addressText : quickPlace.address),
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
                Timber.tag(TAG).e(e, "Geocoding failed");
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
                buildStablePlaceId(latLng.getLatitude(), latLng.getLongitude()),
                resolvedName,
                resolvedAddress,
                0.0,
                new Location(latLng.getLatitude(), latLng.getLongitude()));
    }

    @NonNull
    private String buildStablePlaceId(double latitude, double longitude) {
        String seed = String.format(
                Locale.US,
                "map:%1$.6f:%2$.6f",
                latitude,
                longitude);
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
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
        TextView tvRatingValue = root.findViewById(R.id.tv_rating_value);
        RatingBar rbPlaceRating = root.findViewById(R.id.rb_place_rating);
        TextView tvRatingCount = root.findViewById(R.id.tv_rating_count);
        ImageButton btnAddFavorite = root.findViewById(R.id.btn_add_favorite);
        MaterialButton btnSharePlace = root.findViewById(R.id.btn_share_place);
        MaterialButton btnRoutePlace = root.findViewById(R.id.btn_navigate_place);

        tvName.setText(place.name);
        tvAddress.setText(normalizeDisplayAddress(place.name, place.address));

        if (place.rating > 0) {
            tvRatingValue.setText(String.format(Locale.getDefault(), "%.1f", place.rating));
            rbPlaceRating.setRating((float) place.rating);
        } else {
            tvRatingValue.setText("0");
            rbPlaceRating.setRating(0);
        }
        tvRatingCount.setText("0 reviews"); // Update this when data is fetched

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
                viewModel.addToFavorites(place, new MapViewModel.AddFavoriteCallback() {
                    @Override
                    public void onSuccess() {
                        locationHandler.post(() -> {
                            if (!isAdded()) {
                                return;
                            }
                            viewModel.setStatusText(place.name + " added to Favorites!");
                            updateFavoriteButtonState(btnAddFavorite, true);
                        });
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        locationHandler.post(() -> {
                            if (!isAdded()) {
                                return;
                            }
                            AppSnackbar.show(requireContext(), message);
                        });
                    }
                });
            }
        });

        btnRoutePlace.setOnClickListener(v -> routeToPlace(place));
        btnSharePlace.setOnClickListener(v -> showShareToGroupDialog(place));

        setupReviewsRecyclerView(root);
        setupChipFilters(root);

        allReviews.clear();
        if (reviewAdapter != null) {
            reviewAdapter.showAllReviews(allReviews);
        }
        if (rvReviews != null) {
            rvReviews.setVisibility(View.INVISIBLE);
        }
        if (shimmerReviews != null) {
            shimmerReviews.setVisibility(View.VISIBLE);
            shimmerReviews.startShimmer();
        }
        if (!TextUtils.isEmpty(place.placeSource)) {
            viewModel.loadReviews(place);
        } else if (shimmerReviews != null) {
            shimmerReviews.stopShimmer();
            shimmerReviews.setVisibility(View.GONE);
        }

        View layoutContainer = root.findViewById(R.id.layout_container);
        layoutContainer.post(() -> {
            int dynamicPeekHeight = layoutContainer.getHeight() / 2;
            bottomSheetBehavior.setPeekHeight(dynamicPeekHeight);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        });
    }

    @NonNull
    private String normalizeDisplayAddress(@Nullable String placeName,
            @Nullable String rawAddress) {
        if (rawAddress == null || rawAddress.trim().isEmpty()) {
            return "Address unavailable";
        }

        String normalized = rawAddress.trim();
        if (placeName == null || placeName.trim().isEmpty()) {
            return normalized;
        }

        String name = placeName.trim();
        if (normalized.equalsIgnoreCase(name)) {
            return "Address unavailable";
        }

        if (normalized.regionMatches(true, 0, name, 0, name.length())) {
            String suffix = normalized.substring(name.length()).trim();
            while (!suffix.isEmpty()) {
                char first = suffix.charAt(0);
                if (first == ',' || first == '-' || first == ':' || first == ' ') {
                    suffix = suffix.substring(1).trim();
                    continue;
                }
                break;
            }
            if (!suffix.isEmpty()) {
                normalized = suffix;
            }
        }

        int commaIndex = normalized.indexOf(',');
        if (commaIndex > 0) {
            String firstSegment = normalized.substring(0, commaIndex).trim();
            if (firstSegment.equalsIgnoreCase(name)) {
                String tail = normalized.substring(commaIndex + 1).trim();
                if (!tail.isEmpty()) {
                    normalized = tail;
                }
            }
        }

        return normalized.isEmpty() ? "Address unavailable" : normalized;
    }

    private void setupReviewsRecyclerView(View root) {
        rvReviews = root.findViewById(R.id.rv_reviews);
        shimmerReviews = root.findViewById(R.id.shimmer_reviews);

        if (rvReviews == null) {
            return;
        }

        int itemWidth = calculateReviewCardWidth();
        reviewAdapter.setItemWidthPx(itemWidth);

        rvReviews.setAdapter(reviewAdapter);

        LinearLayoutManager layoutManager = new LinearLayoutManager(
                requireContext(),
                LinearLayoutManager.HORIZONTAL,
                false);
        rvReviews.setLayoutManager(layoutManager);

        if (snapHelper == null) {
            snapHelper = new androidx.recyclerview.widget.LinearSnapHelper();
            snapHelper.attachToRecyclerView(rvReviews);
        }

        if (!rvReviewsTouchListenerRegistered) {
            rvReviews.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
                @Override
                public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull android.view.MotionEvent e) {
                    int action = e.getActionMasked();
                    if (action == android.view.MotionEvent.ACTION_DOWN) {
                        rv.getParent().requestDisallowInterceptTouchEvent(true);
                    } else if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) {
                        rv.getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    return false;
                }

                @Override
                public void onTouchEvent(@NonNull RecyclerView rv, @NonNull android.view.MotionEvent e) {}

                @Override
                public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
            });
            rvReviewsTouchListenerRegistered = true;
        }
    }

    private int calculateReviewCardWidth() {
        float density = requireActivity().getResources().getDisplayMetrics().density;
        int screenWidth = requireActivity().getResources().getDisplayMetrics().widthPixels;
        int horizontalPaddingPx = (int) (16f * density * 2f);
        int carouselGapPx = (int) (12f * density);
        int minWidthPx = (int) (220f * density);
        int maxWidthPx = (int) (320f * density);
        int targetWidthPx = (screenWidth - horizontalPaddingPx - carouselGapPx) / 2;
        return Math.max(minWidthPx, Math.min(maxWidthPx, targetWidthPx));
    }

    private void setupChipFilters(View root) {
        chipGroupFilters = root.findViewById(R.id.chip_group_filters);
        if (chipGroupFilters == null) {
            return;
        }

        chipGroupFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                reviewAdapter.showAllReviews(allReviews);
            } else {
                int selectedChipId = checkedIds.get(0);
                int starRating = 0;

                if (selectedChipId == R.id.chip_filter_5) {
                    starRating = 5;
                } else if (selectedChipId == R.id.chip_filter_4) {
                    starRating = 4;
                } else if (selectedChipId == R.id.chip_filter_3) {
                    starRating = 3;
                } else if (selectedChipId == R.id.chip_filter_2) {
                    starRating = 2;
                } else if (selectedChipId == R.id.chip_filter_1) {
                    starRating = 1;
                }

                reviewAdapter.filterByStarRating(allReviews, starRating);
            }
        });
    }

    private void updateReviewList(java.util.List<com.bif.app.domain.model.Review> reviews, com.bif.app.domain.model.Review myReview) {
        allReviews.clear();
        java.util.List<com.bif.app.domain.model.Review> combinedReviews = new java.util.ArrayList<>();

        if (myReview != null) {
            allReviews.add(new ReviewItem(ReviewItem.VIEW_TYPE_MINE, myReview, true));
            combinedReviews.add(myReview);
        } else {
            allReviews.add(new ReviewItem());
        }

        if (reviews != null) {
            for (com.bif.app.domain.model.Review review : reviews) {
                if (myReview != null && java.util.Objects.equals(review.userId, myReview.userId)) {
                    continue;
                }
                allReviews.add(new ReviewItem(ReviewItem.VIEW_TYPE_OTHERS, review, false));
                if (review != null) {
                    combinedReviews.add(review);
                }
            }
        }

        int count = combinedReviews.size();
        double totalStars = 0;

        for (com.bif.app.domain.model.Review review : combinedReviews) {
            if (review != null) {
                totalStars += review.stars;
            }
        }

        double averageRating = count > 0 ? totalStars / count : 0.0;

        TextView tvRatingCount = placeDetailSheet.findViewById(R.id.tv_rating_count);
        if (tvRatingCount != null) {
            tvRatingCount.setText(String.format(Locale.getDefault(), "%d reviews", count));
        }

        TextView tvRatingValue = placeDetailSheet.findViewById(R.id.tv_rating_value);
        RatingBar rbPlaceRating = placeDetailSheet.findViewById(R.id.rb_place_rating);
        if (tvRatingValue != null && rbPlaceRating != null) {
            if (averageRating > 0) {
                tvRatingValue.setText(String.format(Locale.getDefault(), "%.1f", averageRating));
                rbPlaceRating.setRating((float) averageRating);
            } else {
                tvRatingValue.setText("0");
                rbPlaceRating.setRating(0);
            }
        }

        if (shimmerReviews != null) {
            shimmerReviews.stopShimmer();
            shimmerReviews.setVisibility(View.GONE);
        }
        if (rvReviews != null) {
            rvReviews.setVisibility(View.VISIBLE);
        }

        int activeStarFilter = getActiveStarFilter();

        if (reviewAdapter != null) {
            if (activeStarFilter == 0) {
                reviewAdapter.showAllReviews(allReviews);
            } else {
                reviewAdapter.filterByStarRating(allReviews, activeStarFilter);
            }
        }
    }

    private int getActiveStarFilter() {
        int activeStarFilter = 0;
        if (chipGroupFilters != null) {
            List<Integer> checkedIds = chipGroupFilters.getCheckedChipIds();
            if (!checkedIds.isEmpty()) {
                int selectedId = checkedIds.get(0);
                if (selectedId == R.id.chip_filter_5) activeStarFilter = 5;
                else if (selectedId == R.id.chip_filter_4) activeStarFilter = 4;
                else if (selectedId == R.id.chip_filter_3) activeStarFilter = 3;
                else if (selectedId == R.id.chip_filter_2) activeStarFilter = 2;
                else if (selectedId == R.id.chip_filter_1) activeStarFilter = 1;
            }
        }
        return activeStarFilter;
    }

    private void showAddReviewDialog(@Nullable com.bif.app.domain.model.Review existingReview) {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_review, null);
        RatingBar ratingBar = view.findViewById(R.id.feedback_rating_bar);
        com.google.android.material.textfield.TextInputEditText etComment = view.findViewById(R.id.feedback_input);

        if (existingReview != null) {
            ratingBar.setRating(existingReview.stars);
            if (existingReview.comment != null) {
                etComment.setText(existingReview.comment);
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(existingReview == null
                        ? R.string.add_review_title
                        : R.string.edit_review_title)
                .setView(view)
                .setPositiveButton(R.string.submit_review, (dialog, which) -> {
                    int stars = (int) ratingBar.getRating();
                    if (stars > 0) {
                        String comment = etComment.getText() != null ? etComment.getText().toString().trim() : "";
                        if (existingReview != null) {
                            viewModel.updateReview(existingReview, stars, comment);
                        } else {
                            viewModel.submitReview(stars, comment);
                        }
                    } else {
                        AppSnackbar.show(getContext(), R.string.provide_rating_error);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private Favorite findFavoriteForPlace(Place place) {
        if (place == null) {
            return null;
        }

        String placeId = place != null && place.id != null ? place.id.trim() : "";
        for (Favorite favorite : currentFavorites) {
            String favoritePlaceId = favorite != null && favorite.placeId != null
                    ? favorite.placeId.trim()
                    : "";
            if (!TextUtils.isEmpty(placeId) && placeId.equals(favoritePlaceId)) {
                Timber.d("Matched favorite by placeId: placeId=%s favoriteId=%s",
                        placeId,
                        favorite.id);
                return favorite;
            }

            if (place.address != null && !place.address.isEmpty()
                    && place.address.equals(favorite.address)) {
                Timber.d("Matched favorite by address fallback: placeId=%s favoriteId=%s",
                        placeId,
                        favorite.id);
                return favorite;
            }

            if (place.location != null) {
                double latDelta = favorite.latitude - place.location.latitude;
                double lngDelta = favorite.longitude - place.location.longitude;
                if (Math.sqrt(latDelta * latDelta + lngDelta * lngDelta) < 0.0001) {
                    Timber.d("Matched favorite by coordinate fallback: placeId=%s favoriteId=%s",
                            placeId,
                            favorite.id);
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
        syncSearchUserLocation(lastKnownUserLocation);
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
        syncSearchUserLocation(lastKnownUserLocation);
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
        syncSearchUserLocation(lastKnownUserLocation);
        lastKnownUserBearingDegrees = location.hasBearing() ? location.getBearing() : lastKnownUserBearingDegrees;
        renderUserLocationIndicator(lastKnownUserLocation, lastKnownUserBearingDegrees);

        RouteSession session = viewModel != null ? viewModel.getCurrentRouteSession() : RouteSession.idle();
        if (session.hasRoute()) {
            List<Point> routePoints = resolveRoutePointsForSession(session);
            if (routePoints.size() >= 2) {
                RouteGeometryUtils.RouteProgress progress = RouteGeometryUtils.computeRouteProgress(
                        routePoints,
                        lastKnownUserLocation);
                List<Point> remainingPoints = progress.remainingPoints.size() >= 2
                        ? progress.remainingPoints
                        : routePoints;
                fitCameraToUserAndRemainingRoute(lastKnownUserLocation, remainingPoints);
                return;
            }
        }

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

    private void syncSearchUserLocation(@Nullable Location location) {
        if (viewModel != null) {
            viewModel.updateSearchUserLocation(location);
        }
    }

    private void ensureHeadingSensorUpdates() {
        if (headingUpdatesActive || getContext() == null) {
            return;
        }

        if (sensorManager == null) {
            sensorManager = requireContext().getSystemService(SensorManager.class);
        }
        if (sensorManager == null) {
            return;
        }

        if (headingSensor == null) {
            headingSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
        if (headingSensor == null) {
            return;
        }

        headingUpdatesActive = sensorManager.registerListener(
                headingSensorListener,
                headingSensor,
                SensorManager.SENSOR_DELAY_GAME,
                SensorManager.SENSOR_DELAY_UI);
    }

    private void stopHeadingSensorUpdates() {
        if (!headingUpdatesActive || sensorManager == null) {
            return;
        }
        sensorManager.unregisterListener(headingSensorListener);
        headingUpdatesActive = false;
    }

    private float resolveLiveHeadingOr(float fallback) {
        return Float.isFinite(deviceHeadingDegrees) ? deviceHeadingDegrees : normalizeHeading(fallback);
    }

    private boolean shouldSkipHeadingUpdate(float nextHeading) {
        float normalized = normalizeHeading(nextHeading);
        long now = System.currentTimeMillis();
        if (!Float.isFinite(deviceHeadingDegrees)) {
            return false;
        }

        float delta = Math.abs(normalized - deviceHeadingDegrees);
        if (delta > 180f) {
            delta = 360f - delta;
        }

        return delta < HEADING_MIN_DELTA_DEGREES
                && now - lastHeadingUpdateAtMs < HEADING_MIN_INTERVAL_MS;
    }

    private float computeHeadingDegrees(@NonNull float[] rotationVector) {
        SensorManager.getRotationMatrixFromVector(headingRotationMatrix, rotationVector);

        int xAxis = SensorManager.AXIS_X;
        int yAxis = SensorManager.AXIS_Y;
        switch (resolveDisplayRotation()) {
            case Surface.ROTATION_90:
                xAxis = SensorManager.AXIS_Y;
                yAxis = SensorManager.AXIS_MINUS_X;
                break;
            case Surface.ROTATION_180:
                xAxis = SensorManager.AXIS_MINUS_X;
                yAxis = SensorManager.AXIS_MINUS_Y;
                break;
            case Surface.ROTATION_270:
                xAxis = SensorManager.AXIS_MINUS_Y;
                yAxis = SensorManager.AXIS_X;
                break;
            case Surface.ROTATION_0:
            default:
                break;
        }

        SensorManager.remapCoordinateSystem(
                headingRotationMatrix,
                xAxis,
                yAxis,
                headingRemappedMatrix);
        SensorManager.getOrientation(headingRemappedMatrix, headingOrientation);

        return normalizeHeading((float) Math.toDegrees(headingOrientation[0]));
    }

    private int resolveDisplayRotation() {
        if (getActivity() == null || getActivity().getWindowManager() == null
                || getActivity().getWindowManager().getDefaultDisplay() == null) {
            return Surface.ROTATION_0;
        }
        return getActivity().getWindowManager().getDefaultDisplay().getRotation();
    }

    private float normalizeHeading(float headingDegrees) {
        float normalized = headingDegrees % 360f;
        if (normalized < 0f) {
            normalized += 360f;
        }
        return normalized;
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
        ensureHeadingSensorUpdates();
        ensureFollowLocationUpdates();
    }

    @Override
    public void onPause() {
        stopHeadingSensorUpdates();
        stopFollowLocationUpdates();
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
        if (viewModel != null) {
            viewModel.clearTripOverlay();
        }
        stopSingleLocationUpdates();
        stopHeadingSensorUpdates();
        floatingControlsUpdateScheduled = false;
        userIndicatorVisibleForZoom = null;
        styleLoadRequested = false;
        emulatorRenderModeOptimized = false;
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
        mapLibreMap = null;
        clearFavoriteMarkers();
        clearSearchResultMarkers();
        clearSelectedMarker();
        clearRouteFeatures();
        stopFollowLocationUpdates();
        if (reviewAdapter != null) {
            reviewAdapter.clear();
        }
        if (rvReviews != null) {
            rvReviews.setAdapter(null);
            rvReviews.setOnFlingListener(null);
        }
        rvReviewsTouchListenerRegistered = false;
        if (snapHelper != null) {
            snapHelper.attachToRecyclerView(null);
            snapHelper = null;
        }
        if (shimmerReviews != null) {
            shimmerReviews.stopShimmer();
        }
        if (chipGroupFilters != null) {
            chipGroupFilters.clearCheck();
            chipGroupFilters.setOnCheckedStateChangeListener(null);
        }
        if (allReviews != null) {
            allReviews.clear();
        }
        rvReviews = null;
        shimmerReviews = null;
        chipGroupFilters = null;
        reviewAdapter = null;
        suppressQueryTextChange = false;
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
        progressSearchPlaces = null;
        mapSearchView = null;
        mapSearchHistoryView = null;
        pendingTripStopsJson = null;
        tripRouteModeRequested = false;
        sourceTripId = null;
        sourceTripTitle = null;
        searchContainer = null;
        btnMyLocation = null;
        btnTripRouteBack = null;
        layoutTripStopDetail = null;
        tvTripStopOrderBadge = null;
        tvTripStopTitle = null;
        tvTripStopAddress = null;
        tvTripStopNote = null;
        tvTripStopTime = null;
        btnTripStopClose = null;
        btnTripStopPrev = null;
        btnTripStopNext = null;
        layoutTripStopNavArrows = null;
        super.onDestroyView();
    }
}

