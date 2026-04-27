package com.bif.app.feature.social.trips;

import com.bif.app.feature.social.R;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class TripDetailPagerAdapter extends FragmentStateAdapter {

    private final String tripId;

    public TripDetailPagerAdapter(@NonNull Fragment fragment, String tripId) {
        super(fragment);
        this.tripId = tripId;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return TripOverviewFragment.newInstance(tripId);
            case 1:
                return TripItineraryFragment.newInstance(tripId);
            case 2:
            default:
                return TripCollabFragment.newInstance(tripId);
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}

