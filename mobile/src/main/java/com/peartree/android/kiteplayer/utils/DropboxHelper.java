package com.peartree.android.kiteplayer.utils;

import android.support.annotation.Nullable;

import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxUnlinkedException;

public class DropboxHelper {

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
}
