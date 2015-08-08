package com.peartree.android.ploud.dropbox;

import android.app.Application;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.MediaMetadataRetriever;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.jakewharton.disklrucache.DiskLruCache;
import com.peartree.android.ploud.R;
import com.peartree.android.ploud.database.DropboxDBEntry;
import com.peartree.android.ploud.database.DropboxDBEntryDAO;
import com.peartree.android.ploud.database.DropboxDBSong;
import com.peartree.android.ploud.database.DropboxDBSongDAO;
import com.peartree.android.ploud.model.AlbumArtLoader;
import com.peartree.android.ploud.model.MusicProvider;
import com.peartree.android.ploud.utils.CloseableHelper;
import com.peartree.android.ploud.utils.LogHelper;
import com.peartree.android.ploud.utils.PrefUtils;
import com.peartree.android.ploud.utils.SongCacheHelper;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

@Singleton
public class DropboxSyncService {

    private static final String TAG = LogHelper.makeLogTag(DropboxSyncService.class);

    private Context mApplicationContext;

    private DropboxAPI<AndroidAuthSession> mDropboxApi;
    private OkHttpClient mHttpClient;

    private DropboxDBEntryDAO mEntryDao;
    private DropboxDBSongDAO mSongDao;
    private DiskLruCache mCachedSongs;

    private final PublishSubject<DropboxDBEntry> mMetadataSyncQueue;

    @Inject
    public DropboxSyncService(Application application,
                              DropboxAPI<AndroidAuthSession> dbApi,
                              OkHttpClient httpClient,
                              DropboxDBEntryDAO entryDao,
                              DropboxDBSongDAO songDao,
                              DiskLruCache cachedSongs) {

        this.mApplicationContext = application.getApplicationContext();
        this.mDropboxApi = dbApi;
        this.mHttpClient = httpClient;
        this.mEntryDao = entryDao;
        this.mSongDao = songDao;
        this.mCachedSongs = cachedSongs;

        this.mMetadataSyncQueue = PublishSubject.create(); // No need to serialize

        // Subject works as an event bus through which entries for which metadata is missing
        // are processed asynchronously
        this.mMetadataSyncQueue
                .distinct(entry -> entry.getId())
                .window(1)
                .onBackpressureBuffer()
                .observeOn(Schedulers.newThread())
                // TODO Convert to asynchronous/parallel processing
                .subscribe(this::synchronizeSongDB);
    }

