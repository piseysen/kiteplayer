package com.peartree.android.kiteplayer;

import android.app.Application;

import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.peartree.android.kiteplayer.utils.LogHelper;

import javax.inject.Inject;

import dagger.Lazy;

import static com.google.android.libraries.cast.companionlibrary.cast.BaseCastManager.FEATURE_DEBUGGING;
import static com.google.android.libraries.cast.companionlibrary.cast.BaseCastManager.FEATURE_WIFI_RECONNECT;


public class KiteApplication extends Application {

    private static final String TAG = LogHelper.makeLogTag(KiteApplication.class);

    protected KiteApplicationComponent mApplicationComponent;

    @Inject
    Lazy<VideoCastManager> mCastManager;

    @Override
    public void onCreate() {
        super.onCreate();

        mApplicationComponent = DaggerKiteApplicationComponent.builder()
                .kiteApplicationModule(new KiteApplicationModule(this))
                .build();

        mApplicationComponent.inject(this);

        // String applicationId = getResources().getString(R.string.cast_application_id);
        // VideoCastManager castManager = VideoCastManager.initialize(
        //        getApplicationContext(), applicationId, FullScreenPlayerActivity.class, null);
        mCastManager.get().enableFeatures(FEATURE_WIFI_RECONNECT | FEATURE_DEBUGGING);

    }

    public KiteApplicationComponent getComponent() { return this.mApplicationComponent; }
}
