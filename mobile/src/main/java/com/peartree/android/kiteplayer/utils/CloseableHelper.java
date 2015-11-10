/*
 * Copyright (c) 2015 Rafael Pereira
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 *     https://mozilla.org/MPL/2.0/.
 */

package com.peartree.android.kiteplayer.utils;

import java.io.Closeable;

public class CloseableHelper {

    private static final String TAG = LogHelper.makeLogTag(CloseableHelper.class);

    public static final void closeQuietly(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception e) {
                LogHelper.w(TAG, e,
                        "closeQuietly - Failed closing ", c);
            }
        }
    }
}
