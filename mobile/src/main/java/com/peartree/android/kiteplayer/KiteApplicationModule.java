package com.peartree.android.kiteplayer;

import android.app.Application;
import android.content.Context;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.session.AppKeyPair;
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.peartree.android.kiteplayer.ui.FullScreenPlayerActivity;
import com.peartree.android.kiteplayer.utils.ImmutableFileLRUCache;
import com.peartree.android.kiteplayer.utils.PrefUtils;
import com.peartree.android.kiteplayer.utils.SongCacheHelper;
import com.squareup.okhttp.OkHttpClient;

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
        AndroidAuthSession session = authToken != null?new AndroidAuthSession(appKeys,authToken):new AndroidAuthSession(appKeys);

        return new DropboxAPI<>(session);
    }

    @Provides @Singleton
    ImmutableFileLRUCache provideCachedSongs() {

        String cacheDir = SongCacheHelper.makeLRUCacheDirectoryPath(mApplicationContext);

        try {
            // TODO Fix hardcoded values
            return new ImmutableFileLRUCache(cacheDir, 200 * 1024 * 1024);
        } catch (ImmutableFileLRUCache.ImmutableFileLRUCacheException e) {
            return null;
        }
    }

    @Provides @Singleton
    OkHttpClient provideHttpClient() {
        return new OkHttpClient();
    }

}
