package com.bif.app.feature.social.trips;

import com.bif.app.feature.social.R;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bif.app.core.utils.AppSnackbar;
import com.bif.app.domain.model.TripPlan;
import com.bumptech.glide.Glide;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class TripOverviewFragment extends Fragment {

    private TripDetailViewModel tripDetailViewModel;

    private final ActivityResultLauncher<String> pickCoverImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) {
                    return;
                }

                String stagedPath = copyToInternalStorage(uri);
                if (stagedPath == null || stagedPath.trim().isEmpty()) {
                    if (isAdded()) {
                        AppSnackbar.show(requireContext(), R.string.trip_title_card_update_failed);
                    }
                    return;
                }

                if (tripDetailViewModel != null) {
                    tripDetailViewModel.stageTripCoverImageUpload(stagedPath);
                }
            });

    public static TripOverviewFragment newInstance(String tripId) {
        TripOverviewFragment fragment = new TripOverviewFragment();
        Bundle args = new Bundle();
        args.putString("tripId", tripId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_trip_overview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvTitle = view.findViewById(R.id.tv_overview_title);
        TextView tvStops = view.findViewById(R.id.tv_overview_stops);
        TextView tvTravelers = view.findViewById(R.id.tv_overview_travelers);
        TextView tvDates = view.findViewById(R.id.tv_overview_dates);
        TextView tvDescription = view.findViewById(R.id.tv_overview_description);
        ImageView ivCover = view.findViewById(R.id.iv_overview_cover);
        ImageButton btnEditCover = view.findViewById(R.id.btn_edit_overview_cover);

        tripDetailViewModel = new ViewModelProvider(requireParentFragment())
                .get(TripDetailViewModel.class);

        btnEditCover.setOnClickListener(v -> pickCoverImageLauncher.launch("image/*"));

        if (tripDetailViewModel.getTrip() != null) {
            tripDetailViewModel.getTrip().observe(getViewLifecycleOwner(), trip -> bindTrip(
                trip,
                tvTitle,
                tvStops,
                tvTravelers,
                tvDates,
                tvDescription,
                ivCover
            ));
        }
    }

    private void bindTrip(TripPlan trip,
                          TextView tvTitle,
                  TextView tvStops,
                  TextView tvTravelers,
                          TextView tvDates,
                          TextView tvDescription,
                          ImageView ivCover) {
        if (trip == null) {
            return;
        }

        String title = trip.getTitle() == null || trip.getTitle().trim().isEmpty()
            ? getString(R.string.trip_title_hint)
            : trip.getTitle().trim();
        tvTitle.setText(title);

        tvStops.setText(String.valueOf(trip.getStopCount()));
        int travelers = trip.getParticipantIds() == null ? 0 : trip.getParticipantIds().size();
        tvTravelers.setText(String.valueOf(travelers));

        SimpleDateFormat formatter = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        String start = trip.getStartAt() > 0 ? formatter.format(new Date(trip.getStartAt())) : "-";
        String end = trip.getEndAt() > 0 ? formatter.format(new Date(trip.getEndAt())) : "-";
        tvDates.setText(start + " - " + end);

        String description = trip.getDescription() == null || trip.getDescription().trim().isEmpty()
            ? getString(R.string.trip_overview_no_description)
            : trip.getDescription().trim();
        tvDescription.setText(description);

        bindTripCoverImage(trip, ivCover);
    }

    private void bindTripCoverImage(@NonNull TripPlan trip, @NonNull ImageView ivCover) {
        String localPath = normalize(trip.getLocalCoverImagePath());
        String remoteUrl = normalize(trip.getCoverImageUrl());
        String source = !localPath.isEmpty() ? localPath : remoteUrl;
        if (source.isEmpty()) {
            Glide.with(this).clear(ivCover);
            ivCover.setImageDrawable(null);
            return;
        }

        Glide.with(this)
                .load(source)
                .centerCrop()
                .into(ivCover);
    }

    private String copyToInternalStorage(@NonNull Uri sourceUri) {
        Context context = getContext();
        if (context == null) {
            return null;
        }
        try {
            File stagingDir = new File(context.getFilesDir(), "image-staging");
            if (!stagingDir.exists() && !stagingDir.mkdirs()) {
                return null;
            }

            String extension = resolveImageExtension(context, sourceUri);
            File outFile = new File(stagingDir,
                    "trip-cover-" + UUID.randomUUID() + "." + extension);
            try (InputStream input = context.getContentResolver().openInputStream(sourceUri);
                 FileOutputStream output = new FileOutputStream(outFile)) {
                if (input == null) {
                    return null;
                }

                byte[] buffer = new byte[8192];
                int len;
                while ((len = input.read(buffer)) != -1) {
                    output.write(buffer, 0, len);
                }
                output.flush();
            }
            return outFile.getAbsolutePath();
        } catch (Exception ex) {
            return null;
        }
    }

    private String resolveImageExtension(@NonNull Context context, @NonNull Uri sourceUri) {
        try {
            String mimeType = context.getContentResolver().getType(sourceUri);
            if (mimeType != null) {
                String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                if (ext != null && !ext.trim().isEmpty()) {
                    return ext.trim().toLowerCase();
                }
            }

            String path = sourceUri.getPath();
            if (path != null) {
                int dot = path.lastIndexOf('.');
                if (dot >= 0 && dot < path.length() - 1) {
                    String ext = path.substring(dot + 1).trim().toLowerCase();
                    if (!ext.isEmpty() && ext.length() <= 5) {
                        return ext;
                    }
                }
            }
        } catch (Exception ignored) {
            // Fall back to jpg when MIME/path inspection fails.
        }
        return "jpg";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}