    public Observable<Long> synchronizeEntryDB() {

        LogHelper.d(TAG, "Dropbox auth status: " + mDropboxApi.getSession().isLinked());

        return Observable.create(subscriber -> {

                    LogHelper.d(TAG, "synchronizeEntryDB - Subscribing on thread: " + Thread.currentThread().getName());

                    DropboxAPI.DeltaPage<DropboxAPI.Entry> deltaPage;
                    DropboxAPI.Entry dbEntry;
                    DropboxDBEntry entry;
                    String deltaCursor;

                    int pageCounter = 1;

                    deltaCursor = PrefUtils.getDropboxDeltaCursor(mApplicationContext);

                    try {

                        do {

                            deltaPage = mDropboxApi.delta(deltaCursor);

                            LogHelper.d(TAG, "synchronizeEntryDB - Processing delta page #" + (pageCounter++) + " size: " + deltaPage.entries.size());

                            for (DropboxAPI.DeltaEntry<DropboxAPI.Entry> deltaEntry : deltaPage.entries) {

                                dbEntry = deltaEntry.metadata;

                                if (dbEntry == null || dbEntry.isDeleted) {
                                    mEntryDao.deleteTreeByAncestorDir(deltaEntry.lcPath);
                                    LogHelper.d(TAG, "synchronizeEntryDB - Deleted entry for: " + deltaEntry.lcPath);

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

                                LogHelper.d(TAG, "synchronizeEntryDB - Saved entry for: " + dbEntry.parentPath() + " with id: " + id);

                                subscriber.onNext(id);
                            }

                            deltaCursor = deltaPage.cursor;
                            PrefUtils.setDropboxDeltaCursor(mApplicationContext, deltaPage.cursor);

                        } while (deltaPage.hasMore);

                        subscriber.onCompleted();
                        LogHelper.d(TAG, "synchronizeEntryDB - Finished successfully");

                    } catch (DropboxException dbe) {

                        subscriber.onError(dbe);
                        LogHelper.e(TAG, "synchronizeEntryDB - Finished with error", dbe);

                    }

                }
        );
    }

    public Observable<DropboxDBEntry> fillSongMetadata(Observable<DropboxDBEntry> entries, boolean liveSourceRequired) {
        return entries.map(entry -> {

            // If source is dead, entry will be queued for asynchronous update
            boolean replaceDeadSource = true;

            boolean liveSourceFound = true;

            LogHelper.d(TAG, "fillSongMetadata - Started for: " + entry.getFullPath() + ". Observing on thread: " + Thread.currentThread().getName());

            if (entry.isDir()) {
                LogHelper.d(TAG, "fillSongMetadata - Found directory. Nothing to do.");
                return entry;
            }

            DropboxDBSong song;

            try {
                song = mSongDao.findByEntryId(entry.getId());
            } catch (MalformedURLException | ParseException e) {
                LogHelper.w(TAG, "fillSongMetadata - Bad song data in the database for: " + entry.getFullPath() + ". Replacing...");
                song = null; // Proceed to replace song in DB
            }

            if (song != null) {

                if (liveSourceRequired && (!hasValidCachedData(song) || !hasValidDownloadURL(song))) {

                    // Link missing or bad
                    liveSourceFound = false;

                    song.setDownloadURL(null);
                    song.setDownloadURLExpiration(null);

                    if (replaceDeadSource) {
                        mMetadataSyncQueue.onNext(entry);
                        LogHelper.d(TAG, "fillSongMetadata - Metadata not found for: " + entry.getFullPath() + ". Queueing request for synchronization.");
                    }
                }

                entry.setSong(song);
                LogHelper.d(TAG, "fillSongMetadata - Found song metadata for: " + entry.getFullPath());

            } else {

                liveSourceFound = false;

                // Song metadata was never synchronized
                // Retrieve metadata asynchronously

                song = new DropboxDBSong();
                song.setEntryId(entry.getId());
                entry.setSong(song);

                mMetadataSyncQueue.onNext(entry);
                LogHelper.d(TAG, "fillSongMetadata - Metadata not found for: " + entry.getFullPath() + ". Queueing request for synchronization.");

            }

            if (liveSourceRequired && !liveSourceFound) {
                refreshDownloadURL(entry);
            }

            return entry;

        });
    }

    private void synchronizeSongDB(Observable<DropboxDBEntry> entryObservable) {

        // TODO Convert into method argument
        final boolean cacheFile = true;

        entryObservable
                .subscribe(entry -> {

                    LogHelper.d(TAG, "synchronizeSongDB - Started for: " + entry.getFullPath() + ". Observing on thread: " + Thread.currentThread().getName());

                    DropboxDBSong song;

                    song = entry.getSong();

                    if (song == null) {
                        LogHelper.d(TAG, "synchronizeSongDB - Song is null for entry: " + entry.getFullPath() + ". Ignoring...");
                        return;
                    }

                    if (song.getEntryId() != entry.getId()) {
                        LogHelper.d(TAG, "synchronizeSongDB - Song has mismatching ID for entry: " + entry.getFullPath() + ". Ignoring...");
                        return;
                    }

                    if (!hasValidCachedData(song)) {
                        if (!hasValidDownloadURL(song)) {
                            refreshDownloadURL(entry);
                        }

                        if (cacheFile) {
                            downloadSongDataIntoCache(entry);
                        }
                    }

                    MediaMetadataRetriever retriever;
                    retriever = initializeMediaMetadataRetriever(entry);

                    if (retriever == null) return;

                    song.setAlbum(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));
                    song.setArtist(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));
                    song.setGenre(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE));
                    song.setTitle(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));

                    String tmpString;

                    if ((tmpString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) != null) {
                        try {
                            song.setDuration(Long.valueOf(tmpString));
                        } catch (NumberFormatException e) {
                            LogHelper.w(TAG, "synchronizeSongDB - Invalid duration: " + tmpString + " for file: " + entry.getFullPath(), e);
                        }
                    }

