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
 *     https://mozilla.org/MPL/2.0/.
 */

package com.misterpereira.android.kiteplayer.model;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.misterpereira.android.kiteplayer.VoiceSearchParams;
import com.misterpereira.android.kiteplayer.database.DropboxDBEntry;
import com.misterpereira.android.kiteplayer.database.DropboxDBEntryDAO;
import com.misterpereira.android.kiteplayer.database.DropboxDBSong;
import com.misterpereira.android.kiteplayer.database.DropboxDBSongDAO;
import com.misterpereira.android.kiteplayer.dropbox.DropboxSyncService;
import com.misterpereira.android.kiteplayer.utils.LogHelper;
import com.misterpereira.android.kiteplayer.utils.MediaIDHelper;
import com.misterpereira.android.kiteplayer.utils.NetworkHelper;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;
import rx.schedulers.Schedulers;

import static com.misterpereira.android.kiteplayer.utils.SongCacheHelper.LARGE_ALBUM_ART_DIMENSIONS;
import static com.misterpereira.android.kiteplayer.utils.SongCacheHelper.SMALL_ALBUM_ART_DIMENSIONS;

@Singleton
public class MusicProvider {

    private static final String TAG = LogHelper.makeLogTag(MusicProvider.class);

    public static final String CUSTOM_METADATA_TRACK_SOURCE = "__SOURCE__";
    public static final String CUSTOM_METADATA_FILENAME = "__FILENAME__";
    public static final String CUSTOM_METADATA_DIRECTORY = "__DIRECTORY__";
    public static final String CUSTOM_METADATA_IS_DIRECTORY = "__IS_DIRECTORY__";
    public static final String CUSTOM_METADATA_MIMETYPE = "__MIMETYPE__";


    private final Context mApplicationContext;

    private final DropboxDBEntryDAO mEntryDao;
    private final DropboxDBSongDAO mSongDao;
    private final DropboxSyncService mDBSyncService;

    private volatile State mCurrentState = State.NON_INITIALIZED;

    @Inject
    public MusicProvider(Application application,
                         DropboxDBEntryDAO entryDao,
                         DropboxDBSongDAO songDao,
                         DropboxSyncService syncService) {

        this.mApplicationContext = application.getApplicationContext();

        this.mEntryDao = entryDao;
        this.mSongDao = songDao;
        this.mDBSyncService = syncService;
    }

    enum State {
        NON_INITIALIZED, INITIALIZING, INITIALIZED
    }

    /**
     * Get the list of music tracks from a server and caches the track information
     */
    public Observable init() {

        Observable<Long> existingEntries =
                mEntryDao
                        .findByParentDir("/")
                        .map(DropboxDBEntry::getId);

        Observable<Long> newEntries =
                mDBSyncService
                        .synchronizeEntryDB();

        return existingEntries
                .isEmpty()
                .flatMap(empty -> {
                    if (empty) {
                        // DB is empty
                        // Synchronization must happen happen synchronously
                        return newEntries;

                    } else {

                        // DB not empty
                        // Synchronization will happen asynchronously
                        newEntries
                                .subscribeOn(Schedulers.io())
                                .subscribe(id -> {}, error ->
                                        LogHelper.w(TAG, error,
                                                "init - Failed to refresh DB asynchronously"));

                        // Consider provider initialized
                        return Observable.empty();
                    }
                })
                .ignoreElements()
                .doOnSubscribe(() -> mCurrentState = State.INITIALIZING)
                .doOnError((error) -> mCurrentState = State.NON_INITIALIZED)
                .doOnCompleted(() -> mCurrentState = State.INITIALIZED);
    }

    public Observable refresh() {
        return mDBSyncService.synchronizeEntryDB().ignoreElements();
    }

    public Observable<MediaMetadata> getMusic(String musicId) {

        return getEntryWithSong(musicId)
                .flatMap(this::toMediaMetadata);
    }

    public Observable<MediaMetadata> getMusicForPlayback(String musicId) {

        return mDBSyncService
                .prepareSongForPlayback(getEntryWithSong(musicId))
                .flatMap(this::toMediaMetadata);
    }

