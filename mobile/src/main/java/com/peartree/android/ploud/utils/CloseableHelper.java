package com.peartree.android.ploud.utils;

import java.io.Closeable;

public class CloseableHelper {

    private static final String TAG = LogHelper.makeLogTag(CloseableHelper.class);

    public static final void close(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception e) {
                LogHelper.w(TAG,"close - Failed closing "+c.toString(),e);
            }
        }
    }
}
