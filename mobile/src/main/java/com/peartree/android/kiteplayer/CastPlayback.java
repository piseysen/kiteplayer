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
package com.peartree.android.kiteplayer;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.StringSignature;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.common.images.WebImage;
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.google.android.libraries.cast.companionlibrary.cast.callbacks.VideoCastConsumerImpl;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.CastException;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.NoConnectionException;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.TransientNetworkDisconnectionException;
import com.peartree.android.kiteplayer.model.AlbumArtLoader;
import com.peartree.android.kiteplayer.model.MusicProvider;
import com.peartree.android.kiteplayer.utils.LogHelper;
import com.peartree.android.kiteplayer.utils.MediaIDHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.security.Provider;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import fi.iki.elonen.NanoHTTPD;
import rx.Observable;
import rx.schedulers.Schedulers;

import static android.media.session.MediaSession.QueueItem;
import static com.peartree.android.kiteplayer.utils.SongCacheHelper.LARGE_ALBUM_ART_DIMENSIONS;
import static com.peartree.android.kiteplayer.utils.SongCacheHelper.SMALL_ALBUM_ART_DIMENSIONS;

/**
 * An implementation of Playback that talks to Cast.
 */
public class CastPlayback implements Playback {

    private static final String TAG = LogHelper.makeLogTag(CastPlayback.class);

    private static final String MIME_TYPE_AUDIO_MPEG = "audio/mpeg";
    private static final String ITEM_ID = "itemId";

    private final MusicProvider mMusicProvider;
    private final VideoCastConsumerImpl mCastConsumer = new VideoCastConsumerImpl() {

        @Override
        public void onRemoteMediaPlayerMetadataUpdated() {
            LogHelper.d(TAG, "onRemoteMediaPlayerMetadataUpdated");
            updateMetadata();
        }

        @Override
        public void onRemoteMediaPlayerStatusUpdated() {
            LogHelper.d(TAG, "onRemoteMediaPlayerStatusUpdated");
            updatePlaybackState();
        }
    };

    /** The current PlaybackState*/
    private int mState;
    /** Callback for making completion/error calls on */
    private Callback mCallback;
    private VideoCastManager mCastManager;
    private volatile int mCurrentPosition;
    private volatile String mCurrentMediaId;

    private CachedDataServer mHttpServer;

    public CastPlayback(MusicProvider musicProvider, Context ctx) {
        this.mMusicProvider = musicProvider;
        this.mHttpServer = new CachedDataServer(8080,ctx);
    }

    @Override
    public void start() {
        mCastManager = VideoCastManager.getInstance();
        mCastManager.addVideoCastConsumer(mCastConsumer);

        try {
            mHttpServer.start();
        } catch (IOException e) {
            LogHelper.e(TAG,e,"Failed to start http server");
        }
    }

    @Override
    public void stop(boolean notifyListeners) {
        mCastManager.removeVideoCastConsumer(mCastConsumer);
        mState = PlaybackState.STATE_STOPPED;
        if (notifyListeners && mCallback != null) {
            mCallback.onPlaybackStatusChanged(mState);
        }
        mHttpServer.stop();
    }

    @Override
    public void setState(int state) {
        this.mState = state;
    }

