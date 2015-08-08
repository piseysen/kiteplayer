package com.peartree.android.ploud.utils;

import android.content.Context;

import com.peartree.android.ploud.database.DropboxDBEntry;
import com.peartree.android.ploud.database.DropboxDBSong;

import java.io.File;

public class SongCacheHelper {

    public static String getDiskLRUCachePath(Context context) {
        File diskLRUCacheDir = new File(context.getApplicationContext().getExternalCacheDir().getPath()+File.separator+"lrucache");

        if (!diskLRUCacheDir.exists())
            diskLRUCacheDir.mkdir();

        return diskLRUCacheDir.getPath();
    }

    public static String getDiskLRUCacheKey(DropboxDBEntry entry) {
        return Long.toString(entry.getId());
    }

    public static String getDiskLRUCacheKey(DropboxDBSong song) {
        return Long.toString(song.getEntryId());
    }

    public static String getFileCachePath(Context context) {
        File fileCacheDir = new File(context.getApplicationContext().getCacheDir().getPath()+File.separator+"songs");

        if (!fileCacheDir.exists())
            fileCacheDir.mkdir();

        return fileCacheDir.getPath();
    }

    public static String getFileCacheName(DropboxDBEntry entry) {
        String fileName = getDiskLRUCacheKey(entry);
        String fileExtension = entry.getFilename().substring(entry.getFilename().lastIndexOf("."));

        return fileName+fileExtension;
    }
}
