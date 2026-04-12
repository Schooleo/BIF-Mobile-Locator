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
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

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
        ImageButton btnHome = view.findViewById(R.id.btn_home);
        TextView tvTitle = view.findViewById(R.id.tv_trip_title);
        TabLayout tabLayout = view.findViewById(R.id.tab_layout_detail);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_trip_detail);
        ViewPager2 viewPager = view.findViewById(R.id.view_pager_detail);
        FloatingActionButton fabChat = view.findViewById(R.id.fab_chat);
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
        btnHome.setOnClickListener(v -> Navigation.findNavController(view)
                .navigate(UriUtils.buildUri(UriUtils.PathTo.SOCIAL)));
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
}
