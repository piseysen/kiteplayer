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
package com.peartree.android.kiteplayer.ui;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.peartree.android.kiteplayer.R;
import com.peartree.android.kiteplayer.utils.PrefUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DrawerMenuContents {
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_ICON = "icon";

    private final ArrayList<Map<String, ?>> items;
    private final Runnable[] actions;
    private final Class[] activities;

    private final Activity mParentActivity;
    private final DropboxAPI<AndroidAuthSession> mDBApi;

    public DrawerMenuContents(Activity parentActivity, DropboxAPI<AndroidAuthSession> dbApi) {

        this.mParentActivity = parentActivity;
        this.mDBApi = dbApi;

        items = new ArrayList<>(4);
        actions = new Runnable[4];
        activities = new Class[4];

        activities[0] = MusicPlayerActivity.class;
        actions[0] = () -> replaceParentActivity(0);
        items.add(populateDrawerItem(mParentActivity.getString(R.string.drawer_allmusic_title),
            R.drawable.ic_allmusic_black_24dp));

        activities[1] = SettingsActivity.class;
        actions[1] = () -> replaceParentActivity(1);
        items.add(populateDrawerItem(mParentActivity.getString(R.string.settings),
                R.drawable.ic_settings_black_24dp));

        activities[2] = AuthActivity.class;
        actions[2] = () -> {
            mDBApi.getSession().unlink();
            PrefUtils.setDropboxAuthToken(mParentActivity,null);
            // TODO Delete user data
            replaceParentActivity(2);
        };
        items.add(populateDrawerItem(mParentActivity.getString(R.string.drawer_signout),R.drawable.ic_exit_to_app_black_24dp));
    }

    private void replaceParentActivity(int activityIndex) {
        Bundle extras = ActivityOptions.makeCustomAnimation(
                mParentActivity, R.anim.fade_in, R.anim.fade_out).toBundle();

        mParentActivity.startActivity(new Intent(mParentActivity, activities[activityIndex]), extras);
        mParentActivity.finish();
    }

    public List<Map<String, ?>> getItems() {
        return items;
    }

    public Runnable getActivity(int position) {
        return actions[position];
    }

    public int getPosition(Class activityClass) {
        for (int i=0; i< activities.length; i++) {
            if (activities[i].equals(activityClass)) {
                return i;
            }
        }
        return -1;
    }

    private Map<String, ?> populateDrawerItem(String title, int icon) {
        HashMap<String, Object> item = new HashMap<>();
        item.put(FIELD_TITLE, title);
        item.put(FIELD_ICON, icon);
        return item;
    }

}
