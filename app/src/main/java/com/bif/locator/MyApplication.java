package com.bif.locator;

import android.app.Application;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapsSdkInitializedCallback;
import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize the Maps SDK globally
        MapsInitializer.initialize(getApplicationContext(), MapsInitializer.Renderer.LATEST, renderer -> {
            switch (renderer) {
                case LATEST:
                    // Using the modern renderer
                    break;
                case LEGACY:
                    break;
            }
        });
    }
}