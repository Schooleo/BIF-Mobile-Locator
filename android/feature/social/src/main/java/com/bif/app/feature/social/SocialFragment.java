package com.bif.app.feature.social;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import com.bif.app.core.utils.AppSnackbar;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bif.app.core.utils.DialogUtils;
import com.bif.app.core.utils.UriUtils;
import com.bif.app.core.utils.UserPreferences;
import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.Friendship;
import com.bif.app.domain.model.TripPlan;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.tabs.TabLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class SocialFragment extends Fragment {

    private TabLayout tabLayout;
    private RecyclerView recyclerView;
    private ProgressBar progressLoading;
    private LinearLayout stateLayout;
    private TextView tvStateMessage;
    private Button btnRetry;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FriendsAdapter friendsAdapter;
    private TripListAdapter tripListAdapter;
    private SocialViewModel viewModel;
    private boolean isActionLoading = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_social, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.retryTrips();
            viewModel.retryFriends();
            viewModel.refreshRequestsOnly();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && viewModel != null) {
            viewModel.retryTrips();
            viewModel.retryFriends();
            viewModel.refreshRequestsOnly();
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(SocialViewModel.class);

        tabLayout = view.findViewById(R.id.tab_layout);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh);
        recyclerView = view.findViewById(R.id.recycler_view);
        progressLoading = view.findViewById(R.id.progress_loading);
        stateLayout = view.findViewById(R.id.layout_state);
        tvStateMessage = view.findViewById(R.id.tv_state_message);
        btnRetry = view.findViewById(R.id.btn_retry);

        swipeRefreshLayout.setOnRefreshListener(this::refreshCurrentTab);
        btnRetry.setOnClickListener(v -> refreshCurrentTab());

        setupRecyclerView();
        setupTabs();
        observeViewModel();
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        tripListAdapter = new TripListAdapter(new TripListAdapter.OnTripActionListener() {
            @Override
            public void onCreateTripClick() {
                showCreateTripDialog();
            }

            @Override
            public void onTripClick(TripPlan trip) {
                navigateToTripDetail(trip);
            }

            @Override
            public void onTripMoreClick(TripPlan trip, View anchorView) {
                showTripOptionsMenu(trip, anchorView);
            }
        });

        friendsAdapter = new FriendsAdapter(new FriendsAdapter.OnFriendActionListener() {
            @Override
            public void onAddFriendClick() {
                showAddFriendDialog();
            }

            @Override
            public void onAcceptRequestClick(Friendship friendship) {
                friendsAdapter.removePendingRequestOptimistically(friendship.getId());
                viewModel.acceptFriendRequest(friendship.getId());
            }

            @Override
            public void onRejectRequestClick(Friendship friendship) {
                friendsAdapter.removePendingRequestOptimistically(friendship.getId());
                viewModel.rejectFriendRequest(friendship.getId());
            }

            @Override
            public void onFriendClick(Friend friend) {
                navigateToChatFromFriend(friend);
            }

            @Override
            public void onDeleteFriendClick(Friend friend, int position) {
                DialogUtils.showConfirmDialog(requireContext(),
                        getString(R.string.unfriend_title),
                        getString(R.string.unfriend_confirm_message, friend.getName()),
                        getString(R.string.unfriend_action),
                        getString(R.string.cancel),
                        () -> {
                            friendsAdapter.removeFriendOptimistically(friend.getId());
                            viewModel.deleteFriend(friend);
                        });
            }
        });

        recyclerView.setAdapter(tripListAdapter);
    }

    private void observeViewModel() {
        viewModel.getTripActionMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                int textRes;
                switch (message) {
                    case "__MSG_TRIP_CREATE_SUCCESS__":
                        textRes = R.string.trip_create_success;
                        break;
                    case "__MSG_TRIP_UPDATE_SUCCESS__":
                        textRes = R.string.trip_update_success;
                        break;
                    case "__MSG_TRIP_DELETE_SUCCESS__":
                        textRes = R.string.trip_delete_success;
                        break;
                    case "__MSG_TRIP_UPDATE_FAILED__":
                        textRes = R.string.trip_update_failed;
                        break;
                    case "__MSG_TRIP_DELETE_FAILED__":
                        textRes = R.string.trip_delete_failed;
                        break;
                    default:
                        textRes = R.string.trip_create_failed;
                        break;
                }
                AppSnackbar.show(requireContext(), getString(textRes));
                viewModel.clearTripActionMessage();
            }
        });

        viewModel.getFriendActionMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                String toastMessage = message;
                switch (message) {
                    case "__MSG_USER_NOT_FOUND__":
                        toastMessage = getString(R.string.user_not_found);
                        break;
                    case "__MSG_FRIEND_REQUEST_SENT__":
                        toastMessage = getString(R.string.friend_request_sent);
                        break;
                    case "__MSG_FRIEND_REQUEST_SELF__":
                        toastMessage = getString(R.string.friend_request_self_not_allowed);
                        break;
                    case "__MSG_FRIEND_REQUEST_PENDING__":
                        toastMessage = getString(R.string.friend_request_pending_exists);
                        break;
                    case "__MSG_FRIEND_REQUEST_ALREADY_FRIENDS__":
                        toastMessage = getString(R.string.friend_request_already_friends);
                        break;
                    case "__MSG_FRIEND_REQUEST_SEND_FAILED__":
                        toastMessage = getString(R.string.friend_request_send_failed);
                        break;
                    case "__MSG_FRIEND_REQUEST_REQUIRES_ONLINE__":
                        toastMessage = getString(R.string.friend_request_requires_online);
                        break;
                    case "__MSG_FRIEND_REQUEST_ACCEPT_SUCCESS__":
                        toastMessage = getString(R.string.friend_request_accepted);
                        break;
                    case "__MSG_FRIEND_REQUEST_REJECT_SUCCESS__":
                        toastMessage = getString(R.string.friend_request_rejected);
                        break;
                    case "__MSG_FRIEND_REQUEST_ACCEPT_FAILED__":
                        toastMessage = getString(R.string.friend_request_accept_failed);
                        viewModel.refreshRequestsOnly();
                        break;
                    case "__MSG_FRIEND_REQUEST_REJECT_FAILED__":
                        toastMessage = getString(R.string.friend_request_reject_failed);
                        viewModel.refreshRequestsOnly();
                        break;
                    case "__MSG_UNFRIEND_SUCCESS__":
                        toastMessage = getString(R.string.unfriend_success);
                        break;
                    case "__MSG_UNFRIEND_FAILED__":
                        toastMessage = getString(R.string.unfriend_failed);
                        viewModel.retryFriends();
                        break;
                    case "__MSG_UNFRIEND_REQUIRES_ONLINE__":
                        toastMessage = getString(R.string.unfriend_requires_online);
                        viewModel.retryFriends();
                        break;
                }
                AppSnackbar.show(requireContext(), toastMessage);
                viewModel.clearFriendActionMessage();
            }
        });

        viewModel.getTripActionLoading().observe(getViewLifecycleOwner(), isLoading -> {
            isActionLoading = isLoading != null && isLoading;
            updateActionLoadingUi();
        });

        viewModel.getFriendActionLoading().observe(getViewLifecycleOwner(), isLoading -> {
            isActionLoading = isLoading != null && isLoading;
            updateActionLoadingUi();
        });

        viewModel.getPendingRequests().observe(getViewLifecycleOwner(), requests ->
                friendsAdapter.setPendingRequests(requests != null ? requests : new ArrayList<>()));

        viewModel.getTripUiState().observe(getViewLifecycleOwner(), state -> {
            if (tabLayout.getSelectedTabPosition() == 0) {
                renderTripState(state);
            }
        });

        viewModel.getFriendUiState().observe(getViewLifecycleOwner(), state -> {
            if (tabLayout.getSelectedTabPosition() == 1) {
                renderFriendState(state);
            }
        });
    }

    private void setupTabs() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                renderCurrentTabState();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        renderCurrentTabState();
    }

    private void renderCurrentTabState() {
        if (tabLayout.getSelectedTabPosition() == 0) {
            renderTripState(viewModel.getTripUiState().getValue());
        } else {
            renderFriendState(viewModel.getFriendUiState().getValue());
        }
    }

    private void renderTripState(UiState<List<TripPlan>> state) {
        if (state == null || state instanceof UiState.Loading) {
            showLoading();
            return;
        }
        stopRefreshing();
        if (state instanceof UiState.Empty) {
            tripListAdapter.setTrips(new ArrayList<>());
            showList(tripListAdapter);
            return;
        }
        if (state instanceof UiState.Error) {
            UiState.Error<List<TripPlan>> error = (UiState.Error<List<TripPlan>>) state;
            showState(error.getMessage());
            return;
        }

        UiState.Success<List<TripPlan>> success = (UiState.Success<List<TripPlan>>) state;
        tripListAdapter.setTrips(success.getData());
        showList(tripListAdapter);
    }

    private void renderFriendState(UiState<List<Friend>> state) {
        if (state == null || state instanceof UiState.Loading) {
            showLoading();
            return;
        }
        stopRefreshing();
        if (state instanceof UiState.Empty) {
            friendsAdapter.setFriends(new ArrayList<>());
            showList(friendsAdapter);
            return;
        }
        if (state instanceof UiState.Error) {
            UiState.Error<List<Friend>> error = (UiState.Error<List<Friend>>) state;
            showState(error.getMessage());
            return;
        }

        UiState.Success<List<Friend>> success = (UiState.Success<List<Friend>>) state;
        friendsAdapter.setFriends(success.getData());
        showList(friendsAdapter);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showLoading() {
        recyclerView.setVisibility(View.GONE);
        stateLayout.setVisibility(View.GONE);
        progressLoading.setVisibility(View.VISIBLE);
        recyclerView.setOnTouchListener(null);
        recyclerView.setAlpha(1f);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showState(String message) {
        recyclerView.setVisibility(View.GONE);
        progressLoading.setVisibility(View.GONE);
        stateLayout.setVisibility(View.VISIBLE);
        tvStateMessage.setText(message);
        btnRetry.setVisibility(View.VISIBLE);
        recyclerView.setOnTouchListener(null);
        recyclerView.setAlpha(1f);
    }

    private void showList(RecyclerView.Adapter<?> adapter) {
        progressLoading.setVisibility(View.GONE);
        stateLayout.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        recyclerView.setAdapter(adapter);
        updateActionLoadingUi();
    }

    private void refreshCurrentTab() {
        if (tabLayout == null || viewModel == null) {
            stopRefreshing();
            return;
        }
        if (tabLayout.getSelectedTabPosition() == 0) {
            viewModel.retryTrips();
        } else {
            viewModel.retryFriends();
            viewModel.refreshRequestsOnly();
        }
        renderCurrentTabState();
        swipeRefreshLayout.postDelayed(this::stopRefreshing, 1000L);
    }

    private void stopRefreshing() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void updateActionLoadingUi() {
        boolean tabActive = tabLayout != null
                && (tabLayout.getSelectedTabPosition() == 0 || tabLayout.getSelectedTabPosition() == 1);
        boolean canOverlay = tabActive && recyclerView.getVisibility() == View.VISIBLE;

        if (canOverlay && isActionLoading) {
            progressLoading.setVisibility(View.VISIBLE);
            recyclerView.setAlpha(0.5f);
            recyclerView.setOnTouchListener((v, event) -> true);
        } else {
            if (canOverlay) {
                progressLoading.setVisibility(View.GONE);
            }
            recyclerView.setAlpha(1f);
            recyclerView.setOnTouchListener(null);
        }
    }

    private void showAddFriendDialog() {
        DialogUtils.showCustomInputDialog(
                requireContext(),
                R.layout.dialog_add_friend,
                R.id.btn_add_friend,
                R.id.et_search,
                R.id.btn_close,
                inputText -> {
                    if (!inputText.isEmpty()) {
                        viewModel.addFriend(inputText);
                    } else {
                        AppSnackbar.show(requireContext(), "Please enter a name");
                    }
                }
        );
    }

    private void showCreateTripDialog() {
        DialogUtils.showCustomViewDialog(
                requireContext(),
                R.layout.dialog_create_trip,
                R.id.btn_close,
                (dialogView, dialog) -> {
                    TextView tvDialogTitle = dialogView.findViewById(R.id.tv_dialog_title);
                    EditText etTitle = dialogView.findViewById(R.id.et_trip_title);
                    EditText etDescription = dialogView.findViewById(R.id.et_trip_description);
                    TextView tvStartDate = dialogView.findViewById(R.id.tv_start_date);
                    TextView tvEndDate = dialogView.findViewById(R.id.tv_end_date);
                    Button btnCreate = dialogView.findViewById(R.id.btn_create_trip);

                    final long[] startMillis = {0L};
                    final long[] endMillis = {0L};

                    tvStartDate.setOnClickListener(v -> {
                        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                                .setTitleText(R.string.start_date)
                                .build();
                        picker.addOnPositiveButtonClickListener(selection -> {
                            startMillis[0] = selection != null ? selection : 0L;
                            tvStartDate.setText(formatDate(startMillis[0]));
                        });
                        picker.show(getParentFragmentManager(), "start_date_picker");
                    });

                    tvEndDate.setOnClickListener(v -> {
                        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                                .setTitleText(R.string.end_date)
                                .build();
                        picker.addOnPositiveButtonClickListener(selection -> {
                            endMillis[0] = selection != null ? selection : 0L;
                            tvEndDate.setText(formatDate(endMillis[0]));
                        });
                        picker.show(getParentFragmentManager(), "end_date_picker");
                    });

                    btnCreate.setOnClickListener(v -> {
                        String title = etTitle.getText().toString().trim();
                        String description = etDescription.getText().toString().trim();

                        if (title.isEmpty()) {
                            AppSnackbar.show(requireContext(), R.string.trip_title_required);
                            return;
                        }
                        if (startMillis[0] == 0L || endMillis[0] == 0L) {
                            AppSnackbar.show(requireContext(), R.string.trip_dates_required);
                            return;
                        }
                        if (endMillis[0] < startMillis[0]) {
                            AppSnackbar.show(requireContext(), R.string.trip_dates_invalid);
                            return;
                        }

                        viewModel.createTrip(title, description, startMillis[0], endMillis[0]);
                        dialog.dismiss();
                    });
                }
        );
    }

    private void showTripOptionsMenu(TripPlan trip, View anchorView) {
        if (trip == null || anchorView == null) {
            return;
        }

        PopupMenu popupMenu = new PopupMenu(
                requireContext(),
                anchorView,
                Gravity.NO_GRAVITY,
                0,
                com.bif.app.core.R.style.Widget_BIFLocator_PopupMenu
        );
        popupMenu.getMenuInflater().inflate(R.menu.menu_trip_options, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(item -> onTripOptionSelected(item, trip));
        popupMenu.show();
    }

    private boolean onTripOptionSelected(MenuItem item, TripPlan trip) {
        if (item.getItemId() == R.id.action_edit_trip) {
            showEditTripDialog(trip);
            return true;
        }
        if (item.getItemId() == R.id.action_delete_trip) {
            showDeleteTripDialog(trip);
            return true;
        }
        return false;
    }

    private void showEditTripDialog(TripPlan trip) {
        if (trip == null) {
            return;
        }

        DialogUtils.showCustomViewDialog(
                requireContext(),
                R.layout.dialog_create_trip,
                R.id.btn_close,
                (dialogView, dialog) -> {
                    TextView tvDialogTitle = dialogView.findViewById(R.id.tv_dialog_title);
                    EditText etTitle = dialogView.findViewById(R.id.et_trip_title);
                    EditText etDescription = dialogView.findViewById(R.id.et_trip_description);
                    TextView tvStartDate = dialogView.findViewById(R.id.tv_start_date);
                    TextView tvEndDate = dialogView.findViewById(R.id.tv_end_date);
                    Button btnCreate = dialogView.findViewById(R.id.btn_create_trip);

                    final long[] startMillis = {trip.getStartAt()};
                    final long[] endMillis = {trip.getEndAt()};

                    tvDialogTitle.setText(R.string.edit);
                    etTitle.setText(trip.getTitle() == null ? "" : trip.getTitle());
                    etDescription.setText(trip.getDescription() == null ? "" : trip.getDescription());
                    tvStartDate.setText(formatDate(startMillis[0]));
                    tvEndDate.setText(formatDate(endMillis[0]));
                    btnCreate.setText(R.string.save);

                    tvStartDate.setOnClickListener(v -> {
                        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                                .setTitleText(R.string.start_date)
                                .build();
                        picker.addOnPositiveButtonClickListener(selection -> {
                            startMillis[0] = selection != null ? selection : 0L;
                            tvStartDate.setText(formatDate(startMillis[0]));
                        });
                        picker.show(getParentFragmentManager(), "edit_start_date_picker");
                    });

                    tvEndDate.setOnClickListener(v -> {
                        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                                .setTitleText(R.string.end_date)
                                .build();
                        picker.addOnPositiveButtonClickListener(selection -> {
                            endMillis[0] = selection != null ? selection : 0L;
                            tvEndDate.setText(formatDate(endMillis[0]));
                        });
                        picker.show(getParentFragmentManager(), "edit_end_date_picker");
                    });

                    btnCreate.setOnClickListener(v -> {
                        String title = etTitle.getText().toString().trim();
                        String description = etDescription.getText().toString().trim();

                        if (title.isEmpty()) {
                            AppSnackbar.show(requireContext(), R.string.trip_title_required);
                            return;
                        }
                        if (startMillis[0] == 0L || endMillis[0] == 0L) {
                            AppSnackbar.show(requireContext(), R.string.trip_dates_required);
                            return;
                        }
                        if (endMillis[0] < startMillis[0]) {
                            AppSnackbar.show(requireContext(), R.string.trip_dates_invalid);
                            return;
                        }

                        viewModel.updateTrip(trip.getId(), title, description, startMillis[0], endMillis[0]);
                        dialog.dismiss();
                    });
                }
        );
    }

    private void showDeleteTripDialog(TripPlan trip) {
        if (trip == null) {
            return;
        }

        String tripTitle = trip.getTitle() == null || trip.getTitle().trim().isEmpty()
                ? getString(R.string.trip_title_hint)
                : trip.getTitle().trim();

        if (!isCurrentUserTripOwner(trip)) {
            AppSnackbar.show(requireContext(), R.string.trip_delete_owner_only);
            return;
        }

        DialogUtils.showConfirmDialog(
                requireContext(),
                getString(R.string.delete),
            getString(R.string.trip_delete_confirm, tripTitle),
                getString(R.string.delete),
                getString(R.string.cancel),
                () -> viewModel.deleteTrip(trip.getId())
        );
    }

    private boolean isCurrentUserTripOwner(TripPlan trip) {
        if (trip == null || trip.getParticipantIds() == null || trip.getParticipantIds().isEmpty()) {
            return false;
        }

        String ownerId = trip.getParticipantIds().get(0);
        if (ownerId == null || ownerId.trim().isEmpty()) {
            return false;
        }

        String currentUserId = UserPreferences.getId(requireContext());
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            currentUserId = UserPreferences.getUsername(requireContext());
        }
        return currentUserId != null && ownerId.trim().equals(currentUserId.trim());
    }

    private String formatDate(long millis) {
        if (millis <= 0L) {
            return "";
        }
        return new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new Date(millis));
    }

    private void navigateToChatFromFriend(Friend friend) {
        Uri destUri = UriUtils.buildUri(UriUtils.PathTo.SOCIAL_CHAT).buildUpon()
                .appendQueryParameter("chatType", "friend")
                .appendQueryParameter("chatId", String.valueOf(friend.getId()))
                .appendQueryParameter("chatName", friend.getName())
                .appendQueryParameter("avatarLetter", friend.getAvatarLetter())
                .appendQueryParameter("avatarColor", String.valueOf(friend.getAvatarColor()))
                .appendQueryParameter("memberCount", "0")
                .appendQueryParameter("friendshipCreatedAt", String.valueOf(friend.getFriendshipCreatedAt()))
                .build();
        Navigation.findNavController(requireView()).navigate(destUri);
    }

    private void navigateToTripDetail(TripPlan trip) {
        if (trip == null || trip.getId() == null || trip.getId().trim().isEmpty()) {
            return;
        }

        Uri destUri = UriUtils.buildUri(UriUtils.PathTo.TRIP_DETAIL).buildUpon()
                .appendQueryParameter("tripId", trip.getId())
                .appendQueryParameter("tripTitle", trip.getTitle() == null ? "" : trip.getTitle())
                .build();
        Navigation.findNavController(requireView()).navigate(destUri);
    }
}
