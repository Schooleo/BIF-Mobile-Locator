package com.bif.app.di;

import android.app.ActivityManager;
import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.module.AppGlideModule;

@GlideModule
public class MyAppGlideModule extends AppGlideModule {

    private static final long DISK_CACHE_SIZE_BYTES = 250L * 1024L * 1024L;

    @Override
    public void applyOptions(@NonNull Context context,
                             @NonNull GlideBuilder builder) {
        builder.setDiskCache(new InternalCacheDiskCacheFactory(
                context, DISK_CACHE_SIZE_BYTES));

        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        int memoryClassMb = activityManager != null
                ? activityManager.getMemoryClass()
                : 128;
        long memoryCacheSize = Math.max(
                20L * 1024L * 1024L,
                Math.min(64L * 1024L * 1024L,
                        (memoryClassMb * 1024L * 1024L) / 8L));
        builder.setMemoryCache(new LruResourceCache(memoryCacheSize));
    }

    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}
