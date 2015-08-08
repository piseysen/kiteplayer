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

package com.peartree.android.ploud.model;

import android.app.Application;
import android.content.Context;
import android.media.MediaMetadata;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.peartree.android.ploud.database.DropboxDBEntry;
import com.peartree.android.ploud.database.DropboxDBEntryDAO;
import com.peartree.android.ploud.database.DropboxDBSong;
import com.peartree.android.ploud.dropbox.DropboxSyncService;
import com.peartree.android.ploud.utils.LogHelper;

import java.io.File;
import java.util.Collections;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;
import rx.schedulers.Schedulers;

/**
 * Utility class to get a list of MusicTrack's based on a server-side JSON
 * configuration.
 */
@Singleton
public class MusicProvider {

    private static final String TAG = LogHelper.makeLogTag(MusicProvider.class);

    public static final String CUSTOM_METADATA_TRACK_SOURCE = "__SOURCE__";
    public static final String CUSTOM_METADATA_FILENAME = "__FILENAME__";
    public static final String CUSTOM_METADATA_DIRECTORY = "__DIRECTORY__";
    public static final String CUSTOM_METADATA_IS_DIRECTORY = "__IS_DIRECTORY__";

    private Context mApplicationContext;

    private DropboxAPI<AndroidAuthSession> mDBApi = null;
    private DropboxDBEntryDAO mEntryDao;
    private DropboxSyncService mDBSyncService;

    @Inject
    public MusicProvider(Application application,
                         DropboxAPI<AndroidAuthSession> dbApi,
                         DropboxDBEntryDAO entryDao,
                         DropboxSyncService syncService) {

        this.mApplicationContext = application.getApplicationContext();

        this.mDBApi = dbApi;
        this.mEntryDao = entryDao;
        this.mDBSyncService = syncService;
    }

    enum State {
        NON_INITIALIZED, INITIALIZING, INITIALIZED
    }

    private volatile State mCurrentState = State.NON_INITIALIZED;

    public interface Callback {
        void onMusicCatalogReady(boolean success);
    }


    /**
     * Get media by parent folder
     * Results can include folders and audio files
     */
    public Observable<MediaMetadata> getMusicByFolder(@NonNull String parentFolder,boolean liveSourceRequired) {

        // TODO Sort results

        return mDBSyncService.fillSongMetadata(mEntryDao.getFindByDir(parentFolder),liveSourceRequired)
                .map(entry -> buildMetadataFromDBEntry(entry,mDBSyncService.getCachedSongFile(entry)));
    }


    /**
     * Very basic implementation of a search that filter music tracks with title containing
     * the given query.
     *
     */
    public Iterable<MediaMetadata> searchMusicByGenre(String query) {
        return searchMusic(MediaMetadata.METADATA_KEY_TITLE, query);
    }

    /**
     * Very basic implementation of a search that filter music tracks with title containing
     * the given query.
     *
     */
    public Iterable<MediaMetadata> searchMusicBySongTitle(String query) {
        return searchMusic(MediaMetadata.METADATA_KEY_TITLE, query);
    }

    /**
     * Very basic implementation of a search that filter music tracks with album containing
     * the given query.
     *
     */
    public Iterable<MediaMetadata> searchMusicByAlbum(String query) {
        return searchMusic(MediaMetadata.METADATA_KEY_ALBUM, query);
    }

    /**
     * Very basic implementation of a search that filter music tracks with artist containing
     * the given query.
     *
     */
    public Iterable<MediaMetadata> searchMusicByArtist(String query) {
        return searchMusic(MediaMetadata.METADATA_KEY_ARTIST, query);
    }

    Iterable<MediaMetadata> searchMusic(String metadataField, String query) {

        // TODO Implement searchMusic
        return Collections.emptyList();

    }


    /**
     * Return the MediaMetadata for the given musicID.
     *
     * @param musicId The unique, non-hierarchical music ID.
     */
    public Observable<MediaMetadata> getMusic(String musicId,boolean liveSourceRequired) {

        return mDBSyncService.fillSongMetadata(
                Observable.just(mEntryDao.findById(Long.valueOf(musicId))),liveSourceRequired)
                .map(entry -> buildMetadataFromDBEntry(entry,mDBSyncService.getCachedSongFile(entry)));

    }

    public synchronized void updateMusic(String musicId, MediaMetadata metadata) {

        // TODO Implement updateMusic? This is intended to cache mm containing album art data
    }

    public void setFavorite(String musicId, boolean favorite) {

        // TODO Implement setFavorite
    }

    public boolean isFavorite(String musicId) {

        // TODO Implement isFavorite
        return false;
    }

    public boolean isInitialized() {
        return mCurrentState == State.INITIALIZED;
    }

    /**
     * Get the list of music tracks from a server and caches the track information
     */
    public void retrieveMediaAsync(final Callback callback) {

        mDBSyncService
                .synchronizeEntryDB()
                .subscribeOn(Schedulers.io())
                .subscribe(
                        id -> {
                            mCurrentState = State.INITIALIZING;
                        }, error -> {
                            mCurrentState = State.NON_INITIALIZED;
                            callback.onMusicCatalogReady(false);
                        }, () -> {
                            mCurrentState = State.INITIALIZED;
                            callback.onMusicCatalogReady(true);
                        });
    }

    public static MediaMetadata buildMetadataFromDBEntry(DropboxDBEntry entry, @Nullable File cachedSongFile) {

        MediaMetadata.Builder builder = new MediaMetadata.Builder();

        builder
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, Long.toString(entry.getId()))
                .putString(CUSTOM_METADATA_FILENAME, entry.getFilename())
                .putString(CUSTOM_METADATA_DIRECTORY, entry.getParentDir())
                .putString(CUSTOM_METADATA_IS_DIRECTORY, Boolean.toString(entry.isDir()));

        if (!entry.isDir() && entry.getSong() != null) {

            DropboxDBSong song = entry.getSong();

            if (cachedSongFile != null) {
                builder.putString(CUSTOM_METADATA_TRACK_SOURCE, cachedSongFile.getAbsolutePath());
            } else if (song.getDownloadURL() != null) {
                // TODO Deal with expired download URLs
                builder.putString(CUSTOM_METADATA_TRACK_SOURCE, song.getDownloadURL().toString());
            } else {
                // TODO Ok to return without source?
            }

            builder
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, song.getAlbum() != null ? song.getAlbum() : entry.getParentDir())
                    .putString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, song.getAlbumArtist())
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, song.getArtist())
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, song.getDuration())
                    .putString(MediaMetadata.METADATA_KEY_GENRE, song.getGenre())
                    .putString(MediaMetadata.METADATA_KEY_TITLE, song.getTitle()!=null ? song.getTitle() : entry.getFilename())
                    .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, song.getTrackNumber())
                    .putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, song.getTotalTracks());

        }

        return builder.build();
    }

}
