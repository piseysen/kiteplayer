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

package com.peartree.android.kiteplayer.utils;

import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.MediaSession.QueueItem;
import android.os.Bundle;
import android.text.TextUtils;

import com.peartree.android.kiteplayer.VoiceSearchParams;
import com.peartree.android.kiteplayer.model.MusicProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import rx.Observable;

import static com.peartree.android.kiteplayer.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_SEARCH;
import static com.peartree.android.kiteplayer.utils.MediaIDHelper.MEDIA_ID_ROOT;

/**
 * Utility class to help on queue related tasks.
 */
public class QueueHelper {

    private static final String TAG = LogHelper.makeLogTag(QueueHelper.class);

    public static Observable<QueueItem> getPlayingQueue(String mediaId,
            MusicProvider musicProvider) {

        final String categoryType = MediaIDHelper.extractBrowserCategoryFromMediaID(mediaId);
        final String[] categories = MediaIDHelper.extractBrowseCategoryValueFromMediaID(mediaId);

        if (categoryType == null) {
            LogHelper.e(TAG, "Could not build a playing queue for this mediaId: ", mediaId);
            return null;
        }

        LogHelper.d(TAG, "Creating playing queue for ", categoryType, ",  ", categories);

        Observable<MediaMetadata> mmObservable;
        if (categoryType.equals(MEDIA_ID_MUSICS_BY_SEARCH)) {
            // TODO Test
            mmObservable = Observable.from(musicProvider.searchMusicBySongTitle(categories[0]));
        } else if (categoryType.equals(MEDIA_ID_ROOT)) {
            // TODO Move folder formatting to utils class
            String folder = "/"+ TextUtils.join("/",categories);
            mmObservable = musicProvider
                    .getMusicByFolder(folder, MusicProvider.FLAG_SONG_PLAY_READY|MusicProvider.FLAG_SONG_METADATA_TEXT);
        } else {
            LogHelper.e(TAG, "Unrecognized category type: ", categoryType, " for media ", mediaId);
            return null;
        }

        return mmObservable
                .filter(mm -> mm.getString(MusicProvider.CUSTOM_METADATA_TRACK_SOURCE) != null)
                .lift(new CountOperator<>())
                .map(pair -> convertToQueueItem(pair.second,pair.first,categoryType,categories));
    }



    public static Observable<QueueItem> getPlayingQueueFromSearch(String query,
            Bundle queryParams, MusicProvider musicProvider) {

        LogHelper.d(TAG, "Creating playing queue for musics from search: ", query,
            " params=", queryParams);

        VoiceSearchParams params = new VoiceSearchParams(query, queryParams);

        LogHelper.d(TAG, "VoiceSearchParams: ", params);

        if (params.isAny) {
            // If isAny is true, we will play anything. This is app-dependent, and can be,
            // for example, favorite playlists, "I'm feeling lucky", most recent, etc.
            return getRandomQueue(musicProvider);
        }

        Iterable<MediaMetadata> result = null;
        if (params.isAlbumFocus) {
            result = musicProvider.searchMusicByAlbum(params.album);
        } else if (params.isGenreFocus) {
            result = musicProvider.searchMusicByGenre(params.genre);
        } else if (params.isArtistFocus) {
            result = musicProvider.searchMusicByArtist(params.artist);
        } else if (params.isSongFocus) {
            result = musicProvider.searchMusicBySongTitle(params.song);
        }

        // If there was no results using media focus parameter, we do an unstructured query.
        // This is useful when the user is searching for something that looks like an artist
        // to Google, for example, but is not. For example, a user searching for Madonna on
        // a PodCast application wouldn't get results if we only looked at the
        // Artist (podcast author). Then, we can instead do an unstructured search.
        if (params.isUnstructured || result == null || !result.iterator().hasNext()) {
            // To keep it simple for this example, we do unstructured searches on the
            // song title only. A real world application could search on other fields as well.
            result = musicProvider.searchMusicBySongTitle(query);
        }

        return Observable
                .from(result)
                .filter(mm -> mm.getString(MusicProvider.CUSTOM_METADATA_TRACK_SOURCE) != null)
                .lift(new CountOperator<MediaMetadata>())
                .map(pair -> convertToQueueItem(pair.second, pair.first, MEDIA_ID_MUSICS_BY_SEARCH, new String[]{query}));
    }


    public static int getMusicIndexOnQueue(Iterable<QueueItem> queue,
             String mediaId) {
        int index = 0;
        for (QueueItem item : queue) {
            if (mediaId.equals(item.getDescription().getMediaId())) {
                return index;
            }
            index++;
        }
        return -1;
    }

    public static int getMusicIndexOnQueue(Iterable<QueueItem> queue,
             long queueId) {
        int index = 0;
        for (QueueItem item : queue) {
            if (queueId == item.getQueueId()) {
                return index;
            }
            index++;
        }
        return -1;
    }

    private static QueueItem convertToQueueItem(MediaMetadata track, int index, String categoryType, String[] categories) {

        // TODO Figure out more elegant way to combine arrays
        String[] combined = new String[categories.length+1];

        combined[0] = categoryType;
        for (int i = 0; i < categories.length; i++) {
            combined[i+1] = categories[i];
        }

        return convertToQueueItem(track, index, combined);
    }

    private static QueueItem convertToQueueItem(
            MediaMetadata track, int index, String[] categories) {

        // We create a hierarchy-aware mediaID, so we know what the queue is about by looking
        // at the QueueItem media IDs.
        String hierarchyAwareMediaID = MediaIDHelper.createMediaID(
                track.getDescription().getMediaId(), categories);

        MediaMetadata trackCopy = new MediaMetadata.Builder(track)
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, hierarchyAwareMediaID)
                .build();

        // We don't expect queues to change after created, so we use the item index as the
        // queueId. Any other number unique in the queue would work.
        QueueItem item = new QueueItem(
                trackCopy.getDescription(), index);

        return item;
    }

    /**
     * Create a random queue.
     *
     * @param musicProvider the provider used for fetching music.
     * @return list containing {@link MediaSession.QueueItem}'s
     */
    public static Observable<QueueItem> getRandomQueue(MusicProvider musicProvider) {
        List<MediaMetadata> result = new ArrayList<>();

        // TODO Implement getRandomQueue

//        for (String genre: musicProvider.getGenres()) {
//            Iterable<MediaMetadata> tracks = musicProvider.getMusicsByGenre(genre);
//            for (MediaMetadata track: tracks) {
//                if (ThreadLocalRandom.current().nextBoolean()) {
//                    result.add(track);
//                }
//            }
//        }
        LogHelper.d(TAG, "getRandomQueue: result.size=", result.size());

        Collections.shuffle(result);

        return Observable
                .from(result)
                .filter(mm -> mm.getString(MusicProvider.CUSTOM_METADATA_TRACK_SOURCE) != null)
                .lift(new CountOperator<>())
                .map(pair -> convertToQueueItem(pair.second, pair.first, MEDIA_ID_MUSICS_BY_SEARCH, new String[]{"random"}));
    }

    public static boolean isIndexPlayable(int index, List<QueueItem> queue) {
        return (queue != null && index >= 0 && index < queue.size());
    }
}
