package com.peartree.android.ploud;

import android.app.Application;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.jakewharton.disklrucache.DiskLruCache;
import com.peartree.android.ploud.model.AlbumArtLoader;
import com.peartree.android.ploud.ui.ActionBarCastActivity;
import com.squareup.okhttp.OkHttpClient;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = PloudApplicationModule.class)
public interface PloudApplicationComponent {

    void inject(PloudApplication application);
    void inject(MusicService musicService);

    // TODO Determine best java type for argument
    void inject(ActionBarCastActivity activity);

    Application application();
    VideoCastManager videoCastManager();
    DropboxAPI<AndroidAuthSession> dropboxAPI();
    AlbumArtLoader albumArtLoader();
    DiskLruCache cachedSongs();
    OkHttpClient httpClient();
}
