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

import com.bif.app.core.utils.UserPreferences;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class PersonalInfoFragment extends Fragment {

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

        boolean isLoggedIn = UserPreferences.isLoggedIn(requireContext());
        String username = getStoredValue(UserPreferences.getUsername(requireContext()));
        String email = getStoredValue(UserPreferences.getEmail(requireContext()));

        TextView tvAuthStatusValue = view.findViewById(R.id.tvAuthStatusValue);
        TextView tvUsernameValue = view.findViewById(R.id.tvUsernameValue);
        TextView tvEmailValue = view.findViewById(R.id.tvEmailValue);

        tvAuthStatusValue.setText(isLoggedIn ? R.string.logged_in_status : R.string.guest_status);
        tvUsernameValue.setText(username);
        tvEmailValue.setText(email);
    }

    private String getStoredValue(String value) {
        if (value == null) {
            return getString(R.string.not_available);
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? getString(R.string.not_available) : trimmed;
    }
}
