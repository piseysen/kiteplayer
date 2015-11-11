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
 *
 *      https://mozilla.org/MPL/2.0/
 *
 */

package com.misterpereira.android.kiteplayer.utils;

import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.MediaSession.QueueItem;
import android.os.Bundle;

import com.misterpereira.android.kiteplayer.VoiceSearchParams;
import com.misterpereira.android.kiteplayer.model.MusicProvider;

import java.util.List;

import rx.Observable;

import static com.misterpereira.android.kiteplayer.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_SEARCH;
import static com.misterpereira.android.kiteplayer.utils.MediaIDHelper.MEDIA_ID_ROOT;

/**
 * Utility class to help on queue related tasks.
 */
public class QueueHelper {

    private static final String TAG = LogHelper.makeLogTag(QueueHelper.class);
    private static final Observable<Integer> mIndexSequence =
            Observable.just(1).repeat().scan(0,(first,second) -> first+second);

    public static Observable<QueueItem> getPlayingQueue(String mediaId,
            MusicProvider musicProvider, Context ctx) {

        final String categoryType = MediaIDHelper.extractBrowserCategoryFromMediaID(mediaId);
        final String[] categories = MediaIDHelper.extractBrowseCategoryValueFromMediaID(mediaId);

        if (categoryType == null) {
            LogHelper.e(TAG, "Could not build a playing queue for this mediaId: ", mediaId);
            return null;
        }

        LogHelper.d(TAG, "Creating playing queue for ", categoryType, ",  ", categories);

        Observable<MediaMetadata> mmObservable;
        switch (categoryType) {
            case MEDIA_ID_MUSICS_BY_SEARCH:
                // TODO Test (Oddball case related to cast)
                mmObservable = musicProvider.searchMusicByVoiceParams(
                        new VoiceSearchParams(categories[0], new Bundle()));
                break;
            case MEDIA_ID_ROOT:
                String folder = DropboxHelper.makeDropboxPath(null, categories);
                mmObservable = musicProvider
                        .getMusicByFolder(folder);
                break;
            default:
                LogHelper.e(TAG, "Unrecognized category type: ", categoryType, " for media ", mediaId);
                return null;
        }

        return mmObservable
                .filter(mm -> MusicProvider.willBePlayable(ctx,mm))
                .zipWith(mIndexSequence, (mm, index) ->
                        convertToQueueItem(mm, index, categoryType, categories));
    }



    public static Observable<QueueItem> getPlayingQueueFromSearch(String query,
            Bundle queryParams, MusicProvider musicProvider, Context ctx) {

        LogHelper.d(TAG, "Creating playing queue for musics from search: ", query,
            " params=", queryParams);

        VoiceSearchParams params = new VoiceSearchParams(query, queryParams);

        LogHelper.d(TAG, "VoiceSearchParams: ", params);

        if (params.isAny) {
            // If isAny is true, we will play anything. This is app-dependent, and can be,
            // for example, favorite playlists, "I'm feeling lucky", most recent, etc.
            return getRandomQueue(musicProvider, ctx);
        }

        Observable<MediaMetadata> result =
                musicProvider.searchMusicByVoiceParams(params);

        return result
                .switchIfEmpty(musicProvider.searchMusicByVoiceParams(
                        new VoiceSearchParams(query, new Bundle())))
                .filter(mm -> MusicProvider.willBePlayable(ctx,mm))
                .zipWith(mIndexSequence, (mm, index) ->
                        convertToQueueItem(
                                mm, index,
                                MEDIA_ID_MUSICS_BY_SEARCH, new String[]{query}));
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

        String[] combined = new String[categories.length+1];
        combined[0] = categoryType;
        System.arraycopy(categories, 0, combined, 1, categories.length);

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

        return new QueueItem(
                trackCopy.getDescription(), index);
    }

    /**
     * Create a random queue.
     *
     * @param musicProvider the provider used for fetching music.
     * @return list containing {@link MediaSession.QueueItem}'s
     */
    public static Observable<QueueItem> getRandomQueue(MusicProvider musicProvider, Context ctx) {
        Observable<MediaMetadata> mmObservable =
                musicProvider.getMusicAtRandom(30);


        return mmObservable
                .filter(mm -> MusicProvider.willBePlayable(ctx, mm))
                .zipWith(mIndexSequence, (mm, index) ->
                        convertToQueueItem(
                                mm, index, MEDIA_ID_MUSICS_BY_SEARCH, new String[]{"random"}));
    }

    public static boolean isIndexValid(int index, List<QueueItem> queue) {
        return (queue != null && index >= 0 && index < queue.size());
    }
}
