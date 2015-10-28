/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.peartree.android.kiteplayer.ui;

import android.app.ActivityOptions;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.SearchManager;
import android.content.Intent;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatDialog;
import android.text.TextUtils;

import com.peartree.android.kiteplayer.R;
import com.peartree.android.kiteplayer.utils.LogHelper;
import com.peartree.android.kiteplayer.utils.MediaIDHelper;
import com.peartree.android.kiteplayer.utils.NetworkHelper;
import com.peartree.android.kiteplayer.utils.PrefUtils;

import java.util.List;
import java.util.concurrent.Callable;


/**
 * Main activity for the music player.
 * This class hold the MediaBrowser and the MediaController instances. It will create a MediaBrowser
 * when it is created and connect/disconnect on start/stop. Thus, a MediaBrowser will be always
 * connected while this activity is running.
 */
public class MusicPlayerActivity extends BaseActivity
        implements MediaBrowserFragment.MediaFragmentListener {

    private static final String TAG = LogHelper.makeLogTag(MusicPlayerActivity.class);
    private static final String SAVED_MEDIA_ID="com.peartree.android.kiteplayer.MEDIA_ID";
    private static final String FRAGMENT_TAG = "kite_list_container";

    private enum ErrorType {
        NO_CONNECTION, PLAYER_ERROR, NO_STREAMING, OTHER
    }

    public static final String EXTRA_START_FULLSCREEN =
            "com.peartree.android.kiteplayer.EXTRA_START_FULLSCREEN";

    /**
     * Optionally used with {@link #EXTRA_START_FULLSCREEN} to carry a MediaDescription to
     * the {@link FullScreenPlayerActivity}, speeding up the screen rendering
     * while the {@link android.media.session.MediaController} is connecting.
     */
    public static final String EXTRA_CURRENT_MEDIA_DESCRIPTION =
        "com.peartree.android.kiteplayer.CURRENT_MEDIA_DESCRIPTION";

    private Bundle mVoiceSearchParams;
    private AppCompatDialog mProgressDialog;
    private Snackbar mSnackbarError;
    private CoordinatorLayout mCoordinatorLayout;

    private final MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override
        public void onQueueChanged(List<MediaSession.QueueItem> queue) {
            super.onQueueChanged(queue);
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
        }
    };

    @Override
    protected void onStop() {
        super.onStop();

        if (getMediaController() != null) {
            getMediaController().unregisterCallback(mediaCallback);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        LogHelper.d(TAG, "Activity onCreate");

        setVoiceSearchParamsIfNeeded(getIntent());

        setContentView(R.layout.activity_player);
        mCoordinatorLayout = (CoordinatorLayout) findViewById(R.id.coordinatorlayout);

        initializeFromParams(savedInstanceState, getIntent());
        initializeToolbar();

        // Only check if a full screen player is needed on the first time:
        if (savedInstanceState == null) {
            startFullScreenActivityIfNeeded(getIntent());
        }

    }

    @Override
    public void onPause() {
        super.onPause();
        PrefUtils.setLatestMediaId(this, getMediaId());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        String mediaId = getMediaId();
        if (mediaId != null) {
            outState.putString(SAVED_MEDIA_ID, mediaId);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onMediaItemSelected(MediaBrowser.MediaItem item) {
        LogHelper.d(TAG, "onMediaItemSelected, mediaId=", item.getMediaId());
        if (item.isPlayable()) {
            getMediaController().getTransportControls().playFromMediaId(item.getMediaId(), null);
            if (mProgressDialog != null) {
                mProgressDialog.show();
            } else {
                mProgressDialog = new AppCompatDialog(this) {
                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        setContentView(R.layout.progress_dialog);
                    }
                };
                mProgressDialog.show();
            }
        } else if (item.isBrowsable()) {
            navigateToBrowser(item.getMediaId());
        } else {
            LogHelper.w(TAG, "Ignoring MediaItem that is neither browsable nor playable: ",
                    "mediaId=", item.getMediaId());
        }
    }

    @Override
    public void setToolbarTitle(CharSequence title) {
        LogHelper.d(TAG, "Setting toolbar title to ", title);
        if (title == null) {
            title = getString(R.string.app_name);
        }
        setTitle(title);
    }

    @Override
    public void checkForUserVisibleErrors(boolean forceError) {

        MediaController controller = this.getMediaController();

        Callable<Snackbar> newSnackbar = null;
        ErrorType userVisibleError = null;

        if (!NetworkHelper.isOnline(this)) {

            // If offline, message is about the lack of connectivity

            userVisibleError = ErrorType.NO_CONNECTION;
            newSnackbar = () -> Snackbar.make(
                    mCoordinatorLayout,
                    getText(R.string.error_no_connection),
                    Snackbar.LENGTH_INDEFINITE);

        } else if (controller != null
                && controller.getMetadata() != null
                && controller.getPlaybackState() != null
                && controller.getPlaybackState().getState() == PlaybackState.STATE_ERROR
                && controller.getPlaybackState().getErrorMessage() != null) {

            // If state is ERROR and metadata!=null, use playback state error message

            userVisibleError = ErrorType.PLAYER_ERROR;
            newSnackbar = () -> Snackbar.make(
                    mCoordinatorLayout,
                    controller.getPlaybackState().getErrorMessage(),
                    Snackbar.LENGTH_LONG);


        } else if (forceError) {

            // If the caller requested to show error, show a generic message
            userVisibleError = ErrorType.OTHER;
            newSnackbar = () -> Snackbar.make(
                    mCoordinatorLayout,
                    getText(R.string.error_loading_media),
                    Snackbar.LENGTH_LONG);

        } else if (!NetworkHelper.canStream(this)) {

            // If unable to stream, message is about settings
            userVisibleError = ErrorType.NO_STREAMING;
            newSnackbar = () -> {

                Snackbar sb = Snackbar.make(
                        mCoordinatorLayout,
                        getText(R.string.error_no_streaming),
                        Snackbar.LENGTH_INDEFINITE);

                sb.setAction(R.string.settings, v -> {
                    Bundle extras = ActivityOptions
                            .makeCustomAnimation(this, R.anim.fade_in, R.anim.fade_out)
                            .toBundle();

                    startActivity(new Intent(this, SettingsActivity.class), extras);
                    finish();

                });

                return sb;
            };
        }

        // Dismiss current error message if no longer needed or replaced with different error
        if (mSnackbarError != null &&
                (userVisibleError == null ||
                        mSnackbarError.getView().getTag() != userVisibleError)) {
            mSnackbarError.dismiss();
            mSnackbarError = null;
        }

        // Display new error
        if (userVisibleError != null && newSnackbar != null && mSnackbarError == null) {
            try {
                mSnackbarError = newSnackbar.call();
                mSnackbarError.getView().setTag(userVisibleError);
                mSnackbarError.show();
            } catch (Exception e) { // Should never happen
                LogHelper.e(TAG,e,"checkForUserVisibleErrors - Failed to make snackbar.");
            }
        }

        LogHelper.d(TAG, "checkForUserVisibleErrors. forceError=", forceError,
                " userVisibleError=", userVisibleError);
    }

    @Override
    public void onDropboxSessionUnlinked() {
        reAuthenticate();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        LogHelper.d(TAG, "onNewIntent, intent=", intent);
        setVoiceSearchParamsIfNeeded(intent);
        startFullScreenActivityIfNeeded(intent);
    }

    private void startFullScreenActivityIfNeeded(Intent intent) {
        if (intent != null && intent.getBooleanExtra(EXTRA_START_FULLSCREEN, false)) {
            Intent fullScreenIntent = new Intent(this, FullScreenPlayerActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_CURRENT_MEDIA_DESCRIPTION,
                        (Parcelable)intent.getParcelableExtra(EXTRA_CURRENT_MEDIA_DESCRIPTION));
            startActivity(fullScreenIntent);
        }
    }

    protected void initializeFromParams(Bundle savedInstanceState, Intent intent) {

        navigateToBrowser(null);

        String savedMediaId = PrefUtils.getLatestMediaId(this);
        if (savedInstanceState != null) {
            // If there is a saved media ID, use it
            savedMediaId = savedInstanceState.getString(SAVED_MEDIA_ID);
        }

        if (savedMediaId != null) {
            navigateToBrowser(savedMediaId);
        }

        getFragmentManager().executePendingTransactions();
    }

    private void setVoiceSearchParamsIfNeeded(Intent intent) {
        // check if we were started from a "Play XYZ" voice search. If so, we save the extras
        // (which contain the query details) in a parameter, so we can reuse it later, when the
        // MediaSession is connected.
        if (intent.getAction() != null
            && intent.getAction().equals(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)) {
            mVoiceSearchParams = intent.getExtras();
            LogHelper.d(TAG, "Starting from voice search query=",
                    mVoiceSearchParams.getString(SearchManager.QUERY));
        }
    }

    @Override
    public void onBackPressed() {

        String mediaId = getMediaId();
        String parentMediaId =
                mediaId != null?
                MediaIDHelper.getParentMediaID(mediaId):null;

        FragmentManager fragmentManager = getFragmentManager();

        // If a fragment is the last in the back stack, but the hierarchical media ID indicates that
        // its parent isn't the root, then a new fragment needs to be created to give the user the
        // perception that he/she is navigating up the media hierarchy.
        if (fragmentManager.getBackStackEntryCount() == 1 &&
                parentMediaId != null &&
                !parentMediaId.equals(MediaIDHelper.MEDIA_ID_ROOT)) {
            fragmentManager.popBackStack();
            navigateToBrowser(parentMediaId);
        } else {
            super.onBackPressed();
        }
    }

    private void navigateToBrowser(String mediaId) {
        LogHelper.d(TAG, "navigateToBrowser, mediaId=", mediaId);
        MediaBrowserFragment fragment = getBrowseFragment();

        if (fragment == null || !TextUtils.equals(fragment.getMediaId(), mediaId)) {
            fragment = new MediaBrowserFragment();
            fragment.setMediaId(mediaId);
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.setCustomAnimations(
                R.animator.slide_in_from_right, R.animator.slide_out_to_left,
                R.animator.slide_in_from_left, R.animator.slide_out_to_right);
            transaction.replace(R.id.container, fragment, FRAGMENT_TAG);
            // If this is not the top level media (root), we add it to the fragment back stack,
            // so that actionbar toggle and Back will work appropriately:
            if (mediaId != null) {
                transaction.addToBackStack(null);
            }
            transaction.commit();
        }
    }

    public String getMediaId() {
        MediaBrowserFragment fragment = getBrowseFragment();
        if (fragment == null) {
            return null;
        }
        return fragment.getMediaId();
    }

    private MediaBrowserFragment getBrowseFragment() {
        return (MediaBrowserFragment) getFragmentManager().findFragmentByTag(FRAGMENT_TAG);
    }

    @Override
    protected void onMediaControllerConnected() {
        if (mVoiceSearchParams != null) {
            // If there is a bootstrap parameter to start from a search query, we
            // send it to the media session and set it to null, so it won't play again
            // when the activity is stopped/started or recreated:
            String query = mVoiceSearchParams.getString(SearchManager.QUERY);
            getMediaController().getTransportControls().playFromSearch(query, mVoiceSearchParams);
            mVoiceSearchParams = null;
        }
        getMediaController().registerCallback(mediaCallback);
        getBrowseFragment().onConnected();
    }
}
