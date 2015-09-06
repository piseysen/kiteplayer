package com.peartree.android.kiteplayer.dropbox;

import android.app.Application;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.MediaMetadataRetriever;
import android.support.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxUnlinkedException;
import com.peartree.android.kiteplayer.R;
import com.peartree.android.kiteplayer.database.DropboxDBEntry;
import com.peartree.android.kiteplayer.database.DropboxDBEntryDAO;
import com.peartree.android.kiteplayer.database.DropboxDBSong;
import com.peartree.android.kiteplayer.database.DropboxDBSongDAO;
import com.peartree.android.kiteplayer.model.AlbumArtLoader;
import com.peartree.android.kiteplayer.model.MusicProvider;
import com.peartree.android.kiteplayer.utils.DropboxHelper;
import com.peartree.android.kiteplayer.utils.ImmutableFileLRUCache;
import com.peartree.android.kiteplayer.utils.LogHelper;
import com.peartree.android.kiteplayer.utils.NetworkHelper;
import com.peartree.android.kiteplayer.utils.PrefUtils;
import com.peartree.android.kiteplayer.utils.SongCacheHelper;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

import static com.peartree.android.kiteplayer.model.MusicProvider.FLAG_SONG_METADATA_IMAGE;
import static com.peartree.android.kiteplayer.model.MusicProvider.FLAG_SONG_METADATA_TEXT;
import static com.peartree.android.kiteplayer.model.MusicProvider.FLAG_SONG_PLAY_READY;
import static com.peartree.android.kiteplayer.utils.SongCacheHelper.LARGE_ALBUM_ART_DIMENSIONS;

@Singleton
public class DropboxSyncService {

    private static final String TAG = LogHelper.makeLogTag(DropboxSyncService.class);

    private Context mApplicationContext;

    private DropboxAPI<AndroidAuthSession> mDropboxApi;

    private DropboxDBEntryDAO mEntryDao;
    private DropboxDBSongDAO mSongDao;
    private ImmutableFileLRUCache mCachedSongs;

    private final PublishSubject<AsyncCacheRequest> mMetadataSyncQueue;

    @Inject
    public DropboxSyncService(Application application,
                              DropboxAPI<AndroidAuthSession> dbApi,
                              DropboxDBEntryDAO entryDao,
                              DropboxDBSongDAO songDao,
                              @Nullable ImmutableFileLRUCache cachedSongs) {

        this.mApplicationContext = application.getApplicationContext();
        this.mDropboxApi = dbApi;
        this.mEntryDao = entryDao;
        this.mSongDao = songDao;
        this.mCachedSongs = cachedSongs;

        this.mMetadataSyncQueue = PublishSubject.create(); // No need to serialize

        // Subject works as an event bus through which entries for which metadata is missing
        // are processed asynchronously
        this.mMetadataSyncQueue
                .distinct(request -> request)
                .window(1)
                .onBackpressureBuffer()
                .observeOn(Schedulers.newThread())
                .subscribe(this::synchronizeSongDB);
    }

