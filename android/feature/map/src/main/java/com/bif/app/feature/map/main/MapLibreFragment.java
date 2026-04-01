package com.bif.app.feature.map.main;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bif.app.core.utils.UriUtils;
import com.bif.app.feature.map.BuildConfig;
import com.bif.app.feature.map.R;
import com.bif.app.domain.model.Favorite;
import com.bif.app.domain.model.Group;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.MapState;
import com.bif.app.domain.model.Place;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.json.JSONException;
import org.json.JSONObject;
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
import org.maplibre.android.maps.UiSettings;
import org.maplibre.android.style.layers.Property;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.Point;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

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
    private static final String MARKER_ICON_FAVORITE_ID = "marker-icon-favorite";
    private static final String MARKER_ICON_SEARCH_ID = "marker-icon-search";
    private static final String MARKER_ICON_SELECTED_ID = "marker-icon-selected";
    private static final String PROP_PLACE_ID = "placeId";
    private static final String PROP_NAME = "name";
    private static final String PROP_ADDRESS = "address";
    private static final String PROP_RATING = "rating";
    private static final String PROP_LAT = "lat";
    private static final String PROP_LNG = "lng";

    private MapView mapView;
    private MapLibreMap mapLibreMap;
    private Place selectedPlace;
    private BottomSheetBehavior<View> bottomSheetBehavior;
    private MapViewModel viewModel;
    private List<Favorite> currentFavorites = new ArrayList<>();
    private Runnable hideHistory = () -> {
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

        View bottomSheet = root.findViewById(R.id.place_detail_sheet);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

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
        viewModel = new ViewModelProvider(this).get(MapViewModel.class);

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

        setupSearchUi(view);

        ImageButton btnMyLocation = view.findViewById(R.id.btn_my_location);
        if (btnMyLocation != null) {
            btnMyLocation.setOnClickListener(v -> {
                if (bottomSheetBehavior.getState() != BottomSheetBehavior.STATE_HIDDEN) {
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
            MapStyleUtils.applyPaletteForCurrentMode(requireContext(), style);
            ensurePlaceLayers(style);
            configureCompassAboveMyLocation();

            CameraPosition camera = new CameraPosition.Builder()
                    .target(new LatLng(10.7769, 106.7009))
                    .zoom(12.0)
                    .build();
            mapLibreMap.setCameraPosition(camera);

            mapLibreMap.addOnMapClickListener(point -> {
                hideHistory.run();
                Place tappedPlace = findRenderedPlaceAt(point);
                if (tappedPlace != null) {
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
        });

    }

    private void configureCompassAboveMyLocation() {
        if (mapLibreMap == null || getView() == null) {
            return;
        }

        View root = getView();
        ImageButton btnMyLocation = root.findViewById(R.id.btn_my_location);
        if (btnMyLocation == null) {
            applyCompassMargins(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(120));
            return;
        }

        btnMyLocation.post(() -> {
            if (mapLibreMap == null || getView() == null) {
                return;
            }

            int right = dpToPx(16);
            int myLocationBottom = getView().getHeight() - btnMyLocation.getTop();
            int bottom = myLocationBottom + btnMyLocation.getHeight() + dpToPx(10);
            int topFallback = Math.max(dpToPx(16),
                    getView().getHeight() - bottom - dpToPx(40));

            applyCompassMargins(dpToPx(16), topFallback, right, bottom);
        });
    }

    private void applyCompassMargins(int left, int top, int right, int bottom) {
        if (mapLibreMap == null) {
            return;
        }

        UiSettings uiSettings = mapLibreMap.getUiSettings();
        uiSettings.setCompassEnabled(true);

        // Prefer bottom-end placement if the SDK exposes compass gravity.
        try {
            Method setCompassGravity = uiSettings.getClass()
                    .getMethod("setCompassGravity", int.class);
            setCompassGravity.invoke(uiSettings, Gravity.BOTTOM | Gravity.END);
        } catch (Exception ignored) {
            // Older SDKs may not expose compass gravity; margins still apply.
        }

        uiSettings.setCompassMargins(left, top, right, bottom);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * requireContext().getResources()
                .getDisplayMetrics().density);
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

        addMarkerImages(style);

        addSymbolLayerIfMissing(style, FAVORITE_LAYER_ID, FAVORITE_SOURCE_ID,
                MARKER_ICON_FAVORITE_ID, 0.92f);
        addSymbolLayerIfMissing(style, SEARCH_LAYER_ID, SEARCH_SOURCE_ID,
                MARKER_ICON_SEARCH_ID, 1.0f);
        addSymbolLayerIfMissing(style, SELECTED_LAYER_ID, SELECTED_SOURCE_ID,
                MARKER_ICON_SELECTED_ID, 1.08f);
    }

    private void addSourceIfMissing(@NonNull Style style, @NonNull String sourceId) {
        if (style.getSource(sourceId) == null) {
            style.addSource(new GeoJsonSource(sourceId,
                    FeatureCollection.fromFeatures(Collections.emptyList())));
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

    private void addMarkerImages(@NonNull Style style) {
        style.addImage(MARKER_ICON_FAVORITE_ID,
                loadMarkerBitmap(R.drawable.ic_marker, "#F1C40F"));
        style.addImage(MARKER_ICON_SEARCH_ID,
                loadMarkerBitmap(R.drawable.ic_marker, "#F39C12"));
        style.addImage(MARKER_ICON_SELECTED_ID,
                loadMarkerBitmap(R.drawable.ic_marker, "#E74C3C"));
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
        for (String key : new String[] { "name", "name_en", "name:en", "class" }) {
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
                    finalName = "Selected Location";
                }

                Place clickedPlace = new Place(
                        UUID.randomUUID().toString(),
                        finalName,
                        addressText != null ? addressText : "Unknown Address",
                        0.0,
                        new Location(latLng.getLatitude(), latLng.getLongitude()));

                requireActivity().runOnUiThread(() -> {
                    if (mapLibreMap == null) {
                        return;
                    }

                    selectedPlace = clickedPlace;
                    renderSelectedPlace();

                    animateCameraToSelection(latLng);
                    showPlaceBottomSheet(clickedPlace, requireView());
                });
            } catch (Exception e) {
                Log.e(TAG, "Geocoding failed", e);
                requireActivity().runOnUiThread(() -> {
                    Place fallbackPlace = new Place(
                            UUID.randomUUID().toString(),
                            !TextUtils.isEmpty(preferredName)
                                    ? preferredName
                                    : "Selected Location",
                            "Address unavailable",
                            0.0,
                            new Location(latLng.getLatitude(),
                                    latLng.getLongitude()));

                    if (mapLibreMap == null) {
                        return;
                    }
                    selectedPlace = fallbackPlace;
                    renderSelectedPlace();
                    animateCameraToSelection(latLng);
                    showPlaceBottomSheet(fallbackPlace, requireView());
                });
            }
        }).start();
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
        viewModel.cacheViewedPlace(place);

        TextView tvName = root.findViewById(R.id.tv_place_name);
        TextView tvAddress = root.findViewById(R.id.tv_place_address);
        TextView tvRating = root.findViewById(R.id.tv_place_rating);
        ImageButton btnAddFavorite = root.findViewById(R.id.btn_add_favorite);
        com.google.android.material.button.MaterialButton btnSharePlace = root.findViewById(R.id.btn_share_place);
        com.google.android.material.button.MaterialButton btnNavigatePlace = root.findViewById(R.id.btn_navigate_place);

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

        android.location.Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (location == null) {
            location = locationManager.getLastKnownLocation(
                    LocationManager.NETWORK_PROVIDER);
        }

        if (location != null) {
            mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(location.getLatitude(), location.getLongitude()),
                    15.0));
        } else {
            viewModel.setStatusText("Waiting for GPS signal...");
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
        if (mapView != null) {
            mapView.removeOnDidFailLoadingMapListener(
                    onDidFailLoadingMapListener);
            mapView.onDestroy();
            mapView = null;
        }
        clearFavoriteMarkers();
        clearSearchResultMarkers();
        clearSelectedMarker();
        super.onDestroyView();
    }
}