    public void preloadPlaylist(List<MediaSession.QueueItem> queue) {

        mDBSyncService.downloadSongQueue(
                Observable
                        .from(queue)
                        .flatMap(queueItem -> {
                            String musicId =
                                    MediaIDHelper.extractMusicIDFromMediaID(
                                            queueItem.getDescription().getMediaId());
                            return getEntryWithSong(musicId);
                        }));
    }

    public Observable<MediaMetadata> getMusicMetadata(String musicId) {

        return mDBSyncService
                .fillSongMetadata(getEntryWithSong(musicId))
                .flatMap(this::toMediaMetadata)
                .map(mm -> {

                    Bitmap bitmap = null;
                    Bitmap icon = null;

                    try {
                        bitmap = Glide
                                .with(mApplicationContext)
                                .load(mm)
                                .asBitmap()
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .into(LARGE_ALBUM_ART_DIMENSIONS[0], LARGE_ALBUM_ART_DIMENSIONS[1])
                                .get();

                        icon = Glide
                                .with(mApplicationContext)
                                .load(mm)
                                .asBitmap()
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .into(SMALL_ALBUM_ART_DIMENSIONS[0], SMALL_ALBUM_ART_DIMENSIONS[1])
                                .get();

                    } catch (InterruptedException | ExecutionException e) {
                        LogHelper.w(TAG, e, "getMusicMetadata - Failed loading album art");
                    }

                    return new MediaMetadata.Builder(mm)

                            // set high resolution bitmap in METADATA_KEY_ALBUM_ART. This is used, for
                            // example, on the lockscreen background when the media session is active.
                            .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)

                                    // set small version of the album art in the DISPLAY_ICON. This is used on
                                    // the MediaDescription and thus it should be small to be serialized if
                                    // necessary..
                            .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, icon)

                            .build();
                });

    }

    /**
     * Get media by parent folder
     * Results can include folders and audio files
     */
    public Observable<MediaMetadata> getMusicByFolder(@NonNull String parentFolder) {

        return completeWithSong(mEntryDao
                .findByParentDir(parentFolder))
                .flatMap(this::toMediaMetadata);
    }

    public Observable<MediaMetadata> getMusicAtRandom(int count) {
        return completeWithSong(mEntryDao
                .findRandom(count))
                .flatMap(this::toMediaMetadata);
    }

    public Observable<MediaMetadata> searchMusicByVoiceParams(VoiceSearchParams params) {

        LogHelper.d(TAG,"searchMusicByVoiceParams - Search by params: ",params.toString());

        Observable<MediaMetadata> songQueryResults;
        Observable<MediaMetadata> entryQueryResults;

        if (params.isAny) {
            songQueryResults = Observable.empty();
        } else if (params.isUnstructured) {
            songQueryResults =
                    wrapInEntry(mSongDao.queryByKeyword(params.query))
                            .flatMap(this::toMediaMetadata);
        } else {
            songQueryResults = wrapInEntry(
                    mSongDao.query(
                            params.isGenreFocus?params.genre:null,
                            params.isArtistFocus?params.artist:null,
                            params.isAlbumFocus?params.album:null,
                            params.isSongFocus?params.song:null))
                    .flatMap(this::toMediaMetadata);
        }

        entryQueryResults =
                mEntryDao
                        .queryByFilenameKeyword(params.query)
                        .map(this::completeWithSong)
                        .flatMap(this::toMediaMetadata);

        return songQueryResults
                .concatWith(entryQueryResults)
                .distinct()
                .onBackpressureBuffer();
    }

    private Observable<DropboxDBEntry> getEntryWithSong(String musicId) {

        return completeWithSong(Observable
                .just(mEntryDao.findById(Long.valueOf(musicId))));

    }

    public void deleteAll() {
        mEntryDao.deleteAll();
        mCurrentState = State.NON_INITIALIZED;
    }

    public boolean isInitialized() {
        return mCurrentState == State.INITIALIZED;
    }

    private Observable<MediaMetadata> toMediaMetadata(@NonNull DropboxDBEntry entry) {
        return Observable.just(
                buildMetadataFromDBEntry(
                        mApplicationContext,
                        entry,
                        mDBSyncService.getCachedSongFile(entry),
                        NetworkHelper.canStream(mApplicationContext)));
    }

    @NonNull
    private Observable<DropboxDBEntry> completeWithSong(Observable<DropboxDBEntry> entries) {
        return entries.map(this::completeWithSong);
    }

    private DropboxDBEntry completeWithSong(DropboxDBEntry entry) {
        if (entry != null) {
            if (!entry.isDir()) {
                entry.setSong(mSongDao.findByEntryId(entry.getId()));
            }
        }
        return entry;
    }

    @NonNull
    private Observable<DropboxDBEntry> wrapInEntry(Observable<DropboxDBSong> songs) {
        return songs.map(song -> {
            DropboxDBEntry entry = mEntryDao.findById(song.getEntryId());
            entry.setSong(song);
            return entry;
        });
    }

    public static MediaMetadata buildMetadataFromDBEntry(Context ctx, DropboxDBEntry entry, @Nullable File cachedSongFile, boolean canStream) {

        MediaMetadata.Builder builder = new MediaMetadata.Builder();

        builder
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, Long.toString(entry.getId()))
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, entry.getFilename())
                .putString(CUSTOM_METADATA_FILENAME, entry.getFilename())
                .putString(CUSTOM_METADATA_DIRECTORY, entry.getParentDir())
                .putString(CUSTOM_METADATA_IS_DIRECTORY, Boolean.toString(entry.isDir()));

        if (!entry.isDir() && entry.getOrCreateSong() != null) {

            DropboxDBSong song = entry.getSong();

            if (cachedSongFile != null) {
                builder.putString(CUSTOM_METADATA_TRACK_SOURCE, cachedSongFile.getAbsolutePath());
            } else if (song.getDownloadURL() != null && canStream) {
                builder.putString(CUSTOM_METADATA_TRACK_SOURCE, song.getDownloadURL().toString());
            }

            String displayTitle =
                    song.getTitle()!=null ? song.getTitle() : entry.getFilename();
            String displaySubtitle =
                    song.getAlbum() != null ? song.getAlbum() : entry.getParentDir();

            builder
                    .putString(MediaMetadata.METADATA_KEY_TITLE,
                            // Found to be necessary for metadata display over bluetooth
                            song.getTitle() != null ? song.getTitle() : displayTitle)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM,
                            // Found to be necessary for metadata display over bluetooth
                            song.getAlbum() != null ? song.getAlbum() : displaySubtitle)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, song.getAlbumArtist())
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, song.getArtist())
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, song.getDuration())
                    .putString(MediaMetadata.METADATA_KEY_GENRE, song.getGenre())
                    .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, song.getTrackNumber())
                    .putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, song.getTotalTracks())
                    .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, displayTitle)
                    .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, displaySubtitle)
                    .putString(CUSTOM_METADATA_MIMETYPE, entry.getMimeType());

        } else if (entry.isDir()){

            try {

                Bitmap icon = Glide
                        .with(ctx.getApplicationContext())
                        .load("android.resource://" + ctx.getPackageName() +
                                "/drawable/ic_folder_grey_52dp")
                        .asBitmap()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(SMALL_ALBUM_ART_DIMENSIONS[0], SMALL_ALBUM_ART_DIMENSIONS[1])
                        .get();

                builder
                        .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, icon);

            } catch (InterruptedException | ExecutionException e) {
                LogHelper.w(TAG,"Failed to load folder icon");
            }

        }

        return builder.build();
    }

    public static boolean willBePlayable(Context context, MediaMetadata mm) {

        boolean hasSource;
        boolean isSourceRemote;
        boolean canStream;

        hasSource = mm.getString(CUSTOM_METADATA_TRACK_SOURCE) != null;

        try {
            new URL(mm.getString(CUSTOM_METADATA_TRACK_SOURCE));
            isSourceRemote = true;
        } catch (Exception e) {
            isSourceRemote = false;
        }

        canStream = NetworkHelper.canStream(context);

        return (hasSource && !isSourceRemote) || canStream;

    }

}
