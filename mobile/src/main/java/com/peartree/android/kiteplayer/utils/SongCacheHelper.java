package com.peartree.android.kiteplayer.utils;

import android.content.Context;

import com.peartree.android.kiteplayer.database.DropboxDBEntry;

import java.io.File;

public class SongCacheHelper {

    public static final int[] LARGE_ALBUM_ART_DIMENSIONS = {480,800};

    // TODO Check where small album art should be used
    public static final int[] SMALL_ALBUM_ART_DIMENSIONS = {128, 128};


    public static String makeLRUCacheDirectoryPath(Context context) {
        File diskLRUCacheDir = new File(context.getApplicationContext().getExternalCacheDir().getPath()+File.separator+"lrucache");

        if (!diskLRUCacheDir.exists())
            diskLRUCacheDir.mkdir();

        return diskLRUCacheDir.getPath();
    }

    public static String makeLRUCacheFileName(DropboxDBEntry entry) {

        if (entry.isDir()) return null;

        String fileName = Long.toString(entry.getId());
        String fileExtension = entry.getFilename().substring(entry.getFilename().lastIndexOf("."));

        return fileName+fileExtension;
    }
}
