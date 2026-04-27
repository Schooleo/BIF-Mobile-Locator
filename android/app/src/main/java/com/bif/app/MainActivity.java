package com.bif.app;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        assert navHostFragment != null;

        // Prevent Bottom Nav from auto padding
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnApplyWindowInsetsListener(null);
        bottomNav.setPadding(0, 0, 0, 0);
        bottomNav.setItemActiveIndicatorEnabled(false);

        final NavController navController = navHostFragment.getNavController();
        NavigationUI.setupWithNavController(bottomNav, navController);
        bottomNav.setOnItemSelectedListener(item -> onBottomNavItemSelected(item, navController));
        bottomNav.setOnItemReselectedListener(item -> {
            if (item.getItemId() == R.id.nav_favorites) {
                navController.popBackStack(R.id.nav_favorites, false);
            }
        });

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int destId = destination.getId();
            if (destId == R.id.nav_login
                    || destId == R.id.nav_register
                    || destId == R.id.nav_social_chat
                    || destId == R.id.nav_trip_detail
                    || destId == R.id.nav_add_trip_stop
                    || destId == R.id.nav_friend_settings_locations
                    || destId == R.id.nav_friend_settings_trips
                    || destId == R.id.nav_forgot_password
                    || destId == R.id.nav_forgot_password_otp
                    || destId == R.id.nav_reset_password) {
                bottomNav.setVisibility(View.GONE);
            } else {
                bottomNav.setVisibility(View.VISIBLE);
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private boolean onBottomNavItemSelected(@NonNull MenuItem item,
                                            @NonNull NavController navController) {
        int targetId = item.getItemId();

        if (targetId == R.id.nav_favorites && isCurrentDestination(navController, R.id.nav_favorite_detail)) {
            boolean popped = navController.popBackStack(R.id.nav_favorites, false);
            if (!popped) {
                navController.navigate(R.id.nav_favorites);
            }
            return true;
        }

        if (targetId != R.id.nav_favorites && isCurrentDestination(navController, R.id.nav_favorite_detail)) {
            navController.popBackStack(R.id.nav_favorites, false);
        }

        return NavigationUI.onNavDestinationSelected(item, navController);
    }

    private boolean isCurrentDestination(@NonNull NavController navController, int destinationId) {
        NavDestination current = navController.getCurrentDestination();
        return current != null && current.getId() == destinationId;
    }


}
