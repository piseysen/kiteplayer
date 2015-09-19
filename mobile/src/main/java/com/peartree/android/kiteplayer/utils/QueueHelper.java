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

import android.content.Context;
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
        if (categoryType.equals(MEDIA_ID_MUSICS_BY_SEARCH)) {
            // TODO Test (Oddball case related to cast)
            mmObservable = musicProvider.searchMusicByVoiceParams(
                    new VoiceSearchParams(categories[0],new Bundle()));
        } else if (categoryType.equals(MEDIA_ID_ROOT)) {
            // TODO Move folder formatting to utils class
            String folder = "/"+ TextUtils.join("/",categories);
            mmObservable = musicProvider
                    .getMusicByFolder(folder);
        } else {
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
    public static Observable<QueueItem> getRandomQueue(MusicProvider musicProvider, Context ctx) {
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
                .filter(mm -> MusicProvider.willBePlayable(ctx,mm))
                .zipWith(mIndexSequence, (mm, index) ->
                        convertToQueueItem(
                                mm, index, MEDIA_ID_MUSICS_BY_SEARCH, new String[]{"random"}));
    }

    public static boolean isIndexValid(int index, List<QueueItem> queue) {
        return (queue != null && index >= 0 && index < queue.size());
    }
}
