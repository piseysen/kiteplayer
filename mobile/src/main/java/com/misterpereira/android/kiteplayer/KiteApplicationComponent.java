/*
 * Copyright (c) 2015 Rafael Pereira
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 *     https://mozilla.org/MPL/2.0/.
 */

package com.misterpereira.android.kiteplayer;

import android.app.Application;
import android.support.annotation.Nullable;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.google.android.gms.analytics.Tracker;
import com.misterpereira.android.kiteplayer.model.AlbumArtLoader;
import com.misterpereira.android.kiteplayer.ui.ActionBarCastActivity;
import com.misterpereira.android.kiteplayer.ui.AuthActivity;
import com.misterpereira.android.kiteplayer.ui.MediaBrowserFragment;
import com.misterpereira.android.kiteplayer.utils.ImmutableFileLRUCache;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = KiteApplicationModule.class)
public interface KiteApplicationComponent {

    void inject(KiteApplication application);
    void inject(MusicService musicService);

    void inject(AuthActivity activity);
    void inject(ActionBarCastActivity activity);
    void inject(MediaBrowserFragment fragment);

    Application application();
    DropboxAPI<AndroidAuthSession> dropboxAPI();
    AlbumArtLoader albumArtLoader();

    @Nullable
    ImmutableFileLRUCache cachedSongs();

    Tracker gaTracker();
}
