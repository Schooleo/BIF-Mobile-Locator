package com.bif.app.feature.favorites;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.bif.app.core.utils.UriUtils;
import com.bif.app.domain.model.Group;

import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class FavoriteDetailFragment extends Fragment {

    private FavoriteDetailViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favorite_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(FavoriteDetailViewModel.class);

        Bundle args = getArguments();
        if (args == null) return;

        String name = args.getString("favName", "");
        String address = args.getString("favAddress", "");
        String description = args.getString("favDescription", "");
        String notes = args.getString("favNotes", "");
        int rating = args.getInt("favRating", 0);
        String latitude = args.getString("favLatitude", "");
        String longitude = args.getString("favLongitude", "");

        TextView tvName = view.findViewById(R.id.tv_detail_name);
        TextView tvAddress = view.findViewById(R.id.tv_detail_address);
        TextView tvDescription = view.findViewById(R.id.tv_detail_description);
        TextView tvNotes = view.findViewById(R.id.tv_detail_notes);
        RatingBar ratingBar = view.findViewById(R.id.rating_bar);
        ImageView imgDetail = view.findViewById(R.id.img_detail);
        com.google.android.material.button.MaterialButton btnSharePlace = view.findViewById(R.id.btn_share_place);
        com.google.android.material.button.MaterialButton btnNavigatePlace = view.findViewById(R.id.btn_navigate_place);

        tvName.setText(name);
        tvAddress.setText(address);
        tvDescription.setText(description);
        tvNotes.setText(notes);
        ratingBar.setRating(rating);

        imgDetail.setImageResource(android.R.drawable.ic_menu_gallery);

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> Navigation.findNavController(v).popBackStack());

        btnSharePlace.setOnClickListener(v -> showShareToGroupDialog(name, address, latitude, longitude));
        btnNavigatePlace.setOnClickListener(v -> navigateToPlace(name, address, latitude, longitude));
    }

    private void showShareToGroupDialog(String placeName,
                                        String placeAddress,
                                        String latitude,
                                        String longitude) {
        List<Group> groups = viewModel.getGroups().getValue();
        if (groups == null || groups.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_group_available, Toast.LENGTH_SHORT).show();
            return;
        }

        String[] groupNames = new String[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            groupNames[i] = groups.get(i).getName();
        }

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.select_group_to_share)
                .setItems(groupNames, (dialog, which) -> {
                    Group targetGroup = groups.get(which);
                    String sharedLink = (latitude != null && !latitude.isEmpty() && longitude != null && !longitude.isEmpty())
                            ? latitude + "," + longitude
                            : placeAddress;

                    android.net.Uri destUri = UriUtils.buildUri(UriUtils.PathTo.SOCIAL_CHAT).buildUpon()
                            .appendQueryParameter("chatType", "group")
                            .appendQueryParameter("chatId", targetGroup.getServerId())
                            .appendQueryParameter("chatName", targetGroup.getName())
                            .appendQueryParameter("avatarLetter", targetGroup.getAvatarLetter())
                            .appendQueryParameter("avatarColor", String.valueOf(targetGroup.getAvatarColor()))
                            .appendQueryParameter("memberCount", String.valueOf(targetGroup.getMemberCount()))
                            .appendQueryParameter("sharedPlaceName", placeName)
                            .appendQueryParameter("sharedPlaceAddress", placeAddress)
                            .appendQueryParameter("sharedPlaceLink", sharedLink)
                            .build();

                    Navigation.findNavController(requireView()).navigate(destUri);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void navigateToPlace(String placeName,
                                 String placeAddress,
                                 String latitude,
                                 String longitude) {
        String query;
        if (latitude != null && !latitude.isEmpty() && longitude != null && !longitude.isEmpty()) {
            query = latitude + "," + longitude;
        } else if (placeAddress != null && !placeAddress.isEmpty()) {
            query = placeAddress;
        } else {
            query = placeName;
        }

        android.net.Uri mapUri = UriUtils.buildUri(UriUtils.PathTo.MAP)
                .buildUpon()
                .appendQueryParameter("location", query)
                .build();
        Navigation.findNavController(requireView()).navigate(mapUri);
    }
}
