/*
 * Original work Copyright (C) 2014 The Android Open Source Project
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
 *
 * Modified work Copyright (c) 2015 Rafael Pereira
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 *     https://mozilla.org/MPL/2.0/
 */
package com.misterpereira.android.kiteplayer.ui;

import android.app.Activity;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.misterpereira.android.kiteplayer.KiteApplication;
import com.misterpereira.android.kiteplayer.R;
import com.misterpereira.android.kiteplayer.model.MusicProvider;
import com.misterpereira.android.kiteplayer.utils.DropboxHelper;
import com.misterpereira.android.kiteplayer.utils.LogHelper;
import com.misterpereira.android.kiteplayer.utils.MediaIDHelper;
import com.misterpereira.android.kiteplayer.utils.NetworkHelper;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * A Fragment that lists all the various browsable queues available
 * from a {@link android.service.media.MediaBrowserService}.
 * <p/>
 * It uses a {@link MediaBrowser} to connect to the {@link com.misterpereira.android.kiteplayer.MusicService}.
 * Once connected, the fragment subscribes to get all the children.
 * All {@link MediaBrowser.MediaItem}'s that can be browsed are shown in a ListView.
 */
public class MediaBrowserFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {

    private static final String TAG = LogHelper.makeLogTag(MediaBrowserFragment.class);

    private static final String ARG_MEDIA_ID = "media_id";

    private BrowseAdapter mBrowserAdapter;
    private String mMediaId;
    private MediaFragmentListener mMediaFragmentListener;

    private View mNoSongsMessage;
    private SwipeRefreshLayout mSwipeLayout;

    @Inject
    DropboxAPI<AndroidAuthSession> mDBApi;

    @Inject
    MusicProvider mProvider;

    private final BroadcastReceiver mConnectivityChangeReceiver = new BroadcastReceiver() {
        private int oldOnline = -1;
        @Override
        public void onReceive(Context context, Intent intent) {

            int isOnline = NetworkHelper.isOnline(context)?1:0;
            if (isOnline != oldOnline) {
                oldOnline = isOnline;
                mMediaFragmentListener.checkForUserVisibleErrors(false);
                if (isOnline == 1) {
                    mBrowserAdapter.notifyDataSetChanged();
                }
            }

        }
    };

