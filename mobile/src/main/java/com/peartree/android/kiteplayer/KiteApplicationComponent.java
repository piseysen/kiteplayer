package com.peartree.android.kiteplayer;

import android.app.Application;
import android.support.annotation.Nullable;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.peartree.android.kiteplayer.model.AlbumArtLoader;
import com.peartree.android.kiteplayer.ui.ActionBarCastActivity;
import com.peartree.android.kiteplayer.ui.AuthActivity;
import com.peartree.android.kiteplayer.ui.MediaBrowserFragment;
import com.peartree.android.kiteplayer.utils.ImmutableFileLRUCache;

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
}
