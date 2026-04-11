package com.bif.app.feature.social;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import com.bif.app.core.utils.AppSnackbar;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bif.app.domain.model.Place;
import com.bif.app.domain.model.TripPlan;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;

import org.maplibre.android.MapLibre;
import org.maplibre.android.WellKnownTileServer;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.Property;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.Point;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class AddTripStopFragment extends Fragment {

    private static final String DEFAULT_STYLE_URL = "https://demotiles.maplibre.org/style.json";
    private static final String SEARCH_SOURCE_ID = "trip-stop-search-source";
    private static final String SEARCH_LAYER_ID = "trip-stop-search-layer";
    private static final String SELECTED_SOURCE_ID = "trip-stop-selected-source";
    private static final String SELECTED_LAYER_ID = "trip-stop-selected-layer";
    private static final String MARKER_ICON_SEARCH_ID = "trip-stop-marker-search";
    private static final String MARKER_ICON_SELECTED_ID = "trip-stop-marker-selected";
    private static final String PROP_PLACE_ID = "placeId";
    private static final String PROP_RESULT_INDEX = "resultIndex";
    private static final String PROP_LATITUDE = "latitude";
    private static final String PROP_LONGITUDE = "longitude";

    private AddTripStopViewModel viewModel;
    private TripDetailViewModel tripDetailViewModel;
    private AddTripStopAdapter adapter;
    private MapView mapView;
    private MapLibreMap mapLibreMap;

    private View searchBox;
    private EditText etSearch;
    private ImageButton btnClearSearch;
    private ImageButton btnAiToggle;
    private TextView tvAiLoading;
    private TextView tvEmpty;
    private View searchResultsContainer;

    private TextView tvSelectedPlaceName;
    private TextView tvSelectedPlaceAddress;
    private TextView tvSelectedPlaceRating;
    private TextView tvSelectedDate;
    private TextView tvSelectedTime;
    private MaterialButton btnAddToTrip;

    private BottomSheetBehavior<View> bottomSheetBehavior;
    private final List<AddTripStopViewModel.StopSearchResultItem> currentResults = new ArrayList<>();
    private AddTripStopViewModel.StopSearchResultItem selectedItem;
    private final Calendar selectedDateTime = Calendar.getInstance();
    private long tripStartAt = -1L;
    private long tripEndAt = -1L;

    private final SimpleDateFormat selectedDateFormatter =
            new SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault());
    private final SimpleDateFormat selectedTimeFormatter =
            new SimpleDateFormat("HH:mm", Locale.getDefault());

    private final TextWatcher queryWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            String value = s == null ? "" : s.toString().trim();
            btnClearSearch.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
            if (value.isEmpty()) {
                viewModel.search("");
                currentResults.clear();
                adapter.submitItems(Collections.emptyList());
                clearMapResults();
                bindSelectedPlace(null);
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_trip_stop, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(AddTripStopViewModel.class);
        tripDetailViewModel = new ViewModelProvider(this).get(TripDetailViewModel.class);

        String tripId = "";
        Bundle args = getArguments();
        if (args != null) {
            tripId = args.getString("tripId", "");
        }
        if (tripId == null || tripId.trim().isEmpty()) {
            NavBackStackEntry previousEntry = Navigation.findNavController(view).getPreviousBackStackEntry();
            if (previousEntry != null && previousEntry.getArguments() != null) {
                tripId = previousEntry.getArguments().getString("tripId", "");
            }
        }
        if (tripId == null) {
            tripId = "";
        }

        viewModel.setTripId(tripId);
        tripDetailViewModel.loadTrip(tripId);

        mapView = view.findViewById(R.id.map_view_add_stop);
        initializeMapView(savedInstanceState);

        searchBox = view.findViewById(R.id.layout_search_box);
        etSearch = view.findViewById(R.id.et_search_stop);
        btnClearSearch = view.findViewById(R.id.btn_clear_search);
        btnAiToggle = view.findViewById(R.id.btn_ai_toggle);
        tvAiLoading = view.findViewById(R.id.tv_ai_loading);
        tvEmpty = view.findViewById(R.id.tv_empty_state);
        searchResultsContainer = view.findViewById(R.id.layout_search_results_container);

        tvSelectedPlaceName = view.findViewById(R.id.tv_selected_place_name);
        tvSelectedPlaceAddress = view.findViewById(R.id.tv_selected_place_address);
        tvSelectedPlaceRating = view.findViewById(R.id.tv_selected_place_rating);
        tvSelectedDate = view.findViewById(R.id.tv_selected_date);
        tvSelectedTime = view.findViewById(R.id.tv_selected_time);
        btnAddToTrip = view.findViewById(R.id.btn_add_to_trip);

        RecyclerView rvResults = view.findViewById(R.id.rv_search_results);
        ImageButton btnBack = view.findViewById(R.id.btn_add_stop_back);
        View bottomSheetContainer = view.findViewById(R.id.bottom_sheet_container);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetContainer);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        adapter = new AddTripStopAdapter(this::onResultItemSelected);
        rvResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvResults.setAdapter(adapter);

        btnBack.setOnClickListener(v -> Navigation.findNavController(v).popBackStack());
        btnAiToggle.setOnClickListener(v -> viewModel.toggleAiMode());
        btnClearSearch.setOnClickListener(v -> etSearch.setText(""));

        tvSelectedDate.setOnClickListener(v -> showDatePicker());
        tvSelectedTime.setOnClickListener(v -> showTimePicker());
        btnAddToTrip.setOnClickListener(v -> addSelectedPlaceToTrip());

        etSearch.addTextChangedListener(queryWatcher);
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            boolean submit = actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER);
            if (submit) {
                viewModel.search(v.getText() == null ? "" : v.getText().toString());
                return true;
            }
            return false;
        });

        observeUi();
        initializeDateTime();
        bindSelectedPlace(null);
    }

    private void initializeDateTime() {
        selectedDateTime.setTimeInMillis(System.currentTimeMillis());
        updateDateTimeLabels();
    }

    private void initializeMapView(@Nullable Bundle savedInstanceState) {
        if (mapView == null) {
            return;
        }
        MapLibre.getInstance(requireContext().getApplicationContext(), "", WellKnownTileServer.MapLibre);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this::configureMap);
    }

    private void configureMap(@NonNull MapLibreMap map) {
        this.mapLibreMap = map;
        String styleUrl = TextUtils.isEmpty(BuildConfig.MAPLIBRE_STYLE_URL)
                ? DEFAULT_STYLE_URL
                : BuildConfig.MAPLIBRE_STYLE_URL;

        map.setStyle(new Style.Builder().fromUri(styleUrl), style -> {
            SocialMapStyleUtils.applyPaletteForCurrentMode(requireContext(), style);
            ensurePlaceLayers(style);

            map.setCameraPosition(new CameraPosition.Builder()
                    .target(new LatLng(10.7769, 106.7009))
                    .zoom(12.0)
                    .build());

            map.addOnMapClickListener(point -> {
                AddTripStopViewModel.StopSearchResultItem hit = findRenderedPlaceAt(point);
                if (hit != null) {
                    onResultItemSelected(hit);
                    return true;
                }
                return false;
            });
        });
    }

    private void observeUi() {
        viewModel.getAiToggleEnabled().observe(getViewLifecycleOwner(), enabled -> {
            boolean isEnabled = Boolean.TRUE.equals(enabled);
            btnAiToggle.setEnabled(isEnabled);
            btnAiToggle.setAlpha(isEnabled ? 1f : 0.4f);
            if (!isEnabled) {
                styleSearchBarForAi(false);
            }
        });

        viewModel.getAiModeEnabled().observe(getViewLifecycleOwner(), enabled -> {
            boolean aiEnabled = Boolean.TRUE.equals(enabled);
            btnAiToggle.setImageResource(aiEnabled ? R.drawable.ic_ai_sparkle_on : R.drawable.ic_ai_sparkle_off);
            styleSearchBarForAi(aiEnabled);
        });

        viewModel.getSearchHint().observe(getViewLifecycleOwner(), hint -> etSearch.setHint(hint));

        tripDetailViewModel.getTrip().observe(getViewLifecycleOwner(), this::onTripLoaded);

        viewModel.getSearchState().observe(getViewLifecycleOwner(), state -> {
            if (state instanceof AddTripStopViewModel.SearchState.Loading) {
                AddTripStopViewModel.SearchState.Loading loadingState =
                        (AddTripStopViewModel.SearchState.Loading) state;
                showAiLoading(loadingState.aiMode);
                if (searchResultsContainer != null) {
                    searchResultsContainer.setVisibility(View.GONE);
                }
                tvEmpty.setVisibility(View.GONE);
                return;
            }

            showAiLoading(false);

            if (state instanceof AddTripStopViewModel.SearchState.Success) {
                AddTripStopViewModel.SearchState.Success success =
                        (AddTripStopViewModel.SearchState.Success) state;
                currentResults.clear();
                currentResults.addAll(success.items);
                adapter.submitItems(success.items);
                if (searchResultsContainer != null) {
                    searchResultsContainer.setVisibility(
                            success.items.isEmpty() ? View.GONE : View.VISIBLE);
                }
                renderSearchResultsOnMap(success.items);
                tvEmpty.setVisibility(View.GONE);

                if (!success.items.isEmpty()) {
                    onResultItemSelected(success.items.get(0));
                }
                return;
            }

            currentResults.clear();
            adapter.submitItems(Collections.emptyList());
            clearMapResults();
            bindSelectedPlace(null);
            if (searchResultsContainer != null) {
                searchResultsContainer.setVisibility(View.GONE);
            }
            if (state instanceof AddTripStopViewModel.SearchState.Empty) {
                AddTripStopViewModel.SearchState.Empty empty =
                        (AddTripStopViewModel.SearchState.Empty) state;
                tvEmpty.setText(empty.message);
                tvEmpty.setVisibility(View.VISIBLE);
            } else {
                tvEmpty.setVisibility(View.GONE);
            }
        });
    }

    private void onTripLoaded(@Nullable TripPlan trip) {
        if (trip == null) {
            return;
        }
        tripStartAt = trip.getStartAt();
        tripEndAt = trip.getEndAt();

        if (tripStartAt > 0 && selectedDateTime.getTimeInMillis() < tripStartAt) {
            selectedDateTime.setTimeInMillis(tripStartAt);
        }
        if (tripEndAt > 0 && selectedDateTime.getTimeInMillis() > tripEndAt) {
            selectedDateTime.setTimeInMillis(tripEndAt);
        }
        updateDateTimeLabels();
    }

    private void onResultItemSelected(@NonNull AddTripStopViewModel.StopSearchResultItem item) {
        selectedItem = item;
        bindSelectedPlace(item);
        renderSelectedPlace(item);
        if (mapLibreMap != null && item.place != null && item.place.location != null) {
            mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(item.place.location.latitude, item.place.location.longitude),
                    15.8
            ));
        }
        if (bottomSheetBehavior != null) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    private void bindSelectedPlace(@Nullable AddTripStopViewModel.StopSearchResultItem item) {
        if (item == null || item.place == null) {
            tvSelectedPlaceName.setText(R.string.trip_select_place);
            tvSelectedPlaceAddress.setText("");
            tvSelectedPlaceRating.setText("");
            btnAddToTrip.setEnabled(false);
            return;
        }

        Place place = item.place;
        tvSelectedPlaceName.setText(place.name == null || place.name.trim().isEmpty()
                ? getString(R.string.trip_stop_untitled)
                : place.name);
        tvSelectedPlaceAddress.setText(place.address == null ? "" : place.address);
        tvSelectedPlaceRating.setText(String.format(Locale.getDefault(), "Rating %.1f", place.rating));
        btnAddToTrip.setEnabled(true);
    }

    private void addSelectedPlaceToTrip() {
        if (selectedItem == null) {
            AppSnackbar.show(requireContext(), R.string.trip_select_place);
            return;
        }

        if (viewModel.getCurrentTripId().trim().isEmpty()) {
            AppSnackbar.show(requireContext(), R.string.trip_create_failed);
            return;
        }

        long selectedMillis = selectedDateTime.getTimeInMillis();
        if (!isWithinTripDateRange(selectedMillis)) {
            AppSnackbar.show(requireContext(), R.string.trip_dates_invalid);
            return;
        }

        boolean added = viewModel.addStopToTrip(selectedItem, selectedMillis);
        if (!added) {
            AppSnackbar.show(requireContext(), R.string.trip_create_failed);
            return;
        }
        AppSnackbar.show(requireContext(), R.string.trip_stop_added_to_trip);
        Navigation.findNavController(requireView()).popBackStack();
    }

    private void showDatePicker() {
        Calendar copy = (Calendar) selectedDateTime.clone();
        DatePickerDialog dialog = new DatePickerDialog(
                requireContext(),
                (picker, year, month, dayOfMonth) -> {
                    selectedDateTime.set(Calendar.YEAR, year);
                    selectedDateTime.set(Calendar.MONTH, month);
                    selectedDateTime.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateDateTimeLabels();
                },
                copy.get(Calendar.YEAR),
                copy.get(Calendar.MONTH),
                copy.get(Calendar.DAY_OF_MONTH));

        if (tripStartAt > 0) {
            dialog.getDatePicker().setMinDate(tripStartAt);
        }
        if (tripEndAt > 0) {
            dialog.getDatePicker().setMaxDate(tripEndAt);
        }
        dialog.show();
    }

    private void showTimePicker() {
        Calendar copy = (Calendar) selectedDateTime.clone();
        TimePickerDialog dialog = new TimePickerDialog(
                requireContext(),
                (picker, hourOfDay, minute) -> {
                    selectedDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    selectedDateTime.set(Calendar.MINUTE, minute);
                    selectedDateTime.set(Calendar.SECOND, 0);
                    selectedDateTime.set(Calendar.MILLISECOND, 0);
                    updateDateTimeLabels();
                },
                copy.get(Calendar.HOUR_OF_DAY),
                copy.get(Calendar.MINUTE),
                true);
        dialog.show();
    }

    private void updateDateTimeLabels() {
        Date date = new Date(selectedDateTime.getTimeInMillis());
        tvSelectedDate.setText(selectedDateFormatter.format(date));
        tvSelectedTime.setText(selectedTimeFormatter.format(date));
    }

    private boolean isWithinTripDateRange(long timestamp) {
        if (tripStartAt > 0 && timestamp < tripStartAt) {
            return false;
        }
        return tripEndAt <= 0 || timestamp <= tripEndAt;
    }

    private void styleSearchBarForAi(boolean aiEnabled) {
        searchBox.setBackgroundResource(aiEnabled
                ? R.drawable.bg_ai_search_box_enabled
                : R.drawable.bg_ai_search_box_default);
    }

    private void showAiLoading(boolean visible) {
        tvAiLoading.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            tvAiLoading.clearAnimation();
            return;
        }

        tvAiLoading.setTextColor(android.graphics.Color.parseColor("#2ECC71"));
        AlphaAnimation pulse = new AlphaAnimation(0.35f, 1.0f);
        pulse.setDuration(650L);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(Animation.INFINITE);
        tvAiLoading.startAnimation(pulse);
    }

    private void ensurePlaceLayers(@NonNull Style style) {
        if (style.getSource(SEARCH_SOURCE_ID) == null) {
            style.addSource(new GeoJsonSource(SEARCH_SOURCE_ID,
                    FeatureCollection.fromFeatures(Collections.emptyList())));
        }
        if (style.getSource(SELECTED_SOURCE_ID) == null) {
            style.addSource(new GeoJsonSource(SELECTED_SOURCE_ID,
                    FeatureCollection.fromFeatures(Collections.emptyList())));
        }

        style.addImage(MARKER_ICON_SEARCH_ID, loadMarkerBitmap(R.drawable.ic_marker_search));
        style.addImage(MARKER_ICON_SELECTED_ID, loadMarkerBitmap(R.drawable.ic_marker_selected));

        if (style.getLayer(SEARCH_LAYER_ID) == null) {
            SymbolLayer searchLayer = new SymbolLayer(SEARCH_LAYER_ID, SEARCH_SOURCE_ID);
            searchLayer.setProperties(
                    PropertyFactory.iconImage(MARKER_ICON_SEARCH_ID),
                    PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconSize(0.82f));
            style.addLayer(searchLayer);
        }

        if (style.getLayer(SELECTED_LAYER_ID) == null) {
            SymbolLayer selectedLayer = new SymbolLayer(SELECTED_LAYER_ID, SELECTED_SOURCE_ID);
            selectedLayer.setProperties(
                    PropertyFactory.iconImage(MARKER_ICON_SELECTED_ID),
                    PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconSize(0.96f));
            style.addLayer(selectedLayer);
        }
    }

    @NonNull
    private Bitmap loadMarkerBitmap(int drawableRes) {
        Drawable drawable = AppCompatResources.getDrawable(requireContext(), drawableRes);
        if (drawable == null) {
            return Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        }
        Drawable mutable = DrawableCompat.wrap(drawable.mutate());
        int width = Math.max(mutable.getIntrinsicWidth(), 48);
        int height = Math.max(mutable.getIntrinsicHeight(), 48);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        mutable.setBounds(0, 0, width, height);
        mutable.draw(canvas);
        return bitmap;
    }

    private void renderSearchResultsOnMap(@NonNull List<AddTripStopViewModel.StopSearchResultItem> items) {
        if (mapLibreMap == null) {
            return;
        }

        List<Feature> features = new ArrayList<>();
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        int included = 0;
        for (AddTripStopViewModel.StopSearchResultItem item : items) {
            if (item == null || item.place == null || item.place.location == null) {
                continue;
            }

            Place place = item.place;
            Feature feature = Feature.fromGeometry(
                    Point.fromLngLat(place.location.longitude, place.location.latitude));
            feature.addStringProperty(PROP_PLACE_ID, place.id == null ? "" : place.id);
            feature.addNumberProperty(PROP_RESULT_INDEX, features.size());
            feature.addNumberProperty(PROP_LATITUDE, place.location.latitude);
            feature.addNumberProperty(PROP_LONGITUDE, place.location.longitude);
            features.add(feature);

            builder.include(new LatLng(place.location.latitude, place.location.longitude));
            included++;
        }
        setFeatures(SEARCH_SOURCE_ID, features);

        if (included > 1) {
            mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 160));
        } else if (included == 1 && !items.isEmpty() && items.get(0).place != null && items.get(0).place.location != null) {
            mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(items.get(0).place.location.latitude, items.get(0).place.location.longitude),
                    15.0
            ));
        }
    }

    private void renderSelectedPlace(@Nullable AddTripStopViewModel.StopSearchResultItem item) {
        if (item == null || item.place == null || item.place.location == null) {
            setFeatures(SELECTED_SOURCE_ID, Collections.emptyList());
            return;
        }

        Feature feature = Feature.fromGeometry(
                Point.fromLngLat(item.place.location.longitude, item.place.location.latitude));
        feature.addStringProperty(PROP_PLACE_ID, item.place.id == null ? "" : item.place.id);
        setFeatures(SELECTED_SOURCE_ID, Collections.singletonList(feature));
    }

    private void clearMapResults() {
        setFeatures(SEARCH_SOURCE_ID, Collections.emptyList());
        setFeatures(SELECTED_SOURCE_ID, Collections.emptyList());
    }

    private void setFeatures(@NonNull String sourceId, @NonNull List<Feature> features) {
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

    @Nullable
    private AddTripStopViewModel.StopSearchResultItem findRenderedPlaceAt(@NonNull LatLng point) {
        if (mapLibreMap == null || mapView == null) {
            return null;
        }

        PointF screen = mapLibreMap.getProjection().toScreenLocation(point);
        List<Feature> features = mapLibreMap.queryRenderedFeatures(screen, SEARCH_LAYER_ID, SELECTED_LAYER_ID);
        if (features.isEmpty()) {
            return null;
        }

        String placeId = features.get(0).getStringProperty(PROP_PLACE_ID);
        Number resultIndex = features.get(0).getNumberProperty(PROP_RESULT_INDEX);
        if (resultIndex != null) {
            int index = resultIndex.intValue();
            if (index >= 0 && index < currentResults.size()) {
                return currentResults.get(index);
            }
        }

        AddTripStopViewModel.StopSearchResultItem byId = findCurrentResultById(currentResults, placeId);
        if (byId != null) {
            return byId;
        }

        Double latitude = getFeatureDouble(features.get(0), PROP_LATITUDE);
        Double longitude = getFeatureDouble(features.get(0), PROP_LONGITUDE);
        if (latitude != null && longitude != null) {
            return findNearestCurrentResult(currentResults, latitude, longitude);
        }

        return findNearestCurrentResult(currentResults, point.getLatitude(), point.getLongitude());
    }

    @Nullable
    static AddTripStopViewModel.StopSearchResultItem findCurrentResultById(
            @NonNull List<AddTripStopViewModel.StopSearchResultItem> items,
            @Nullable String placeId) {
        if (placeId == null || placeId.trim().isEmpty()) {
            return null;
        }
        for (AddTripStopViewModel.StopSearchResultItem item : items) {
            if (item != null && item.place != null && placeId.equals(item.place.id)) {
                return item;
            }
        }
        return null;
    }

    @Nullable
    static AddTripStopViewModel.StopSearchResultItem findNearestCurrentResult(
            @NonNull List<AddTripStopViewModel.StopSearchResultItem> items,
            double latitude,
            double longitude) {
        AddTripStopViewModel.StopSearchResultItem best = null;
        double bestDistance = Double.MAX_VALUE;
        for (AddTripStopViewModel.StopSearchResultItem item : items) {
            if (item == null || item.place == null || item.place.location == null) {
                continue;
            }
            double latDiff = item.place.location.latitude - latitude;
            double lonDiff = item.place.location.longitude - longitude;
            double distance = (latDiff * latDiff) + (lonDiff * lonDiff);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = item;
            }
        }
        return best;
    }

    @Nullable
    private Double getFeatureDouble(@NonNull Feature feature, @NonNull String property) {
        Number value = feature.getNumberProperty(property);
        return value != null ? value.doubleValue() : null;
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
    public void onDestroyView() {
        if (etSearch != null) {
            etSearch.removeTextChangedListener(queryWatcher);
        }
        if (mapView != null) {
            mapView.onDestroy();
            mapView = null;
        }
        super.onDestroyView();
    }
}
