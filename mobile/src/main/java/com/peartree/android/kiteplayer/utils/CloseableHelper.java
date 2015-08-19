package com.peartree.android.kiteplayer.utils;

import java.io.Closeable;

public class CloseableHelper {

    private static final String TAG = LogHelper.makeLogTag(CloseableHelper.class);

    public static final void closeQuietly(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception e) {
                LogHelper.w(TAG,"closeQuietly - Failed closing "+c.toString(),e);
            }
        }
    }
}