                    if ((tmpString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)) != null) {
                        try {
                            song.setTrackNumber(Integer.valueOf(tmpString));
                        } catch (NumberFormatException e) {
                            LogHelper.w(TAG, "synchronizeSongDB - Invalid track number: " + tmpString + " for file: " + entry.getFullPath(), e);
                        }
                    }

                    if ((tmpString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS)) != null) {
                        try {
                            song.setTotalTracks(Integer.valueOf(tmpString));
                        } catch (NumberFormatException e) {
                            LogHelper.w(TAG, "synchronizeSongDB - Invalid number of tracks: " + tmpString + " for file: " + entry.getFullPath(), e);
                        }
                    }

                    mSongDao.insertOrReplace(song);

                    LogHelper.d(TAG, "synchronizeSongDB - Updated song for: " + entry.getFullPath() + " with id: " + song.getId());

                    // Cache album art
                    try {
                        Glide
                                .with(mApplicationContext)
                                .load(retriever.getEmbeddedPicture())
                                .fallback(R.drawable.ic_default_art)
                                .signature(new AlbumArtLoader.Key(MusicProvider.buildMetadataFromDBEntry(entry,getCachedSongFile(entry))))
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                // TODO Capture size in constants
                                .into(480, 800)
                                .get();


                        LogHelper.d(TAG, "synchronizeSongDB - Cached album art image for: " + entry.getFullPath());

                    } catch (InterruptedException | ExecutionException e) {
                        LogHelper.w(TAG, "synchronizeSongDB - Failed to cache album art image for: " + entry.getFullPath(), e);
                    }

                });

    }

    private void refreshDownloadURL(DropboxDBEntry entry) {

        DropboxDBSong song = entry.getSong();
        DropboxAPI.DropboxLink link;
        try {

            if (song.getDownloadURL() == null || song.getDownloadURLExpiration().compareTo(new Date()) <= 0) {

                link = mDropboxApi.media(entry.getFullPath(), false);

                LogHelper.d(TAG, "synchronizeSongDB - Generated new download URL for: " + entry.getFullPath());

                // Songs are deleted from the DB automatically when entry/file is update
                // A song found in the database is guaranteed to have fresh metadata
                // In that case, only the streaming/download link needs to be refreshed

                song.setDownloadURL(new URL(link.url));
                song.setDownloadURLExpiration(link.expires);

            }

        } catch (MalformedURLException e) {
            LogHelper.w(TAG, "synchronizeSongDB - Invalid URL for: " + entry.getFullPath(), e);
            throw new RuntimeException(e);
        } catch (DropboxException e) {
            LogHelper.w(TAG, "synchronizeSongDB - Finished with error", e);
            throw new RuntimeException(e); // Is there a better way throw exceptions from within map?
        }
    }

    private void downloadSongDataIntoCache(DropboxDBEntry entry) {

        DropboxDBSong song = entry.getSong();

        Request downloadRequest;
        Response downloadResponse;
        InputStream downloadStream = null;

        DiskLruCache.Editor cacheEditor = null;
        OutputStream cacheStream = null;

        downloadRequest = new Request.Builder()
                .url(song.getDownloadURL())
                .build();

        try {

            downloadResponse = mHttpClient.newCall(downloadRequest).execute();
            if (downloadResponse.isSuccessful()) {

                LogHelper.d(TAG, "synchronizeSongDB - Successfully downloaded song.");
                downloadStream = downloadResponse.body().byteStream();

                cacheEditor = mCachedSongs.edit(SongCacheHelper.getDiskLRUCacheKey(entry));
                cacheStream = cacheEditor.newOutputStream(0);

                int read = 0;
                byte[] buffer = new byte[1024];

                while ((read = downloadStream.read(buffer)) != -1) {
                    cacheStream.write(buffer, 0, read);
                }
                cacheStream.flush();
                cacheEditor.commit();
                LogHelper.d(TAG, "synchronizeSongDB - Successfully cached song");
            } else {
                LogHelper.w(TAG, "synchronizeSongDB - Failed downloading song (Server response: " + downloadResponse.code() + ")");
            }

        } catch (IOException e) {
            LogHelper.w(TAG,"synchronizeSongDB - Failed caching song file.",e);
            try {
                cacheEditor.abort();
            } catch (IOException ioe) {
                LogHelper.w(TAG,"synchronizeSongDB - Failed closing aborting cache editor.",ioe);
            }
        } finally {
            CloseableHelper.close(cacheStream);
            CloseableHelper.close(downloadStream);
        }
    }

    private MediaMetadataRetriever initializeMediaMetadataRetriever(DropboxDBEntry entry) {

        DropboxDBSong song = entry.getSong();

        MediaMetadataRetriever retriever;
        File cachedSongFile = null;

        retriever = new MediaMetadataRetriever();

        cachedSongFile = getCachedSongFile(entry);

        try {
            if (cachedSongFile != null) {
                retriever.setDataSource(cachedSongFile.getPath());
            } else {
                retriever.setDataSource(song.getDownloadURL().toString(), new HashMap<String, String>());
            }
        } catch (RuntimeException e) {
            LogHelper.w(TAG, "synchronizeSongDB - Unable to use as data source: " + song.getDownloadURL(),e);
            return null;
        }
        LogHelper.d(TAG, "synchronizeSongDB - Metadata retriever created for: " + entry.getFullPath() + " with URL: " + song.getDownloadURL());


        return retriever;
    }

    public Observable<byte[]> getAlbumArt(MediaMetadata mm) {

        return Observable.create(subscriber -> {

            LogHelper.d(TAG, "getAlbumArt - Starting subscriber on thread: "+Thread.currentThread().getName());

            MediaMetadataRetriever retriever;

            // TODO Test flag for good links
            if (mm == null || mm.getString(MusicProvider.CUSTOM_METADATA_TRACK_SOURCE) == null) {

                LogHelper.d(TAG, "getAlbumArt - Unable to create metadata retriever. Metadata null or missing download URL.");

                // Nothing to update
                subscriber.onError(new Exception("getAlbumArt - Unable to obtain download URL for song (null or missing)."));
                return;
            }

            retriever = new MediaMetadataRetriever();

            try {
                // TODO Implement disk LRU for song files
                retriever.setDataSource(mm.getString(MusicProvider.CUSTOM_METADATA_TRACK_SOURCE), new HashMap<String, String>());
                LogHelper.d(TAG, "getAlbumArt - Metadata retriever created with URL: " + mm.getString(MusicProvider.CUSTOM_METADATA_TRACK_SOURCE));

            } catch (RuntimeException e) {
                LogHelper.w(TAG, "getAlbumArt - Unable to use as data source: " + mm.getString(MusicProvider.CUSTOM_METADATA_TRACK_SOURCE), e);
                subscriber.onError(e);
                return;
            }

            byte[] bitmapByteArray = retriever.getEmbeddedPicture();

            if (bitmapByteArray != null && bitmapByteArray.length > 0) {
                subscriber.onNext(bitmapByteArray);
            } else {
                // TODO Avoid future attempts when file contains no image data
                subscriber.onError(new Exception("File contains no image data."));
                LogHelper.w(TAG, "getAlbumArt - Finished with error for: " + mm.getString(MusicProvider.CUSTOM_METADATA_TRACK_SOURCE));
                return;
            }

            subscriber.onCompleted();
            LogHelper.d(TAG, "getAlbumArt - Finished succesfully for: " + mm.getString(MusicProvider.CUSTOM_METADATA_TRACK_SOURCE));

        });

    }

    public boolean hasValidDownloadURL(DropboxDBSong song) {
        return !(song.getDownloadURL() == null ||
                song.getDownloadURLExpiration() == null ||
                song.getDownloadURLExpiration().compareTo(new Date()) <= 0);
    }

    public boolean hasValidCachedData(DropboxDBSong song) {
        DiskLruCache.Snapshot snapshot = null;
        try {
            snapshot = mCachedSongs.get(SongCacheHelper.getDiskLRUCacheKey(song));
        } catch (IOException e) {
            LogHelper.w(TAG, "hasValidCachedData - Unable to retrieve value from cache", e);
            return false;
        } finally {
            CloseableHelper.close(snapshot);
        }
        return snapshot != null;
    }

    public File getCachedSongFile(DropboxDBEntry entry) {

        DropboxDBSong song = entry.getSong();

        File cachedSongFile = null;
        DiskLruCache.Snapshot cachedSongData = null;
        InputStream cachedSongInputStream = null;
        FileOutputStream cachedSongOutputStream = null;
        try {

            cachedSongData = mCachedSongs.get(Long.toString(entry.getId()));

            if (cachedSongData != null) {

                cachedSongInputStream = cachedSongData.getInputStream(0);

                cachedSongFile =
                        new File(
                                SongCacheHelper.getFileCachePath(mApplicationContext),
                                SongCacheHelper.getFileCacheName(entry));

                if (cachedSongFile.exists()) {
                    return cachedSongFile;
                } else {
                    cachedSongFile.createNewFile();
                    cachedSongOutputStream = new FileOutputStream(cachedSongFile);

                    int read = 0;
                    byte[] buffer = new byte[1024];

                    while ((read = cachedSongInputStream.read(buffer)) != -1) {
                        cachedSongOutputStream.write(buffer, 0, read);
                    }
                    cachedSongOutputStream.flush();
                }
            }

        } catch (IOException e) {
            LogHelper.w(TAG, "synchronizeSongDB - Unable to use temp file as data source", e);
            cachedSongFile = null;
        } finally {
            CloseableHelper.close(cachedSongOutputStream);
            CloseableHelper.close(cachedSongInputStream);
            CloseableHelper.close(cachedSongData);
        }
        return cachedSongFile;
    }


}
