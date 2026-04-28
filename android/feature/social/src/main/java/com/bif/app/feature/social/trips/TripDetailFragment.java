package com.bif.app.feature.social;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.bif.app.core.utils.UriUtils;
import com.bif.app.domain.model.TripPlan;
import com.bif.app.domain.model.TripStop;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class TripDetailFragment extends Fragment {

    private String tripId = "";
    private String tripTitle = "";
    private int tripMemberCount = 0;
    private TripDetailViewModel viewModel;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_trip_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            tripId = args.getString("tripId", "");
            tripTitle = args.getString("tripTitle", "");
        }

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        TextView tvTitle = view.findViewById(R.id.tv_trip_title);
        TabLayout tabLayout = view.findViewById(R.id.tab_layout_detail);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_trip_detail);
        ViewPager2 viewPager = view.findViewById(R.id.view_pager_detail);
        FloatingActionButton fabChat = view.findViewById(R.id.fab_chat);
        FloatingActionButton fabShowOnMap = view.findViewById(R.id.fab_show_on_map);
        View unreadDot = view.findViewById(R.id.view_chat_unread_dot);

        tvTitle.setText(tripTitle == null || tripTitle.trim().isEmpty()
                ? getString(R.string.trip_description_label)
                : tripTitle);

        viewPager.setAdapter(new TripDetailPagerAdapter(this, tripId));
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText(R.string.overview);
                    break;
                case 1:
                    tab.setText(R.string.itinerary);
                    break;
                case 2:
                default:
                    tab.setText(R.string.collab);
                    break;
            }
        }).attach();

        btnBack.setOnClickListener(v -> Navigation.findNavController(view).popBackStack());
        fabChat.setOnClickListener(v -> {
            viewModel.markGroupChatReadNow();
            Uri destUri = UriUtils.buildUri(UriUtils.PathTo.SOCIAL_CHAT).buildUpon()
                    .appendQueryParameter("chatType", "group")
                    .appendQueryParameter("chatId", tripId)
                    .appendQueryParameter("chatName", tripTitle == null ? "" : tripTitle)
                    .appendQueryParameter("avatarLetter", "T")
                    .appendQueryParameter("avatarColor", "0")
                    .appendQueryParameter("memberCount", String.valueOf(tripMemberCount))
                    .build();
            Navigation.findNavController(view).navigate(destUri);
        });
        fabShowOnMap.setOnClickListener(v -> {
            TripPlan trip = viewModel != null && viewModel.getTrip() != null
                    ? viewModel.getTrip().getValue()
                    : null;
            String stopsJson = serializeTripStops(trip != null ? trip.getStops() : null);
            if (stopsJson.isEmpty()) {
                Snackbar.make(view, R.string.trip_stop_no_stops_to_show, Snackbar.LENGTH_SHORT).show();
                return;
            }

            Uri destUri = UriUtils.buildUri(UriUtils.PathTo.MAP).buildUpon()
                    .appendQueryParameter("tripStopsJson", stopsJson)
                    .appendQueryParameter("sourceTripId", tripId == null ? "" : tripId)
                    .appendQueryParameter("sourceTripTitle", tripTitle == null ? "" : tripTitle)
                    .build();
            Navigation.findNavController(view).navigate(destUri);
        });

        viewModel = new ViewModelProvider(this).get(TripDetailViewModel.class);
        if (tripId != null && !tripId.trim().isEmpty()) {
            viewModel.loadTrip(tripId);
        }
        swipeRefreshLayout.setOnRefreshListener(() -> {
            viewModel.refreshTripContent();
            swipeRefreshLayout.postDelayed(() -> {
                if (isAdded()) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }, 1000L);
        });
        viewModel.getTrip().observe(getViewLifecycleOwner(), trip -> {
            tripMemberCount = resolveTripMemberCount(trip);
            swipeRefreshLayout.setRefreshing(false);
        });
        viewModel.getHasUnreadGroupMessages().observe(getViewLifecycleOwner(), hasUnread -> unreadDot.setVisibility(Boolean.TRUE.equals(hasUnread) ? View.VISIBLE : View.GONE));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.refreshUnreadState();
        }
    }

    private int resolveTripMemberCount(@Nullable TripPlan trip) {
        if (trip == null || trip.getParticipantIds() == null) {
            return 0;
        }
        return trip.getParticipantIds().size();
    }

    @NonNull
    private String serializeTripStops(@Nullable List<TripStop> stops) {
        if (stops == null || stops.isEmpty()) {
            return "";
        }

        List<TripStop> validStops = new ArrayList<>();
        for (TripStop stop : stops) {
            if (stop == null) {
                continue;
            }

            double lat = stop.getLatitude();
            double lng = stop.getLongitude();
            if (!Double.isFinite(lat) || !Double.isFinite(lng)) {
                continue;
            }
            if (Double.compare(lat, 0.0d) == 0 && Double.compare(lng, 0.0d) == 0) {
                continue;
            }
            validStops.add(stop);
        }

        if (validStops.isEmpty()) {
            return "";
        }

        Collections.sort(validStops, Comparator.comparingInt(TripStop::getOrderIndex));

        JSONArray items = new JSONArray();
        for (TripStop stop : validStops) {
            JSONObject item = new JSONObject();
            try {
                item.put("lat", stop.getLatitude());
                item.put("lng", stop.getLongitude());
                item.put("order", stop.getOrderIndex());
                item.put("title", stop.getTitle() == null ? "" : stop.getTitle());
                item.put("address", stop.getAddress() == null ? "" : stop.getAddress());
                item.put("note", stop.getNote() == null ? "" : stop.getNote());
                item.put("time", stop.getArrivalTime());
                items.put(item);
            } catch (JSONException ignored) {
                // Skip malformed stop payload entries.
            }
        }

        if (items.length() == 0) {
            return "";
        }
        return items.toString();
    }
}