    public Observable<Long> synchronizeEntryDB() {

        return Observable.create(subscriber -> {

                    LogHelper.d(TAG,
                            "synchronizeEntryDB - On thread: ",
                            Thread.currentThread().getName());

                    DropboxAPI.DeltaPage<DropboxAPI.Entry> deltaPage;
                    DropboxAPI.Entry dbEntry;
                    DropboxDBEntry entry;
                    String deltaCursor;

                    int pageCounter = 1;

                    deltaCursor = PrefUtils.getDropboxDeltaCursor(mApplicationContext);

                    try {

                        do {

                            deltaPage = mDropboxApi.delta(deltaCursor);

                            LogHelper.d(TAG,
                                    "synchronizeEntryDB - Processing delta page #", (pageCounter++),
                                    " with size=", deltaPage.entries.size());

                            for (DropboxAPI.DeltaEntry<DropboxAPI.Entry> deltaEntry : deltaPage.entries) {

                                dbEntry = deltaEntry.metadata;

                                if (dbEntry == null || dbEntry.isDeleted) {
                                    mEntryDao.deleteTreeByAncestorDir(deltaEntry.lcPath);
                                    LogHelper.d(TAG,
                                            "synchronizeEntryDB - Deleted entry path=", deltaEntry.lcPath);

                                    continue;
                                }

                                entry = new DropboxDBEntry();

                                entry.setIsDir(dbEntry.isDir);
                                entry.setRoot(dbEntry.root);
                                entry.setParentDir(dbEntry.parentPath());
                                entry.setFilename(dbEntry.fileName());

                                entry.setRev(dbEntry.rev);
                                entry.setHash(dbEntry.hash);
                                entry.setModified(dbEntry.modified);
                                entry.setClientMtime(dbEntry.clientMtime);

                                entry.setMimeType(dbEntry.mimeType);
                                entry.setIcon(dbEntry.icon);
                                entry.setThumbExists(dbEntry.thumbExists);

                                long id = mEntryDao.insertOrReplace(entry);

                                LogHelper.d(TAG,
                                        "synchronizeEntryDB - Saved entry for path=",
                                        dbEntry.parentPath(), " with id=", id);

                                subscriber.onNext(id);
                            }

                            deltaCursor = deltaPage.cursor;
                            PrefUtils.setDropboxDeltaCursor(mApplicationContext, deltaPage.cursor);

                        } while (deltaPage.hasMore);

                        subscriber.onCompleted();
                        LogHelper.d(TAG, "synchronizeEntryDB - Finished successfully");

                    } catch (DropboxException e) {

                        DropboxHelper.unlinkSessionIfUnlinkedException(mDropboxApi.getSession(), e);

                        subscriber.onError(e);
                        LogHelper.e(TAG, e, "synchronizeEntryDB - Finished with error");

                    }

                }
        );
    }

    public Observable<DropboxDBEntry> fillSongMetadata(
            Observable<DropboxDBEntry> entries,
            int cacheFlags) {

        return entries.map(entry -> {

            LogHelper.d(TAG,
                    "fillSongMetadata - Started for path=" + entry.getFullPath(),
                    "on thread: ", Thread.currentThread().getName());

            if (entry.isDir()) {
                LogHelper.d(TAG, "fillSongMetadata - Found directory. Nothing to do.");
                return entry;
            }

            DropboxDBSong song = mSongDao.findByEntryId(entry.getId());

            if (song == null) {
                song = new DropboxDBSong();
                song.setEntryId(entry.getId());
            }

            entry.setSong(song);

            if (!song.hasLatestMetadata() &&
                    (cacheFlags & (FLAG_SONG_METADATA_TEXT | FLAG_SONG_METADATA_IMAGE)) > 0) {
                LogHelper.d(TAG,
                        "fillSongMetadata - Queueing metadata sync for path=", entry.getFullPath());
                mMetadataSyncQueue.onNext(new AsyncCacheRequest(entry, cacheFlags));
            }

            if ((cacheFlags & FLAG_SONG_PLAY_READY) == FLAG_SONG_PLAY_READY &&
                    getCachedSongFile(entry) == null &&
                    NetworkHelper.canStream(mApplicationContext)) {

                refreshDownloadURL(entry);

            }

            return entry;

        });
    }

    private void synchronizeSongDB(Observable<AsyncCacheRequest> cacheRequestObservable) {

        cacheRequestObservable
                .subscribeOn(Schedulers.newThread())
                .subscribe(request -> synchronizeSongDB(request.getEntry(), request.getCacheFlags()));

    }

