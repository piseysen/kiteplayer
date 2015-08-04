package com.peartree.android.ploud.model;

import android.content.Context;
import android.media.MediaMetadata;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.module.GlideModule;
import com.peartree.android.ploud.utils.LogHelper;

import java.io.InputStream;

public class AlbumArtModule implements GlideModule {

    private static final String TAG = LogHelper.makeLogTag(AlbumArtModule.class);

    @Override
    public void applyOptions(Context context, GlideBuilder builder) {

    }

    @Override
    public void registerComponents(Context context, Glide glide) {

        glide.register(MediaMetadata.class, InputStream.class,
                new AlbumArtLoader.Factory());

    }
}
