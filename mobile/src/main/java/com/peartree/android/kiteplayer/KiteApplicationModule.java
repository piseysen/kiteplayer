package com.peartree.android.kiteplayer;

import android.app.Application;
import android.content.Context;
import android.support.annotation.Nullable;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.session.AppKeyPair;
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.peartree.android.kiteplayer.ui.FullScreenPlayerActivity;
import com.peartree.android.kiteplayer.utils.ImmutableFileLRUCache;
import com.peartree.android.kiteplayer.utils.PrefUtils;
import com.squareup.okhttp.OkHttpClient;

import java.io.File;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class KiteApplicationModule {

    private KiteApplication mApplication;
    private Context mApplicationContext;

    public KiteApplicationModule(KiteApplication application) {
        mApplication = application;
        mApplicationContext = application.getApplicationContext();
    }

    @Provides @Singleton
    Application provideApplication() {
        return mApplication;
    }

    @Provides @Singleton
    VideoCastManager provideVideoCastManager() {
        return VideoCastManager.initialize(
                mApplicationContext, mApplicationContext.getResources().getString(R.string.cast_application_id), FullScreenPlayerActivity.class, null);
    }

    @Provides @Singleton
    DropboxAPI<AndroidAuthSession> provideDropboxAPI() {

        String authToken = PrefUtils.getDropboxAuthToken(mApplication);

        AppKeyPair appKeys = new AppKeyPair(BuildConfig.dbApiKey, BuildConfig.dbApiSecret);
        AndroidAuthSession session =
                authToken != null ?
                        new AndroidAuthSession(appKeys, authToken) :
                        new AndroidAuthSession(appKeys);

        return new DropboxAPI<>(session);
    }

    @Provides
    @Singleton
    @Nullable
    ImmutableFileLRUCache provideCachedSongs() {

        File diskLRUCacheDir =
                new File(mApplicationContext.getExternalCacheDir().getPath() +
                        File.separator + "lrucache");

        if (!diskLRUCacheDir.exists())
            diskLRUCacheDir.mkdir();

        int cacheSize = Integer.parseInt(PrefUtils.getCacheSize(mApplicationContext));

        final ImmutableFileLRUCache cache =
                new ImmutableFileLRUCache(diskLRUCacheDir.getPath(), cacheSize * 1024 * 1024);

        PrefUtils.registerOnCacheSizeChangeListener(mApplicationContext,
                newValue -> cache.setSizeLimitInBytes(Integer.parseInt(newValue) * 1024 * 1024));

        try {
            return cache;
        } catch (ImmutableFileLRUCache.ImmutableFileLRUCacheException e) {
            return null;
        }
    }

    @Provides @Singleton
    OkHttpClient provideHttpClient() {
        return new OkHttpClient();
    }

}