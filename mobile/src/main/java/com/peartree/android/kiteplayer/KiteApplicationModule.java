/*
 * Copyright (c) 2015 Rafael Pereira
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 *
 *      https://mozilla.org/MPL/2.0/
 *
 */

package com.peartree.android.kiteplayer;

import android.app.Application;
import android.content.Context;
import android.support.annotation.Nullable;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.session.AppKeyPair;
import com.peartree.android.kiteplayer.utils.ImmutableFileLRUCache;
import com.peartree.android.kiteplayer.utils.PrefUtils;
import com.squareup.okhttp.OkHttpClient;

import java.io.File;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class KiteApplicationModule {

    private final KiteApplication mApplication;
    private final Context mApplicationContext;

    public KiteApplicationModule(KiteApplication application) {
        mApplication = application;
        mApplicationContext = application.getApplicationContext();
    }

    @Provides @Singleton
    Application provideApplication() {
        return mApplication;
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

        File diskLRUCacheDir = mApplicationContext.getExternalCacheDir();
        if (diskLRUCacheDir == null) {
            diskLRUCacheDir = mApplicationContext.getCacheDir();
        }

        diskLRUCacheDir = new File(diskLRUCacheDir.getPath() + File.separator + "lrucache");

        if (!diskLRUCacheDir.exists())
            diskLRUCacheDir.mkdir();

        // Allocates 90% of user defined cache size to songs
        int songCacheSizeInMBs =
                Math.round(Integer.parseInt(PrefUtils.getCacheSize(mApplicationContext)) * 0.9f);

        final ImmutableFileLRUCache cache =
                new ImmutableFileLRUCache(diskLRUCacheDir.getPath(), songCacheSizeInMBs * 1024 * 1024);

        PrefUtils.registerOnCacheSizeChangeListener(mApplicationContext,
                newValue -> {
                    // Allocates 90% of user defined cache size to songs
                    int newSongCacheSizeInMBs =
                            Math.round(Integer.parseInt(newValue) * 0.9f);
                    cache.setSizeLimitInBytes(newSongCacheSizeInMBs * 1024 * 1024);
                });

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