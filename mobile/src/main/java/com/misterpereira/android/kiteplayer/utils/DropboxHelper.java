/*
 * Copyright (c) 2015 Rafael Pereira
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 *
 *      https://mozilla.org/MPL/2.0/
 *
 */

package com.misterpereira.android.kiteplayer.utils;

import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxUnlinkedException;

public class DropboxHelper {

    private static final String DROPBOX_SEPARATOR = "/";
    private static final String DROPBOX_SEPARATOR_REGEX = "\\/";

    public static void unlinkSessionIfUnlinkedException(@Nullable AndroidAuthSession session, DropboxException dbe) {
        if (dbe instanceof DropboxUnlinkedException) {
            if (session != null) {
                session.unlink();
            }
        }
    }

    public static boolean isUnlinked(AndroidAuthSession session) {
        return session == null || !session.isLinked();
    }

    public static String makeDropboxPath(String filename, String... path) {
        String dropboxPath = TextUtils.join(DROPBOX_SEPARATOR,path);
        if (filename != null) {
            dropboxPath += DROPBOX_SEPARATOR + filename;
        }
        return DROPBOX_SEPARATOR + dropboxPath;
    }

    public static String[] splitPath(String path) {
        if (path == null || DROPBOX_SEPARATOR.equals(path)) {
            return new String[0];
        }

        int startPos = path.startsWith(DROPBOX_SEPARATOR)?1:0;
        int endPos = path.endsWith(DROPBOX_SEPARATOR)?path.length()-1:path.length();

        return TextUtils.split(path.substring(startPos,endPos), DROPBOX_SEPARATOR_REGEX);
    }

    public static String toCategoryMediaID(String categoryType, String path, String directory) {
        String[] splitPath = DropboxHelper.splitPath(path);

        String[] mediaIDCategories = new String[splitPath.length+2];
        mediaIDCategories[0] = categoryType;
        mediaIDCategories[splitPath.length+1] = directory;
        System.arraycopy(splitPath,0,mediaIDCategories,1,splitPath.length);

        return MediaIDHelper.createMediaID(null, mediaIDCategories);
    }

    public static String toMusicMediaID(String categoryType, String path, String musicID) {
        String[] splitPath = DropboxHelper.splitPath(path);

        String[] mediaIDCategories = new String[splitPath.length+1];
        mediaIDCategories[0] = categoryType;
        System.arraycopy(splitPath,0,mediaIDCategories,1,splitPath.length);

        return MediaIDHelper.createMediaID(musicID, mediaIDCategories);
    }
}
