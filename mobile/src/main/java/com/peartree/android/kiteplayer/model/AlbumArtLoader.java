/*
 * Copyright (c) 2015 Rafael Pereira
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 *     https://mozilla.org/MPL/2.0/.
 */

package com.peartree.android.kiteplayer.model;

import android.content.Context;
import android.media.MediaMetadata;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.GenericLoaderFactory;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.stream.StreamModelLoader;
import com.peartree.android.kiteplayer.KiteApplication;
import com.peartree.android.kiteplayer.dropbox.DropboxSyncService;
import com.peartree.android.kiteplayer.utils.LogHelper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;

import javax.inject.Inject;

import rx.Observable;
import rx.Subscription;
import rx.schedulers.Schedulers;

public class AlbumArtLoader implements StreamModelLoader<MediaMetadata> {

    private static final String TAG = LogHelper.makeLogTag(AlbumArtLoader.class);

    private DropboxSyncService mSyncService;

    @Inject
    public AlbumArtLoader(DropboxSyncService syncService) {
        this.mSyncService = syncService;
    }

    @Override
    public DataFetcher<InputStream> getResourceFetcher(MediaMetadata mm, int width, int height) {

        return new DataFetcher<InputStream>() {

            private Subscription subscription;

            @Override
            public InputStream loadData(Priority priority) throws Exception {
                final ByteArrayOutputStream os = new ByteArrayOutputStream();

                LogHelper.d(TAG,
                        "getResourceFetcher - Starting async data load for album=",
                        mm.getString(MediaMetadata.METADATA_KEY_ALBUM),
                        " with dimensions (", width, "x", height, ")");

                mSyncService
                        .getAlbumArt(mm)
                        .single()
                        .subscribeOn(Schedulers.immediate())
                        .subscribe(byteArray -> {
                            try {
                                os.write(byteArray);
                            } catch (IOException e) {
                                // do nothing, output stream will be empty;
                                LogHelper.w(TAG, e,
                                        "getResourceFetcher - Exception while reading",
                                        " image byte array for album=",
                                        mm.getString(MediaMetadata.METADATA_KEY_ALBUM));
                            }
                        }, error -> {
                            LogHelper.w(TAG, error, "getResourceFetcher - Finished with error");
                            os.reset();
                        });

                LogHelper.d(TAG,
                        "getResourceFetcher - Finished async data load for album=",
                        mm.getString(MediaMetadata.METADATA_KEY_ALBUM),
                        " with dimensions (", width, "x", height, "). Total bytes returned: ",
                        os.size());

                return new ByteArrayInputStream(os.toByteArray());
            }

            @Override
            public void cleanup() {
                subscription = null;
            }

            @Override
            public String getId() {
                return new Key(mm).toString();
            }

            @Override
            public void cancel() {
                if (subscription != null && !subscription.isUnsubscribed()) {
                    subscription.unsubscribe();
                }

            }
        };

    }

    public static class Factory implements ModelLoaderFactory<MediaMetadata,InputStream> {

        @Override
        public ModelLoader<MediaMetadata, InputStream> build(Context context, GenericLoaderFactory factories) {

            KiteApplication application = (KiteApplication)context.getApplicationContext();

            return application.getComponent().albumArtLoader();
        }

        @Override
        public void teardown() {
            // Do nothing
        }
    }

    public static class Key implements com.bumptech.glide.load.Key {

        private String signature;

        public Key(MediaMetadata mm) {

            // Prefer album artist, try artist otherwise
            signature =
                    mm.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) != null?
                            mm.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST):
                            mm.getString(MediaMetadata.METADATA_KEY_ARTIST);

            signature = signature != null?signature+"/":"";

            signature +=
                    mm.getString(MediaMetadata.METADATA_KEY_ALBUM) != null?
                            mm.getString(MediaMetadata.METADATA_KEY_ALBUM):"";
        }

        @Override
        public String toString() {
            return signature;
        }

        @Override
        public boolean equals(Object o) {

            if (this == o) return true;

            if (o instanceof Key) {
                Key key = (Key) o;
                return signature.equals(key.signature);
            } else if (o instanceof String) {
                String signature = (String)o;
                return this.signature.equals(signature);
            }

            return false;
        }

        @Override
        public int hashCode() {
            return signature != null ? signature.hashCode() : 0;
        }

        @Override

        public void updateDiskCacheKey(MessageDigest messageDigest) throws UnsupportedEncodingException {
            messageDigest.update(signature.getBytes(STRING_CHARSET_NAME));
        }
    }
}
