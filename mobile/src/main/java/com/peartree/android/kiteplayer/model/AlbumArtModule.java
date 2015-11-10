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

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.load.engine.cache.ExternalCacheDiskCacheFactory;
import com.bumptech.glide.module.GlideModule;
import com.peartree.android.kiteplayer.utils.LogHelper;
import com.peartree.android.kiteplayer.utils.PrefUtils;

import java.io.File;
import java.io.InputStream;

public class AlbumArtModule implements GlideModule {

    private static final String TAG = LogHelper.makeLogTag(AlbumArtModule.class);

    @Override
    public void applyOptions(Context context, GlideBuilder builder) {

        // Allocates 10% of user defined cache size to album art
        int albumArtCacheSizeInMBs =
                Math.round(Integer.parseInt(PrefUtils.getCacheSize(context)) * 0.1f);

        builder.setDiskCache(
                new ExternalCacheDiskCacheFactory(
                        context,
                        "albumartcache",
                        albumArtCacheSizeInMBs * 1024 * 1024));
    }

    @Override
    public void registerComponents(Context context, Glide glide) {

        glide.register(MediaMetadata.class, InputStream.class,
                new AlbumArtLoader.Factory());

    }
}
