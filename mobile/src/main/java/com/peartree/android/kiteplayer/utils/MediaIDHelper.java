/*
 * Original work Copyright (C) 2014 The Android Open Source Project
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
 *
 * Modified work Copyright (c) 2015 Rafael Pereira
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 *
 *      https://mozilla.org/MPL/2.0/
 *
 */

package com.peartree.android.kiteplayer.utils;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

/**
 * Utility class to help on generating and parsing MediaIDs.
 * MediaIDs are of the form <categoryType>~<categoryValue>|<musicUniqueId>, to make it easy
 * to find the category (like genre) that a music was selected from, so we
 * can correctly build the playing queue. This is specially useful when
 * one music can appear in more than one list, like "by genre -> genre_1"
 * and "by artist -> artist_1".
 */

public class MediaIDHelper {

    // Media IDs used on browseable items of MediaBrowser
    public static final String MEDIA_ID_ROOT = "__ROOT__";
    public static final String MEDIA_ID_MUSICS_BY_SEARCH = "__BY_SEARCH__";

    private static final String CATEGORY_SEPARATOR = "~";
    private static final String LEAF_SEPARATOR = "|";

    public static String createMediaID(@Nullable String musicID, @NonNull String... categories) {

        String categoryHierarchy = TextUtils.join(CATEGORY_SEPARATOR,categories);

        if (musicID != null) {
            return categoryHierarchy + LEAF_SEPARATOR + musicID;
        } else {
            return categoryHierarchy;
        }
    }

    public static @NonNull String[] getHierarchy(String mediaID) {
        String prunedId = pruneLeaf(mediaID);
        return prunedId != null?prunedId.split(CATEGORY_SEPARATOR):new String[0];
    }

    public static String extractMusicIDFromMediaID(String mediaID) {

        return pickLeaf(mediaID);
    }

    public static String extractBrowserCategoryFromMediaID(String mediaID) {

        return takeRoot(mediaID);
    }

    public static String[] extractBrowseCategoryValueFromMediaID(String mediaID) {

        String prunedId = pruneRoot(pruneLeaf(mediaID));
        return prunedId != null?prunedId.split(CATEGORY_SEPARATOR):new String[0];
    }

    public static String getParentMediaID(String mediaID) {

        if (!isCategory(mediaID)) {
            return pruneLeaf(mediaID);
        } else {
            int lastCategoryPos = mediaID.lastIndexOf(CATEGORY_SEPARATOR);
            return lastCategoryPos>=0?mediaID.substring(0,lastCategoryPos):null;
        }
    }

    private static boolean isCategory(String mediaID) {
        return !mediaID.contains(LEAF_SEPARATOR);
    }

    @Nullable
    private static String pruneLeaf(String mediaID) {

        if (mediaID == null) return null;

        int leafPos = mediaID.indexOf(LEAF_SEPARATOR);
        return leafPos >= 0?mediaID.substring(0,leafPos):mediaID;
    }

    @Nullable
    private static String pruneRoot(String mediaID) {

        if (mediaID == null) return null;

        int rootSize = mediaID.indexOf(CATEGORY_SEPARATOR);
        return rootSize >= 0?mediaID.substring(rootSize+1):null;
    }

    @Nullable
    private static String pickLeaf(String mediaID) {

        if (mediaID == null) return null;

        int leafPos = mediaID.indexOf(LEAF_SEPARATOR);
        return leafPos >= 0?mediaID.substring(leafPos+1):null;
    }

    @Nullable
    private static String takeRoot(String mediaID) {

        String prunedId = pruneLeaf(mediaID);
        if (prunedId == null) return null;

        String[] branch = prunedId.split(CATEGORY_SEPARATOR,2);
        return branch.length >= 0?branch[0]:null;
    }
}
