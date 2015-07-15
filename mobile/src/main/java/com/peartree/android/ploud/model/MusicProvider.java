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

import android.media.MediaMetadata;
import android.media.MediaMetadataRetriever;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.peartree.android.ploud.database.DropboxDBEntry;
import com.peartree.android.ploud.database.DropboxDBEntryDAO;
import com.peartree.android.ploud.dropbox.DropboxSyncService;
import com.peartree.android.ploud.utils.LogHelper;

import java.util.Collections;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Utility class to get a list of MusicTrack's based on a server-side JSON
 * configuration.
 */
@Singleton
public class MusicProvider {

    private static final String TAG = LogHelper.makeLogTag(MusicProvider.class);

    public static final String CUSTOM_METADATA_TRACK_SOURCE = "__SOURCE__";

    private DropboxAPI<AndroidAuthSession> mDBApi = null;
    private DropboxDBEntryDAO mEntryDao;
    private DropboxSyncService mDBSyncService;

    @Inject
    public MusicProvider(DropboxAPI<AndroidAuthSession> dbApi, DropboxDBEntryDAO entryDao, DropboxSyncService syncService) {

        // TODO Better encapsulate features under syncService
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
     * Get an iterator over the list of genres
     *
     * @return genres
     */
    public Iterable<String> getGenres() {

        // TODO Implement getGenres
        return Collections.emptyList();
    }

    /**
     * Get music tracks of the given genre
     *
     */
    public Iterable<MediaMetadata> getMusicsByGenre(String genre) {

        // TODO Implement getMusicByGenre
        return Collections.emptyList();

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
    public MediaMetadata getMusic(String musicId) {

        DropboxDBEntry entry;
        MediaMetadata metadata = null;

        entry = mEntryDao.findById(Long.valueOf(musicId));

        if (entry != null) {
            try {
                metadata = buildMetadataFromDBEntry(entry);
            } catch (DropboxException de) {
                LogHelper.d(TAG,de);
            }
        }

        return metadata;

    }

    public MediaMetadata buildMetadataFromDBEntry(DropboxDBEntry entry) throws DropboxException {

        DropboxAPI.DropboxLink link;
        MediaMetadataRetriever retriever;
        MediaMetadata.Builder builder = new MediaMetadata.Builder();

        link = mDBApi.media(entry.getParentDir() + entry.getFilename(), false);

        retriever = new MediaMetadataRetriever();
        retriever.setDataSource(link.url);

        return builder
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, Long.toString(entry.getId()))
                .putString(CUSTOM_METADATA_TRACK_SOURCE, link.url)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM))
                .putString(MediaMetadata.METADATA_KEY_ARTIST, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST))
                .putLong(MediaMetadata.METADATA_KEY_DURATION, Long.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)))
                .putString(MediaMetadata.METADATA_KEY_GENRE, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE))
                // .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, Long.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)))
                .putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, Long.valueOf(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS)))
                .build();
    }

    public synchronized void updateMusic(String musicId, MediaMetadata metadata) {

        // TODO Implement updateMusic genre?
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
     * for future reference, keying tracks by musicId and grouping by genre.
     */
    public void retrieveMediaAsync(final Callback callback) {

        mDBSyncService
                .getDBEntrySync()
                .observeOn(AndroidSchedulers.mainThread())
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

    private synchronized void buildListsByGenre() {

        // TODO Implement buildListsByGenre?
    }

}