    // Receive callbacks from the MediaController. Here we update our state such as which queue
    // is being shown, the current title and description and the PlaybackState.
    private final MediaController.Callback mMediaControllerCallback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            super.onMetadataChanged(metadata);
            if (metadata == null) {
                return;
            }
            LogHelper.d(TAG, "Received metadata change to media ",
                    metadata.getDescription().getMediaId());
            mBrowserAdapter.notifyDataSetChanged();
        }

        @Override
        public void onPlaybackStateChanged(@NonNull PlaybackState state) {
            super.onPlaybackStateChanged(state);
            LogHelper.d(TAG, "Received state change: ", state);

            mMediaFragmentListener.checkForUserVisibleErrors(false);
            mBrowserAdapter.notifyDataSetChanged();
        }
    };

    private final MediaBrowser.SubscriptionCallback mSubscriptionCallback =
        new MediaBrowser.SubscriptionCallback() {
            @Override
            public void onChildrenLoaded(@NonNull String parentId,
                                         @NonNull List<MediaBrowser.MediaItem> children) {
                try {
                    LogHelper.d(TAG, "fragment onChildrenLoaded, parentId=", parentId,
                            "  count=", children.size());

                    if (children.isEmpty() && DropboxHelper.isUnlinked(mDBApi.getSession())) {
                        mMediaFragmentListener.onDropboxSessionUnlinked();
                    } else {
                        mBrowserAdapter.clear();
                        mBrowserAdapter.addAll(children);
                        mBrowserAdapter.notifyDataSetChanged();

                        mNoSongsMessage.setVisibility(children.isEmpty()?View.VISIBLE:View.GONE);
                    }
                    mMediaFragmentListener.checkForUserVisibleErrors(false);

                } catch (Throwable t) {
                    LogHelper.e(TAG, t, "Error on childrenloaded");
                    mMediaFragmentListener.checkForUserVisibleErrors(true);
                } finally {
                    mSwipeLayout.setRefreshing(false);
                }
            }

            @Override
            public void onError(@NonNull String id) {
                LogHelper.e(TAG, "browse fragment subscription onError, id=", id);

                mSwipeLayout.setRefreshing(false);
                mMediaFragmentListener.checkForUserVisibleErrors(true);
            }
        };

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // If used on an activity that doesn't implement MediaFragmentListener, it
        // will throw an exception as expected:
        mMediaFragmentListener = (MediaFragmentListener) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        LogHelper.d(TAG, "fragment.onCreateView");

        ((KiteApplication)getActivity().getApplication()).getComponent().inject(this);

        View rootView = inflater.inflate(R.layout.fragment_list, container, false);

        mSwipeLayout = (SwipeRefreshLayout) rootView.findViewById(R.id.swipe_refresh_layout);

        mSwipeLayout.setOnRefreshListener(this);
        mSwipeLayout.setColorSchemeResources(R.color.app_accent);
        mSwipeLayout.post(() -> mSwipeLayout.setRefreshing(true));

        mNoSongsMessage = rootView.findViewById(R.id.nosong_message_layout);
        Button mNoSongsRefreshButton = (Button) rootView.findViewById(R.id.nosong_refresh_button);
        mNoSongsRefreshButton.setOnClickListener(view -> {
            mSwipeLayout.setRefreshing(true);
            onRefresh();
        });

        mBrowserAdapter = new BrowseAdapter(getActivity());

        ListView listView = (ListView) rootView.findViewById(R.id.list_view);
        listView.setAdapter(mBrowserAdapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            mMediaFragmentListener.checkForUserVisibleErrors(false);
            MediaBrowser.MediaItem item = mBrowserAdapter.getItem(position);
            mMediaFragmentListener.onMediaItemSelected(item);
        });

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();

        // fetch browsing information to fill the listview:
        MediaBrowser mediaBrowser = mMediaFragmentListener.getMediaBrowser();

        LogHelper.d(TAG, "fragment.onStart, mediaId=", mMediaId,
                "  onConnected=" + mediaBrowser.isConnected());

        if (mediaBrowser.isConnected()) {
            onConnected();
        }

        // Registers BroadcastReceiver to track network connection changes.
        this.getActivity().registerReceiver(mConnectivityChangeReceiver,
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    public void onStop() {
        super.onStop();
        MediaBrowser mediaBrowser = mMediaFragmentListener.getMediaBrowser();
        if (mediaBrowser != null && mediaBrowser.isConnected() && mMediaId != null) {
            mediaBrowser.unsubscribe(mMediaId);
        }
        if (getActivity().getMediaController() != null) {
            getActivity().getMediaController().unregisterCallback(mMediaControllerCallback);
        }
        this.getActivity().unregisterReceiver(mConnectivityChangeReceiver);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mMediaFragmentListener = null;
    }

    public String getMediaId() {
        Bundle args = getArguments();
        if (args != null) {
            return args.getString(ARG_MEDIA_ID);
        }
        return null;
    }

    public void setMediaId(String mediaId) {
        Bundle args = new Bundle(1);
        args.putString(MediaBrowserFragment.ARG_MEDIA_ID, mediaId);
        setArguments(args);
    }

    // Called when the MediaBrowser is connected. This method is either called by the
    // fragment.onStart() or explicitly by the activity in the case where the connection
    // completes after the onStart()
    public void onConnected() {
        if (isDetached()) {
            return;
        }
        mMediaId = getMediaId();
        if (mMediaId == null) {
            mMediaId = mMediaFragmentListener.getMediaBrowser().getRoot();
        }
        updateTitle();

        // Unsubscribing before subscribing is required if this mediaId already has a subscriber
        // on this MediaBrowser instance. Subscribing to an already subscribed mediaId will replace
        // the callback, but won't trigger the initial callback.onChildrenLoaded.
        //
        // This is temporary: A bug is being fixed that will make subscribe
        // consistently call onChildrenLoaded initially, no matter if it is replacing an existing
        // subscriber or not. Currently this only happens if the mediaID has no previous
        // subscriber or if the media content changes on the service side, so we need to
        // unsubscribe first.
        mMediaFragmentListener.getMediaBrowser().unsubscribe(mMediaId);

        mMediaFragmentListener.getMediaBrowser().subscribe(mMediaId, mSubscriptionCallback);

        // Add MediaController callback so we can redraw the list when metadata changes:
        // Unregisters first, to ensure listener isn't added multiple times
        if (getActivity().getMediaController() != null) {
            getActivity().getMediaController().unregisterCallback(mMediaControllerCallback);
            getActivity().getMediaController().registerCallback(mMediaControllerCallback);
        }
    }

    private void updateTitle() {
        if (MediaIDHelper.MEDIA_ID_ROOT.equals(mMediaId)) {
            mMediaFragmentListener.setToolbarTitle(null);
            return;
        }

        String[] parentHierarchy = MediaIDHelper.getHierarchy(mMediaId);

        mMediaFragmentListener.setToolbarTitle(parentHierarchy[parentHierarchy.length - 1]);

    }

    @SuppressWarnings("unchecked")
    @Override
    public void onRefresh() {
        mProvider
                .refresh()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        entryId -> {
                            if (!mSwipeLayout.isRefreshing()) {
                                mSwipeLayout.setRefreshing(true);
                            }
                        }, error -> {
                            if (NetworkHelper.isOnline(getActivity()) &&
                                    DropboxHelper.isUnlinked(mDBApi.getSession())) {
                                mMediaFragmentListener.onDropboxSessionUnlinked();
                            }
                            mMediaFragmentListener.checkForUserVisibleErrors(true);
                            mSwipeLayout.setRefreshing(false);
                        }, () -> {
                            mSwipeLayout.setRefreshing(false);
                            onConnected();
                        });
    }

    // An adapter for showing the list of browsed MediaItem's
    private static class BrowseAdapter extends ArrayAdapter<MediaBrowser.MediaItem> {

        public BrowseAdapter(Activity context) {
            super(context, R.layout.media_list_item, new ArrayList<>());
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            MediaBrowser.MediaItem item = getItem(position);
            int itemState = MediaItemViewHolder.STATE_NONE;
            if (item.isPlayable()) {
                itemState = MediaItemViewHolder.STATE_PLAYABLE;
                MediaController controller = ((Activity) getContext()).getMediaController();
                if (controller != null && controller.getMetadata() != null) {
                    String currentPlaying = controller.getMetadata().getDescription().getMediaId();
                    String musicId = MediaIDHelper.extractMusicIDFromMediaID(
                            item.getDescription().getMediaId());
                    if (currentPlaying != null && currentPlaying.equals(musicId)) {
                        PlaybackState pbState = controller.getPlaybackState();
                        if (pbState == null || pbState.getState() == PlaybackState.STATE_ERROR) {
                            itemState = MediaItemViewHolder.STATE_NONE;
                        } else if (pbState.getState() == PlaybackState.STATE_PLAYING) {
                            itemState = MediaItemViewHolder.STATE_PLAYING;
                        } else {
                            itemState = MediaItemViewHolder.STATE_PAUSED;
                        }
                    }
                }
            } else if (item.isBrowsable()) {
                itemState = MediaItemViewHolder.STATE_CLOSED_FOLDER;
            }

            return MediaItemViewHolder.setupView((Activity) getContext(), convertView, parent,
                item.getDescription(), itemState);
        }

    }

    public interface MediaFragmentListener extends MediaBrowserProvider {
        void onMediaItemSelected(MediaBrowser.MediaItem item);
        void setToolbarTitle(CharSequence title);
        void checkForUserVisibleErrors(boolean forceError);
        void onDropboxSessionUnlinked();
    }
}
