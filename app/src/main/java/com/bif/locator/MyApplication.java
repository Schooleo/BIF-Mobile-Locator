package com.bif.locator;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapsSdkInitializedCallback;
import com.google.android.libraries.places.api.Places;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class MyApplication extends Application implements OnMapsSdkInitializedCallback {

    @Override
    public void onCreate() {
        super.onCreate();
        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(this, getPlacesApiKey());
        }

        // Initialize the Maps SDK globally
        MapsInitializer.initialize(getApplicationContext(), MapsInitializer.Renderer.LATEST, this);
    }

    @Override
    public void onMapsSdkInitialized(@NonNull MapsInitializer.Renderer renderer) {
        if (renderer == MapsInitializer.Renderer.LATEST) {
            Log.d("MapsDemo", "The latest version of the renderer is used.");
        }
    }

    private String getPlacesApiKey(){
        try {
            ApplicationInfo appInfo = getPackageManager()
                    .getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            return appInfo.metaData.getString("com.google.android.places.API_KEY");
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}