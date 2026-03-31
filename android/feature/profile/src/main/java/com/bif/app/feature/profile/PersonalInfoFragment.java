package com.bif.app.feature.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.bif.app.domain.repository.IProfileRepository;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class PersonalInfoFragment extends Fragment {

    @Inject
    IProfileRepository profileRepository;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_personal_info, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        NavController navController = Navigation.findNavController(view);

        Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> navController.popBackStack());

        bindLocalProfileState(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) {
            bindLocalProfileState(getView());
            syncProfileMetadataFromServer();
        }
    }

    private void bindLocalProfileState(@NonNull View view) {
        IProfileRepository.LocalProfile localProfile =
            profileRepository.readLocalProfile();
        boolean isLoggedIn = localProfile.isLoggedIn;
        String username = getStoredValue(localProfile.username);
        String email = getStoredValue(localProfile.email);

        TextView tvAuthStatusValue = view.findViewById(R.id.tvAuthStatusValue);
        TextView tvUsernameValue = view.findViewById(R.id.tvUsernameValue);
        TextView tvEmailValue = view.findViewById(R.id.tvEmailValue);

        tvAuthStatusValue.setText(isLoggedIn ? R.string.logged_in_status : R.string.guest_status);
        tvUsernameValue.setText(username);
        tvEmailValue.setText(email);
    }

    private void syncProfileMetadataFromServer() {
        if (!isAdded()) {
            return;
        }

        profileRepository.syncProfileMetadata(new IProfileRepository.ProfileCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) {
                    return;
                }

                // Repository callbacks can come from background thread.
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) {
                        return;
                    }
                    View currentView = getView();
                    if (currentView != null) {
                        bindLocalProfileState(currentView);
                    }
                });
            }

            @Override
            public void onFailure() {
                // Keep rendering cached profile values when sync fails.
            }
        });
    }

    private String getStoredValue(String value) {
        if (value == null) {
            return getString(R.string.not_available);
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? getString(R.string.not_available) : trimmed;
    }
}
