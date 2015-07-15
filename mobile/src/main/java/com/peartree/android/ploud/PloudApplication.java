package com.peartree.android.ploud;

import android.app.Application;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.session.AppKeyPair;
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.peartree.android.ploud.utils.LogHelper;

import javax.inject.Inject;

import dagger.Lazy;

import static com.google.android.libraries.cast.companionlibrary.cast.BaseCastManager.FEATURE_DEBUGGING;
import static com.google.android.libraries.cast.companionlibrary.cast.BaseCastManager.FEATURE_WIFI_RECONNECT;


public class PloudApplication extends Application {

    private static final String TAG = LogHelper.makeLogTag(PloudApplication.class);

    protected PloudApplicationComponent mApplicationComponent;

    @Inject
    Lazy<VideoCastManager> mCastManager;

    @Override
    public void onCreate() {
        super.onCreate();

        mApplicationComponent = DaggerPloudApplicationComponent.builder()
                .ploudApplicationModule(new PloudApplicationModule(this))
                .build();

        mApplicationComponent.inject(this);

        // String applicationId = getResources().getString(R.string.cast_application_id);
        // VideoCastManager castManager = VideoCastManager.initialize(
        //        getApplicationContext(), applicationId, FullScreenPlayerActivity.class, null);
        mCastManager.get().enableFeatures(FEATURE_WIFI_RECONNECT | FEATURE_DEBUGGING);

    }

    public PloudApplicationComponent getComponent() { return this.mApplicationComponent; }
}
