/*
 * Copyright (c) 2015 Rafael Pereira
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 *
 *      https://mozilla.org/MPL/2.0/
 *
 */

package com.peartree.android.kiteplayer.utils;

import com.peartree.android.kiteplayer.database.DropboxDBEntry;

public class SongCacheHelper {

    public static final int[] LARGE_ALBUM_ART_DIMENSIONS = {480,800};
    public static final int[] SMALL_ALBUM_ART_DIMENSIONS = {128, 128};

    public static String makeLRUCacheFileName(DropboxDBEntry entry) {

        if (entry.isDir()) return null;

        String fileName = Long.toString(entry.getId());
        String fileExtension = entry.getFilename().substring(entry.getFilename().lastIndexOf("."));

        return fileName+fileExtension;
    }
}