    @Override
    public int getCurrentStreamPosition() {
        if (!mCastManager.isConnected()) {
            return mCurrentPosition;
        }
        try {
            return (int)mCastManager.getCurrentMediaPosition();
        } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
            LogHelper.e(TAG, e, "Exception getting media position");
        }
        return -1;
    }

    @Override
    public void setCurrentStreamPosition(int pos) {
        this.mCurrentPosition = pos;
    }

    @Override
    public void play(QueueItem item) {
        loadMedia(item.getDescription().getMediaId(), true)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.immediate())
                .subscribe(
                        mediaInfo -> {},
                        e -> {
                            LogHelper.e(TAG, e, "Exception loading media");
                            if (mCallback != null) {
                                mCallback.onError(e.getMessage());
                            }
                        },
                        () -> {
                            mState = PlaybackState.STATE_BUFFERING;
                            if (mCallback != null) {
                                mCallback.onPlaybackStatusChanged(mState);
                            }
                        });
    }

    @Override
    public void pause() {
        try {
            if (mCastManager.isRemoteMediaLoaded()) {
                mCastManager.pause();
                mCurrentPosition = (int) mCastManager.getCurrentMediaPosition();
            } else {
                loadMedia(mCurrentMediaId, false)
                        .subscribeOn(Schedulers.io())
                        .observeOn(Schedulers.immediate())
                        .subscribe(
                                mediaInfo -> {},
                                e -> {
                                    LogHelper.e(TAG, e, "Exception pausing cast playback");
                                    if (mCallback != null) {
                                        mCallback.onError(e.getMessage());
                                    }
                                });
            }
        } catch (CastException | TransientNetworkDisconnectionException
                | NoConnectionException | IllegalArgumentException e) {
            LogHelper.e(TAG, e, "Exception pausing cast playback");
            if (mCallback != null) {
                mCallback.onError(e.getMessage());
            }
        }
    }

    @Override
    public void seekTo(int position) {
        if (mCurrentMediaId == null) {
            if (mCallback != null) {
                mCallback.onError("seekTo cannot be calling in the absence of mediaId.");
            }
            return;
        }
        try {
            if (mCastManager.isRemoteMediaLoaded()) {
                mCastManager.seek(position);
                mCurrentPosition = position;
            } else {
                mCurrentPosition = position;
                loadMedia(mCurrentMediaId, false)
                        .subscribeOn(Schedulers.io())
                        .observeOn(Schedulers.immediate())
                        .subscribe(
                                mediaInfo -> {},
                                e -> {
                                    LogHelper.e(TAG, e, "Exception pausing cast playback");
                                    if (mCallback != null) {
                                        mCallback.onError(e.getMessage());
                                    }
                                }
                        );
            }
        } catch (TransientNetworkDisconnectionException | NoConnectionException | IllegalArgumentException e) {
            LogHelper.e(TAG, e, "Exception pausing cast playback");
            if (mCallback != null) {
                mCallback.onError(e.getMessage());
            }
        }
    }

    @Override
    public void setCurrentMediaId(String mediaId) {
        this.mCurrentMediaId = mediaId;
    }

    @Override
    public String getCurrentMediaId() {
        return mCurrentMediaId;
    }

    @Override
    public void setCallback(Callback callback) {
        this.mCallback = callback;
    }

    @Override
    public boolean isConnected() {
        return mCastManager.isConnected();
    }

    @Override
    public boolean isPlaying() {
        try {
            return mCastManager.isConnected() && mCastManager.isRemoteMediaPlaying();
        } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
            LogHelper.e(TAG, e, "Exception calling isRemoteMoviePlaying");
        }
        return false;
    }

    @Override
    public int getState() {
        return mState;
    }

    private Observable<MediaInfo> loadMedia(String mediaId, boolean autoPlay) {
        String musicId = MediaIDHelper.extractMusicIDFromMediaID(mediaId);

        Observable<android.media.MediaMetadata> playableObservable =
                mMusicProvider
                        .getMusicForPlayback(musicId)
                        .single()
                        .cache();

        Observable<android.media.MediaMetadata> metadataObservable =
                mMusicProvider
                        .getMusicMetadata(musicId)
                        .single()
                        .timeout(500, TimeUnit.MILLISECONDS);


        return playableObservable
                .zipWith(metadataObservable, (playable, metadata) -> {
                    return new android.media.MediaMetadata.Builder(metadata)
                            .putString(
                                    MusicProvider.CUSTOM_METADATA_TRACK_SOURCE,
                                    playable.getString(MusicProvider.CUSTOM_METADATA_TRACK_SOURCE))
                            .build();
                })
                .onErrorResumeNext(playableObservable)
                .map(track -> {

                    if (!mHttpServer.isAlive()) {
                        LogHelper.w(TAG, "Local server appears to be dead. Attempting restart...");
                        mHttpServer.stop();
                        try {
                            mHttpServer.start();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    if (track == null) {
                        throw new IllegalArgumentException("Invalid mediaId " + mediaId);
                    }
                    if (!TextUtils.equals(mediaId, mCurrentMediaId)) {
                        mCurrentMediaId = mediaId;
                        mCurrentPosition = 0;
                    }
                    JSONObject customData = new JSONObject();
                    try {
                        customData.put(ITEM_ID, mediaId);
                    } catch (JSONException e) {
                        throw new RuntimeException(e); // TODO Better way to deal with exceptions
                    }
                    MediaInfo media = toCastMediaMetadata(track, customData, mHttpServer);
                    try {
                        mCastManager.loadMedia(media, autoPlay, mCurrentPosition, customData);
                    } catch (Exception e) {
                        throw new RuntimeException(e); // TODO Better way to deal with exceptions
                    }

                    return media;
                });
    }

    /**
     * Helper method to convert a {@link android.media.MediaMetadata} to a
     * {@link com.google.android.gms.cast.MediaInfo} used for sending media to the receiver app.
     *
     * @param track {@link com.google.android.gms.cast.MediaMetadata}
     * @param customData custom data specifies the local mediaId used by the player.
     * @return mediaInfo {@link com.google.android.gms.cast.MediaInfo}
     */
    private static MediaInfo toCastMediaMetadata(android.media.MediaMetadata track,
                                                 JSONObject customData,
                                                 CachedDataServer httpServer) {

        MediaMetadata mediaMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK);
        mediaMetadata.putString(MediaMetadata.KEY_TITLE,
                track.getDescription().getTitle() == null ? "" :
                        track.getDescription().getTitle().toString());
        mediaMetadata.putString(MediaMetadata.KEY_SUBTITLE,
                track.getDescription().getSubtitle() == null ? "" :
                    track.getDescription().getSubtitle().toString());
        mediaMetadata.putString(MediaMetadata.KEY_ALBUM_ARTIST,
                track.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST));
        mediaMetadata.putString(MediaMetadata.KEY_ALBUM_TITLE,
                track.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM));

        AlbumArtLoader.Key cachedImageKey = new AlbumArtLoader.Key(track);

        if (httpServer.isAlive()) {
            WebImage image = new WebImage(
                    new Uri.Builder().encodedPath(
                            httpServer.getBaseUrl() + httpServer.ALBUM_ART_PATH +
                                    "/" + cachedImageKey.toString())
                            .build());
            // First image is used by the receiver for showing the audio album art.
            mediaMetadata.addImage(image);
            // Second image is used by Cast Companion Library on the full screen activity that is shown
            // when the cast dialog is clicked.
            mediaMetadata.addImage(image);
        }

        String source = track.getString(MusicProvider.CUSTOM_METADATA_TRACK_SOURCE);

        try {
            URL remoteSource = new URL(source);
        } catch (MalformedURLException e) {
            if (!httpServer.isAlive()) {
                LogHelper.e(TAG, "toCastMediaMetadata - Local server appears to be dead.");
            }
            source = httpServer.getBaseUrl() + httpServer.SONG_FILE_PATH + source;

        }

        String mimeType = track.getString(MusicProvider.CUSTOM_METADATA_MIMETYPE);
        return new MediaInfo.Builder(source)
                .setContentType(mimeType != null?mimeType:MIME_TYPE_AUDIO_MPEG)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setMetadata(mediaMetadata)
                .setCustomData(customData)
                .build();
    }

    private void updateMetadata() {
        // Sync: We get the customData from the remote media information and update the local
        // metadata if it happens to be different from the one we are currently using.
        // This can happen when the app was either restarted/disconnected + connected, or if the
        // app joins an existing session while the Chromecast was playing a queue.
        try {
            MediaInfo mediaInfo = mCastManager.getRemoteMediaInformation();
            if (mediaInfo == null) {
                return;
            }
            JSONObject customData = mediaInfo.getCustomData();

            if (customData != null && customData.has(ITEM_ID)) {
                String remoteMediaId = customData.getString(ITEM_ID);
                if (!TextUtils.equals(mCurrentMediaId, remoteMediaId)) {
                    mCurrentMediaId = remoteMediaId;
                    if (mCallback != null) {
                        mCallback.onMetadataChanged(remoteMediaId);
                    }
                    mCurrentPosition = getCurrentStreamPosition();
                }
            }
        } catch (TransientNetworkDisconnectionException | NoConnectionException | JSONException e) {
            LogHelper.e(TAG, e, "Exception processing update metadata");
        }

    }

    private void updatePlaybackState() {
        int status = mCastManager.getPlaybackStatus();
        int idleReason = mCastManager.getIdleReason();

        LogHelper.d(TAG, "onRemoteMediaPlayerStatusUpdated ", status);

        // Convert the remote playback states to media playback states.
        switch (status) {
            case MediaStatus.PLAYER_STATE_IDLE:
                if (idleReason == MediaStatus.IDLE_REASON_FINISHED) {
                    if (mCallback != null) {
                        mCallback.onCompletion();
                    }
                }
                break;
            case MediaStatus.PLAYER_STATE_BUFFERING:
                mState = PlaybackState.STATE_BUFFERING;
                if (mCallback != null) {
                    mCallback.onPlaybackStatusChanged(mState);
                }
                break;
            case MediaStatus.PLAYER_STATE_PLAYING:
                mState = PlaybackState.STATE_PLAYING;
                updateMetadata();
                if (mCallback != null) {
                    mCallback.onPlaybackStatusChanged(mState);
                }
                break;
            case MediaStatus.PLAYER_STATE_PAUSED:
                mState = PlaybackState.STATE_PAUSED;
                updateMetadata();
                if (mCallback != null) {
                    mCallback.onPlaybackStatusChanged(mState);
                }
                break;
            default: // case unknown
                LogHelper.d(TAG, "State default : ", status);
                break;
        }
    }

    private static class CachedDataServer extends NanoHTTPD {

        private static final String TAG = LogHelper.makeLogTag(CachedDataServer.class);
        private static final String MIME_TYPE_PNG = "image/png";
        private static final int[] IMAGE_SIZE_CAST = new int[]{384,384};

        public static final String ALBUM_ART_PATH = "/albumart";
        public static final String SONG_FILE_PATH = "/songfile";

        private final Context mApplicationContext;

        public CachedDataServer(int port, Context ctx) {

            super(port);
            mApplicationContext = ctx.getApplicationContext();
        }

        @Override
        public Response serve(IHTTPSession session) {

            String uri = session.getUri();
            try {
                if (uri.startsWith(ALBUM_ART_PATH)) {
                    return serveAlbumArt(uri.substring(ALBUM_ART_PATH.length()));
                } else if (uri.startsWith(SONG_FILE_PATH)) {
                    return serveSongFile(uri.substring(SONG_FILE_PATH.length()));
                } else {
                    return new Response(Response.Status.NOT_FOUND, MIME_PLAINTEXT, (String) null);
                }
            } catch (FileNotFoundException e) {
                LogHelper.e(TAG,e,"serve - Failed to serve uri=",uri);
                return new Response(Response.Status.NOT_FOUND, MIME_PLAINTEXT, (String) null);
            } catch (ExecutionException | InterruptedException e) {
                LogHelper.e(TAG,e,"serve - Failed to serve uri=",uri);
                return new Response(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.getMessage());
            }
        }

        private Response serveSongFile(String filePath) throws FileNotFoundException {

            LogHelper.d(TAG, "serveSongFile - Request received for filePath=",filePath);

            File songFile = new File(filePath);

            MimeTypeMap mtm = MimeTypeMap.getSingleton();
            String extension = MimeTypeMap.getFileExtensionFromUrl(filePath);
            String mimeType = mtm.getMimeTypeFromExtension(extension);

            Response r = new Response(
                    Response.Status.OK,
                    !TextUtils.isEmpty(mimeType)?mimeType:MIME_TYPE_AUDIO_MPEG,
                    new FileInputStream(songFile));

            LogHelper.d(TAG, "serveSongFile - Serving filePath=",filePath,
                    " with mimeType=", mimeType);

            return r;
        }

        private Response serveAlbumArt(String albumArtPath) throws ExecutionException, InterruptedException {

            LogHelper.d(TAG, "serveAlbumArt - Request received for albumArtPath=",albumArtPath);

            String cacheSignature = albumArtPath.substring(1);
            Bitmap albumArt;

            try {
                albumArt = Glide
                        .with(mApplicationContext)
                        .load(new android.media.MediaMetadata.Builder().build())
                        .asBitmap()
                        .signature(new StringSignature(cacheSignature))
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(IMAGE_SIZE_CAST[0],IMAGE_SIZE_CAST[1])
                        .get();
            } catch (InterruptedException | ExecutionException e) {

                LogHelper.w(TAG, e, "serveAlbumArt - No album art found for albumArtPath=",
                        albumArtPath,", falling back to default art.");

                albumArt = Glide
                        .with(mApplicationContext)
                        .load(R.drawable.ic_album_art)
                        .asBitmap()
                        .into(IMAGE_SIZE_CAST[0],IMAGE_SIZE_CAST[1])
                        .get();
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            albumArt.compress(Bitmap.CompressFormat.PNG, 0, bos);

            Response r = new Response(
                    Response.Status.OK, MIME_TYPE_PNG, new ByteArrayInputStream(bos.toByteArray()));

            LogHelper.d(TAG, "serveAlbumArt - Serving albumArtPath=",albumArtPath);

            return r;
        }

        @Nullable
        public String getBaseUrl() {

            WifiManager wifiManager =
                    (WifiManager) mApplicationContext.getSystemService(
                            mApplicationContext.WIFI_SERVICE);

            int ipAddress = wifiManager.getConnectionInfo().getIpAddress();

            // Convert little-endian to big-endianif needed
            if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
                ipAddress = Integer.reverseBytes(ipAddress);
            }

            byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

            String ipAddressString;
            try {
                ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
            } catch (UnknownHostException ex) {
                LogHelper.e(TAG, ex, "getBaseUrl - Unable to get host address.");
                ipAddressString = null;
            }

            return "http://"+ipAddressString+":"+getListeningPort();
        }

    }

}
