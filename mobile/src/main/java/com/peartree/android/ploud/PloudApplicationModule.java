package com.peartree.android.ploud;

import android.app.Application;
import android.content.Context;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.session.AppKeyPair;
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.peartree.android.ploud.ui.FullScreenPlayerActivity;
import com.peartree.android.ploud.utils.LogHelper;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class PloudApplicationModule {

    private PloudApplication mApplication;
    private Context mApplicationContext;

    public PloudApplicationModule(PloudApplication application) {
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
        // TODO Fix Dropbox initialization
        String authToken = "VrT5QL64dnkAAAAAAAGCEZZsdPzrOjNjzYbkb-c1CZxJHZ9fVNQ-KqnMVY-VOEWS"; //PrefUtils.getDropboxAuthToken(this);

        AppKeyPair appKeys = new AppKeyPair(BuildConfig.dbApiKey, BuildConfig.dbApiSecret);
        AndroidAuthSession session = authToken != null?new AndroidAuthSession(appKeys,authToken):new AndroidAuthSession(appKeys);

        return new DropboxAPI<>(session);
    }

}
