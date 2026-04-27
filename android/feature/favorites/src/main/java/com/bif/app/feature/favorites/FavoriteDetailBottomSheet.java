package com.bif.app.feature.favorites;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.Window;
import android.view.Gravity;
import android.widget.ImageButton;
import android.widget.RatingBar;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import com.bif.app.core.utils.AppSnackbar;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.bif.app.core.utils.UriUtils;
import com.bif.app.domain.model.Favorite;
import com.bif.app.domain.model.Group;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class FavoriteDetailBottomSheet extends BottomSheetDialogFragment {

    private FavoriteDetailViewModel viewModel;
    private TextView tvName;
    private TextView tvAddress;
    private TextView tvDescription;
    private TextView tvNotes;
    private RatingBar ratingBar;

    public static FavoriteDetailBottomSheet newInstance(@NonNull Favorite favorite) {
        FavoriteDetailBottomSheet sheet = new FavoriteDetailBottomSheet();
        Bundle args = new Bundle();
        args.putString("favId", favorite.id != null ? favorite.id : "");
        args.putString("favPlaceId", favorite.placeId != null ? favorite.placeId : "");
        args.putString("favName", favorite.name != null ? favorite.name : "");
        args.putString("favAddress", favorite.address != null ? favorite.address : "");
        args.putString("favDescription", favorite.description != null ? favorite.description : "");
        args.putString("favNotes", favorite.notes != null ? favorite.notes : "");
        args.putInt("favRating", favorite.rating);
        args.putString("favLatitude", String.valueOf(favorite.latitude));
        args.putString("favLongitude", String.valueOf(favorite.longitude));
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favorite_detail, container, false);
    }


    @Override
    public void onStart() {
        super.onStart();
        configureBottomSheetWindow();
        adjustForBottomNavigation();
    }

    private void configureBottomSheetWindow() {
        Dialog dialog = getDialog();
        if (!(dialog instanceof BottomSheetDialog)) {
            return;
        }

        BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) dialog;
        bottomSheetDialog.setCanceledOnTouchOutside(false);
        Window window = bottomSheetDialog.getWindow();
        if (window == null) {
            return;
        }

        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        window.setGravity(Gravity.BOTTOM);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(FavoriteDetailViewModel.class);
        bindViews(view);

        Favorite favorite = parseFavoriteArgs();
        if (favorite == null) {
            dismissAllowingStateLoss();
            return;
        }

        viewModel.initializeFavorite(favorite);
        setupActions(view);
        observeFavorite();
        observeDynamicRating();
    }

    private void adjustForBottomNavigation() {
        Dialog dialog = getDialog();
        if (!(dialog instanceof BottomSheetDialog) || getActivity() == null) {
            return;
        }

        int bottomNavId = requireContext().getResources()
                .getIdentifier("bottom_navigation", "id", requireContext().getPackageName());
        if (bottomNavId == 0) {
            return;
        }

        BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) dialog;
        FrameLayout bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        View bottomNavigation = getActivity().findViewById(bottomNavId);
        if (bottomSheet == null || bottomNavigation == null) {
            return;
        }

        bottomNavigation.post(() -> bottomSheet.post(() -> {
            int navHeight = bottomNavigation.getVisibility() == View.VISIBLE
                    ? bottomNavigation.getHeight() + bottomNavigation.getPaddingBottom()
                    : 0;

            View parent = (View) bottomSheet.getParent();
            if (parent != null && parent.getPaddingBottom() != navHeight) {
                parent.setPadding(
                        parent.getPaddingLeft(),
                        parent.getPaddingTop(),
                        parent.getPaddingRight(),
                        navHeight
                );
            }

            ViewGroup.LayoutParams params = bottomSheet.getLayoutParams();
            if (params instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) params;
                if (marginParams.bottomMargin != navHeight) {
                    marginParams.bottomMargin = navHeight;
                    bottomSheet.setLayoutParams(marginParams);
                }
            }

            bottomSheet.requestLayout();
        }));
    }

    private void bindViews(@NonNull View view) {
        tvName = view.findViewById(R.id.tv_detail_name);
        tvAddress = view.findViewById(R.id.tv_detail_address);
        tvDescription = view.findViewById(R.id.tv_detail_description);
        tvNotes = view.findViewById(R.id.tv_detail_notes);
        ratingBar = view.findViewById(R.id.rating_bar);
    }

    private void setupActions(@NonNull View view) {
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        MaterialButton btnSharePlace = view.findViewById(R.id.btn_share_place);
        MaterialButton btnViewOnMap = view.findViewById(R.id.btn_navigate_place);
        MaterialButton btnEditNote = view.findViewById(R.id.btn_edit_note);

        btnBack.setOnClickListener(v -> dismissAllowingStateLoss());
        btnSharePlace.setOnClickListener(v -> {
            Favorite favorite = viewModel.getCurrentFavorite().getValue();
            if (favorite != null) {
                showShareToGroupDialog(favorite);
            }
        });
        btnViewOnMap.setOnClickListener(v -> {
            Favorite favorite = viewModel.getCurrentFavorite().getValue();
            if (favorite != null) {
                viewOnMap(favorite);
            }
        });
        btnEditNote.setOnClickListener(v -> showEditNoteDialog());
    }

    private void observeFavorite() {
        viewModel.getCurrentFavorite().observe(getViewLifecycleOwner(), favorite -> {
            if (favorite == null) {
                return;
            }
            tvName.setText(favorite.name);
            tvAddress.setText(defaultText(favorite.address, getString(R.string.favorite_address_unavailable)));
            tvDescription.setText(defaultText(favorite.description, getString(R.string.favorite_description_empty)));
            tvNotes.setText(defaultText(favorite.notes, getString(R.string.favorite_note_empty)));
        });
    }

    private void observeDynamicRating() {
        viewModel.getDynamicRating().observe(getViewLifecycleOwner(), rating -> {
            if (rating == null) {
                return;
            }
            ratingBar.setRating(Math.max(0f, rating));
        });
    }

    @Nullable
    private Favorite parseFavoriteArgs() {
        Bundle args = getArguments();
        if (args == null) {
            return null;
        }

        Favorite favorite = new Favorite();
        favorite.id = args.getString("favId", "");
        favorite.placeId = args.getString("favPlaceId", "");
        favorite.name = args.getString("favName", "");
        favorite.address = args.getString("favAddress", "");
        favorite.description = args.getString("favDescription", "");
        favorite.notes = args.getString("favNotes", "");
        favorite.rating = args.getInt("favRating", 0);
        favorite.latitude = parseDouble(args.getString("favLatitude", ""));
        favorite.longitude = parseDouble(args.getString("favLongitude", ""));
        return favorite;
    }

    private void showShareToGroupDialog(@NonNull Favorite favorite) {
        List<Group> groups = viewModel.getGroups().getValue();
        if (groups == null || groups.isEmpty()) {
            AppSnackbar.show(requireContext(), R.string.no_group_available);
            return;
        }

        String[] groupNames = new String[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            groupNames[i] = groups.get(i).getName();
        }

        new MaterialAlertDialogBuilder(
                requireContext(),
                com.bif.app.core.R.style.ThemeOverlay_BIFLocator_MaterialAlertDialog
        )
                .setTitle(R.string.select_group_to_share)
                .setItems(groupNames, (dialog, which) -> {
                    Group targetGroup = groups.get(which);
                    String sharedLink = buildLocationQuery(favorite);

                    android.net.Uri destUri = UriUtils.buildUri(UriUtils.PathTo.SOCIAL_CHAT).buildUpon()
                            .appendQueryParameter("chatType", "group")
                            .appendQueryParameter("chatId", targetGroup.getServerId())
                            .appendQueryParameter("chatName", targetGroup.getName())
                            .appendQueryParameter("avatarLetter", targetGroup.getAvatarLetter())
                            .appendQueryParameter("avatarColor", String.valueOf(targetGroup.getAvatarColor()))
                            .appendQueryParameter("memberCount", String.valueOf(targetGroup.getMemberCount()))
                            .appendQueryParameter("sharedPlaceName", favorite.name)
                            .appendQueryParameter("sharedPlaceAddress", favorite.address)
                            .appendQueryParameter("sharedPlaceLink", sharedLink)
                            .build();

                    NavController navController = resolveNavController();
                    if (navController != null) {
                        dismissAllowingStateLoss();
                        navController.navigate(destUri);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showEditNoteDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_favorite_note, null);
        TextInputEditText input = dialogView.findViewById(R.id.et_favorite_note);
        Favorite favorite = viewModel.getCurrentFavorite().getValue();
        if (favorite != null && !TextUtils.isEmpty(favorite.notes)) {
            input.setText(favorite.notes);
            if (input.getText() != null) {
                input.setSelection(input.getText().length());
            }
        }

        new MaterialAlertDialogBuilder(
                requireContext(),
                com.bif.app.core.R.style.ThemeOverlay_BIFLocator_MaterialAlertDialog
        )
                .setTitle(R.string.edit_note)
                .setView(dialogView)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save_note, (dialog, which) -> {
                    String note = input.getText() != null ? input.getText().toString() : "";
                    viewModel.updateNotes(note);
                    AppSnackbar.show(requireContext(), R.string.favorite_note_saved);
                })
                .show();
    }

    private void viewOnMap(@NonNull Favorite favorite) {
        android.net.Uri mapUri = UriUtils.buildUri(UriUtils.PathTo.MAP)
                .buildUpon()
                .appendQueryParameter("location", buildLocationQuery(favorite))
                .appendQueryParameter("focusName", favorite.name != null ? favorite.name : "")
                .appendQueryParameter("focusAddress", favorite.address != null ? favorite.address : "")
                .build();
        NavController navController = resolveNavController();
        if (navController != null) {
            dismissAllowingStateLoss();
            navController.navigate(mapUri);
        }
    }

    @Nullable
    private NavController resolveNavController() {
        if (getParentFragment() != null) {
            return Navigation.findNavController(getParentFragment().requireView());
        }
        return null;
    }

    @NonNull
    private String buildLocationQuery(@NonNull Favorite favorite) {
        if (Double.isFinite(favorite.latitude) && Double.isFinite(favorite.longitude)
                && (favorite.latitude != 0.0 || favorite.longitude != 0.0)) {
            return favorite.latitude + "," + favorite.longitude;
        }

        if (!TextUtils.isEmpty(favorite.address)) {
            return favorite.address;
        }

        return favorite.name != null ? favorite.name : "";
    }

    private double parseDouble(@Nullable String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return 0.0;
        }

        try {
            return Double.parseDouble(rawValue.trim());
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    @NonNull
    private String defaultText(@Nullable String value, @NonNull String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
