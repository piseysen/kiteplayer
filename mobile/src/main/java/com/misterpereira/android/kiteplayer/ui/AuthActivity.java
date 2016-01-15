/*
 * Copyright (c) 2015 Rafael Pereira
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 *     https://mozilla.org/MPL/2.0/.
 */

package com.misterpereira.android.kiteplayer.ui;

import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.misterpereira.android.kiteplayer.KiteApplication;
import com.misterpereira.android.kiteplayer.R;
import com.misterpereira.android.kiteplayer.model.MusicProvider;
import com.misterpereira.android.kiteplayer.utils.DropboxHelper;
import com.misterpereira.android.kiteplayer.utils.LogHelper;
import com.misterpereira.android.kiteplayer.utils.PrefUtils;

import javax.inject.Inject;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class AuthActivity extends AppCompatActivity {

    private static final String TAG = LogHelper.makeLogTag(AuthActivity.class);

    @Inject
    DropboxAPI<AndroidAuthSession> mDBApi;

    @Inject
    MusicProvider mMusicProvider;

    private View mAuthMessage;
    private ProgressBar mAuthProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Dependency Injection
        ((KiteApplication)getApplication()).getComponent().inject(this);

        if (mDBApi.getSession() != null && mDBApi.getSession().isLinked()) {
            // Skip activity if user already logged in
            launchPlayer();
        }

        setContentView(R.layout.activity_auth);
        mAuthMessage = findViewById(R.id.auth_message_layout);

        Button mAuthButton = (Button) findViewById(R.id.auth_button);
        mAuthButton.setOnClickListener(v -> {
            mAuthMessage.setVisibility(View.GONE);
            mAuthProgress.setVisibility(View.VISIBLE);
            mDBApi.getSession()
                    .startOAuth2Authentication(AuthActivity.this);
        });

        mAuthProgress = (ProgressBar)findViewById(R.id.auth_progress);

    }

    @Override
    protected void onStart() {
        super.onStart();
        if (PrefUtils.getDropboxAuthToken(this) != null &&
                DropboxHelper.isUnlinked(mDBApi.getSession())) {

            mAuthMessage.setVisibility(View.GONE);

            AlertDialog unlinkedSessionDialog =
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.unlinked_session_title)
                            .setMessage(R.string.unlinked_session_message)
                            .setPositiveButton(R.string.unlinked_session_positive,
                                    (dialog,which) -> {
                                        mAuthMessage.setVisibility(View.GONE);
                                        mAuthProgress.setVisibility(View.VISIBLE);
                                        mDBApi.getSession()
                                                .startOAuth2Authentication(AuthActivity.this);
                                    })
                            .setNegativeButton(R.string.unlinked_session_negative,
                                    (dialog,which) -> {
                                        dialog.dismiss();
                                        mAuthMessage.setVisibility(View.VISIBLE);
                                    })
                            .create();

            unlinkedSessionDialog.show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mDBApi.getSession().authenticationSuccessful()) {

            mDBApi.getSession().finishAuthentication();

            Observable
                    .create(subscriber -> {
                        try {
                            // Required to complete auth, sets the access token on the session

                            long lastUserID = PrefUtils.getDropboxUserId(this);
                            long newUserID = mDBApi.accountInfo().uid;

                            if (newUserID != lastUserID) {
                                mMusicProvider.deleteAll();
                                PrefUtils.setDropboxDeltaCursor(this, null);
                            }

                            PrefUtils.setDropboxUserId(this, newUserID);

                            String accessToken = mDBApi.getSession().getOAuth2AccessToken();
                            PrefUtils.setDropboxAuthToken(this, accessToken);

                            subscriber.onCompleted();

                        } catch (DropboxException | IllegalStateException e) {
                            subscriber.onError(e);
                        }
                    })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            nothing -> {},
                            error -> LogHelper.i("DbAuthLog", error, "Error authenticating"),
                            AuthActivity.this::launchPlayer);

        } else {
            mAuthProgress.setVisibility(View.GONE);
            mAuthMessage.setVisibility(View.VISIBLE);
        }
    }

    private void launchPlayer() {
        Intent i = new Intent(this,MusicPlayerActivity.class);
        Bundle extras = ActivityOptions
                .makeCustomAnimation(this, R.anim.fade_in, R.anim.fade_out)
                .toBundle();
        startActivity(i,extras);
        finish();
    }
}