    private void synchronizeSongDB(DropboxDBEntry entry, int cacheFlag) {

        LogHelper.d(TAG,
                "synchronizeSongDB - Started for path=", entry.getFullPath(),
                " on thread: ", Thread.currentThread().getName());

        DropboxDBSong song;
        File cachedSongFile;

        boolean updateSongInDB = false;

        song = entry.getSong();

        if (song == null) {
            LogHelper.d(TAG,
                    "synchronizeSongDB - Song is null for path=", entry.getFullPath(),
                    ". Ignoring...");
            return;
        }

        if (song.getEntryId() != entry.getId()) {
            LogHelper.d(TAG, "synchronizeSongDB - Song has mismatching ID for path=",
                    entry.getFullPath(), ". Ignoring...");
            return;
        }

        cachedSongFile = getOrCacheSongFile(entry, -1, cacheFlag);
        if (cachedSongFile == null && (cacheFlag & FLAG_SONG_PLAY_READY) == FLAG_SONG_PLAY_READY) {

            LogHelper.d(TAG,
                    "synchronizeSongDB - Song not cached. Attempting to refresh URL for path=",
                    entry.getFullPath());

            refreshDownloadURL(entry);
            updateSongInDB = true;

        }

        MediaMetadataRetriever retriever = null;

        // If song metadata is current, skip
        // Unless image is required and song is readily available in cache
        if (!song.hasLatestMetadata() ||
                ((cacheFlag & FLAG_SONG_METADATA_IMAGE) == FLAG_SONG_METADATA_IMAGE && cachedSongFile != null)) {

            LogHelper.d(TAG,
                    "synchronizeSongDB - Metadata retriever required for path=",
                    entry.getFullPath());

            retriever = initializeMediaMetadataRetriever(entry, cachedSongFile);
        }

        if (retriever != null &&
                !song.hasLatestMetadata() &&
                (cacheFlag & FLAG_SONG_METADATA_TEXT) == FLAG_SONG_METADATA_TEXT) {

            LogHelper.d(TAG,
                    "synchronizeSongDB - Updating text metadata for path= ", entry.getFullPath());

            song.setAlbum(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));
            song.setArtist(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));
            song.setGenre(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE));
            song.setTitle(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));

            String tmpString;

            if ((tmpString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) != null) {
                try {
                    song.setDuration(Long.valueOf(tmpString));
                } catch (NumberFormatException e) {
                    LogHelper.w(TAG, e,
                            "synchronizeSongDB - Invalid duration=", tmpString,
                            " for path=", entry.getFullPath());
                }
            }

            if ((tmpString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)) != null) {
                try {
                    song.setTrackNumber(Integer.valueOf(tmpString));
                } catch (NumberFormatException e) {
                    LogHelper.w(TAG, e, "synchronizeSongDB - Invalid track number=", tmpString,
                            " for path=", entry.getFullPath());
                }
            }

            if ((tmpString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS)) != null) {
                try {
                    song.setTotalTracks(Integer.valueOf(tmpString));
                } catch (NumberFormatException e) {
                    LogHelper.w(TAG, "synchronizeSongDB - Invalid number of tracks=", tmpString,
                            " for path=", entry.getFullPath());
                }
            }

            song.setHasLatestMetadata(true);
            updateSongInDB = true;

        }

        if (retriever != null) {

            LogHelper.d(TAG, "synchronizeSongDB - Updating image data for path=", entry.getFullPath());

            // Cache album art
            try {

                byte[] embeddedPicture = retriever.getEmbeddedPicture();

                if (embeddedPicture != null && embeddedPicture.length > 0) {
                    Glide
                            .with(mApplicationContext)
                            .load(retriever.getEmbeddedPicture())
                            .fallback(R.drawable.ic_album_art)
                            .signature(new AlbumArtLoader.Key(MusicProvider.buildMetadataFromDBEntry(entry, cachedSongFile, false)))
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(LARGE_ALBUM_ART_DIMENSIONS[0], LARGE_ALBUM_ART_DIMENSIONS[1])
                            .get();

                    LogHelper.d(TAG, "synchronizeSongDB - Cached album art image for path=", entry.getFullPath());

                } else {
                    song.setHasValidAlbumArt(false);
                }

            } catch (InterruptedException | ExecutionException e) {
                song.setHasValidAlbumArt(false);
                LogHelper.w(TAG, e,
                        "synchronizeSongDB - Failed to cache album art image for path=",
                        entry.getFullPath());
            }
        }

        if (updateSongInDB) {
            long id = mSongDao.insertOrReplace(song);
            LogHelper.d(TAG,
                    "synchronizeSongDB - Updated song for path=", entry.getFullPath(),
                    " with id=", id);
        }

    }

    private void refreshDownloadURL(DropboxDBEntry entry) {

        DropboxDBSong song = entry.getSong();
        DropboxAPI.DropboxLink link;

        boolean hasValidDownloadURL =
                !(song.getDownloadURL() == null ||
                        song.getDownloadURLExpiration() == null ||
                        song.getDownloadURLExpiration().compareTo(new Date()) <= 0);

        if (!hasValidDownloadURL) {

            try {

                link = mDropboxApi.media(entry.getFullPath(), false);

                LogHelper.d(TAG,
                        "refreshDownloadURL - Generated new download URL for path=",
                        entry.getFullPath());

                song.setDownloadURL(new URL(link.url));
                song.setDownloadURLExpiration(link.expires);

            } catch (MalformedURLException | DropboxException e) {

                if (e instanceof DropboxException) {
                    DropboxHelper.unlinkSessionIfUnlinkedException(mDropboxApi.getSession(),
                            (DropboxException) e);
                }
                song.setDownloadURL(null);
                song.setDownloadURLExpiration(null);
                LogHelper.d(TAG, e,
                        "refreshDownloadURL - Failed to refresh download URL for path=",
                        entry.getFullPath());

            }
        }

    }

    private
    @Nullable
    File downloadSongDataIntoCache(DropboxDBEntry entry) {

        LogHelper.d(TAG,
                "downloadSongDataIntoCache - Starting download for path=", entry.getFullPath());

        // Null check "formality"
        if (mCachedSongs == null) return null;

        // Skip download of not allowed by user
        if (!NetworkHelper.canSync(mApplicationContext)) {
            return null;
        }

        File newCacheFile = mCachedSongs.newFile(
                SongCacheHelper.makeLRUCacheFileName(entry),
                cacheStream -> mDropboxApi.getFile(entry.getFullPath(), entry.getRev(), cacheStream, null));

        if (newCacheFile != null) {
            LogHelper.d(TAG,
                    "downloadSongDataIntoCache - Finished download for path=", entry.getFullPath());
        } else {
            LogHelper.w(TAG,
                    "downloadSongDataIntoCache - Failed download for path=", entry.getFullPath());
        }

        return newCacheFile;
    }

    private
    @Nullable
    MediaMetadataRetriever initializeMediaMetadataRetriever(DropboxDBEntry entry, @Nullable File cachedSongFile) {

        DropboxDBSong song = entry.getSong();

        MediaMetadataRetriever retriever;
        retriever = new MediaMetadataRetriever();

        try {
            if (cachedSongFile != null) {

                LogHelper.d(TAG,
                        "initializeMediaMetadataRetriever - Initializing retriever for path=",
                        entry.getFullPath(), " with cachedSongFile=", cachedSongFile.getPath());

                retriever.setDataSource(cachedSongFile.getPath());

            } else if (song != null && song.getDownloadURL() != null &&
                    NetworkHelper.canSync(mApplicationContext)) {

                LogHelper.d(TAG,
                        "initializeMediaMetadataRetriever - Initializing retriever for path=",
                        entry.getFullPath(), " with downloadURL=", song.getDownloadURL());

                retriever.setDataSource(
                        song.getDownloadURL().toString(),
                        new HashMap<String, String>());
            } else {
                return null;
            }
        } catch (RuntimeException e) {
            LogHelper.w(TAG, e,
                    "initializeMediaMetadataRetriever - Failed to initialize retriever for path=",
                    entry.getFullPath());
            return null;
        }

        LogHelper.d(TAG,
                "initializeMediaMetadataRetriever - Retriever initialized for: path=",
                entry.getFullPath());

        return retriever;
    }

    public Observable<byte[]> getAlbumArt(MediaMetadata mm) {

        return Observable.create(subscriber -> {

            LogHelper.d(TAG,
                    "getAlbumArt - Starting subscriber on thread=",
                    Thread.currentThread().getName());

            DropboxDBEntry entry =
                    mEntryDao.findById(
                            Long.parseLong(mm.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)));
            DropboxDBSong song = entry != null ? mSongDao.findByEntryId(entry.getId()) : null;


            MediaMetadataRetriever retriever;
            if (entry != null && song != null && song.hasValidAlbumArt()) {

                retriever = initializeMediaMetadataRetriever(entry, getCachedSongFile(entry));

                if (retriever != null) {

                    byte[] bitmapByteArray = retriever.getEmbeddedPicture();

                    if (bitmapByteArray != null && bitmapByteArray.length > 0) {

                        subscriber.onNext(bitmapByteArray);

                    } else {

                        song.setHasValidAlbumArt(false);
                        mSongDao.insertOrReplace(song);

                        subscriber.onError(new Exception("File contains no image data."));
                        LogHelper.w(TAG,
                                "getAlbumArt - Finished with error for source=",
                                mm.getString(MusicProvider.CUSTOM_METADATA_TRACK_SOURCE));
                        return;
                    }
                }
            }

            subscriber.onCompleted();
            LogHelper.d(TAG,
                    "getAlbumArt - Finished succesfully for source=",
                    mm.getString(MusicProvider.CUSTOM_METADATA_TRACK_SOURCE));

        });

    }

    public
    @Nullable
    File getCachedSongFile(DropboxDBEntry entry) {
        return getCachedSongFile(entry, 0);
    }


    public
    @Nullable
    File getCachedSongFile(DropboxDBEntry entry, long timeout) {

        if (mCachedSongs == null) return null;

        return mCachedSongs.get(SongCacheHelper.makeLRUCacheFileName(entry), timeout);
    }

    public
    @Nullable
    File getOrCacheSongFile(DropboxDBEntry entry, long timeout, int cacheFlag) {

        if (mCachedSongs == null) return null;

        File cachedSongFile =
                mCachedSongs.get(SongCacheHelper.makeLRUCacheFileName(entry), timeout);

        if (cachedSongFile == null) {

            final DropboxDBSong song = entry.getSong();

            final boolean isCheapNetwork = !NetworkHelper.isNetworkMetered(mApplicationContext);
            final boolean isMetadataNeeded = (!song.hasLatestMetadata() &&
                    (cacheFlag & FLAG_SONG_METADATA_TEXT) == FLAG_SONG_METADATA_TEXT);
            final boolean willSongBePlayed = (cacheFlag & FLAG_SONG_PLAY_READY) == FLAG_SONG_PLAY_READY;

            if ((isCheapNetwork && (isMetadataNeeded || willSongBePlayed)) ||
                    (isMetadataNeeded && willSongBePlayed)) {

                LogHelper.d(TAG,
                        "getOrCacheSongFile - Attempting to save to cache for path=",
                        entry.getFullPath());

                cachedSongFile = downloadSongDataIntoCache(entry);

            }
        }

        return cachedSongFile;
    }

    private class AsyncCacheRequest {
        private DropboxDBEntry entry;
        private int cacheFlags;

        public AsyncCacheRequest(DropboxDBEntry entry, int cacheFlags) {
            this.entry = entry;
            this.cacheFlags = cacheFlags;
        }

        public DropboxDBEntry getEntry() {
            return entry;
        }

        public int getCacheFlags() {
            return cacheFlags;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof AsyncCacheRequest) {
                AsyncCacheRequest that = (AsyncCacheRequest) o;
                return getEntry().getId() == that.getEntry().getId() && getCacheFlags() == that.getCacheFlags();
            }

            return false;
        }
    }

}
