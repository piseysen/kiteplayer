/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.peartree.android.kiteplayer.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Util for setting and accessing {@link SharedPreferences} for the current application.
 */
public class PrefUtils {

    private static final String PREF_NAMESPACE = "com.peartree.android.kiteplayer.utils.PREFS";
    private static final String FTU_SHOWN = "ftu_shown";
    private static final String DROPBOX_AUTH_TOKEN = "db_token";
    private static final String DROPBOX_DELTA_CURSOR = "db_delta_cursor";

    public static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(PREF_NAMESPACE, Context.MODE_PRIVATE);
    }

    public static void setFtuShown(Context context, boolean shown) {
        getPreferences(context).edit().putBoolean(FTU_SHOWN, shown).apply();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isFtuShown(Context context) {
        return getPreferences(context).getBoolean(FTU_SHOWN, false);
    }

    public static void setDropboxAuthToken(Context context, String token) {
        getPreferences(context).edit().putString(DROPBOX_AUTH_TOKEN, token).apply();
    }

    public static String getDropboxAuthToken(Context context) {
        return getPreferences(context).getString(DROPBOX_AUTH_TOKEN, null);
    }

    public static void setDropboxDeltaCursor(Context context, String cursor) {
        getPreferences(context).edit().putString(DROPBOX_DELTA_CURSOR, cursor).apply();
    }

    public static String getDropboxDeltaCursor(Context context) {
        return getPreferences(context).getString(DROPBOX_DELTA_CURSOR, null);
    }
}
