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
import com.peartree.android.ploud.R;
import com.peartree.android.ploud.database.DropboxDBEntry;
import com.peartree.android.ploud.database.DropboxDBEntryDAO;
import com.peartree.android.ploud.database.DropboxDBSong;
import com.peartree.android.ploud.database.DropboxDBSongDAO;
import com.peartree.android.ploud.model.AlbumArtLoader;
import com.peartree.android.ploud.model.MusicProvider;
import com.peartree.android.ploud.utils.LogHelper;
import com.peartree.android.ploud.utils.PrefUtils;

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
    private DropboxDBEntryDAO mEntryDao;
    private DropboxDBSongDAO mSongDao;

    private final PublishSubject<DropboxDBEntry> mMetadataSyncQueue;

    @Inject
    public DropboxSyncService(Application application, DropboxAPI<AndroidAuthSession> dbApi, DropboxDBEntryDAO entryDao, DropboxDBSongDAO songDao) {
        this.mApplicationContext = application.getApplicationContext();
        this.mDropboxApi = dbApi;
        this.mEntryDao = entryDao;
        this.mSongDao = songDao;

        this.mMetadataSyncQueue = PublishSubject.create(); // No need to serialize
        this.mMetadataSyncQueue
                .distinct(entry -> entry.getId())
                .window(1)
                .onBackpressureBuffer()
                .observeOn(Schedulers.newThread())
                // TODO Convert to asynchronous/parallel processing
                .subscribe(this::updateSongMetadata);
    }

    public Observable<Long> updateEntryDB() {

        LogHelper.d(TAG, "Dropbox auth status: " + mDropboxApi.getSession().isLinked());

        return Observable.create(subscriber -> {

                    LogHelper.d(TAG, "updateEntryDB - Subscribing on thread: " + Thread.currentThread().getName());

                    DropboxAPI.DeltaPage<DropboxAPI.Entry> deltaPage;
                    DropboxAPI.Entry dbEntry;
                    DropboxDBEntry entry;
                    String deltaCursor;

                    int pageCounter = 1;

                    deltaCursor = PrefUtils.getDropboxDeltaCursor(mApplicationContext);

                    try {

                        do {

                            deltaPage = mDropboxApi.delta(deltaCursor);

                            LogHelper.d(TAG, "updateEntryDB - Processing delta page #" + (pageCounter++) + " size: " + deltaPage.entries.size());

                            for (DropboxAPI.DeltaEntry<DropboxAPI.Entry> deltaEntry : deltaPage.entries) {

                                dbEntry = deltaEntry.metadata;

                                if (dbEntry == null || dbEntry.isDeleted) {

                                    mEntryDao.deleteTreeByAncestorDir(deltaEntry.lcPath);
                                    LogHelper.d(TAG, "updateEntryDB - Deleted entry for: " + deltaEntry.lcPath);

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

                                LogHelper.d(TAG, "updateEntryDB - Saved entry for: " + dbEntry.parentPath() + " with id: " + id);

                                subscriber.onNext(id);
                            }

                            deltaCursor = deltaPage.cursor;
                            PrefUtils.setDropboxDeltaCursor(mApplicationContext, deltaPage.cursor);

                        } while (deltaPage.hasMore);

                        subscriber.onCompleted();
                        LogHelper.d(TAG, "updateEntryDB - Finished successfully");

                    } catch (DropboxException dbe) {

                        subscriber.onError(dbe);
                        LogHelper.w(TAG, "updateEntryDB - Finished with error", dbe);

                    }

                }
        );
    }

    public Observable<DropboxDBEntry> updateSongDB(Observable<DropboxDBEntry> entries) {
        return entries.map(entry -> {

            LogHelper.d(TAG,"updateSongDB - Started for: "+entry.getFilename()+". Observing on thread: "+Thread.currentThread().getName());

            if (entry.isDir()) {

                LogHelper.d(TAG,"updateSongDB - Found directory. Nothing to do.");
                return entry;
            }

            DropboxDBSong existingSong;

            try {
                existingSong = mSongDao.findByEntryId(entry.getId());
            } catch (MalformedURLException | ParseException e) {
                LogHelper.w(TAG, "updateSongDB - Bad song data in the database for: " + entry.getFilename() + ". Replacing...");
                existingSong = null; // Proceed to replace song in DB
            }

            if (existingSong != null &&
                    existingSong.getDownloadURLExpiration() != null &&
                    existingSong.getDownloadURLExpiration().compareTo(new Date()) > 0) {

                LogHelper.d(TAG,"updateSongDB - Found song with valid download URL for: " + entry.getFilename());

                entry.setSong(existingSong);
                return entry;
            }

            try {

                DropboxAPI.DropboxLink link;
                DropboxDBSong song;

                // TODO Can this be deferred until link is needed (either to play or retrieve md)?
                link = mDropboxApi.media(entry.getParentDir() + entry.getFilename(), false);

                LogHelper.d(TAG,"updateSongDB - Generated new download URL for: "+entry.getFilename());

                // Songs are deleted from the DB automatically when entry/file is update
                // A song found in the database is guaranteed to have fresh metadata
                // In that case, only the streaming/download link needs to be refreshed

                song = existingSong != null ? existingSong : new DropboxDBSong();

                try {
                    song.setDownloadURL(new URL(link.url));
                } catch (MalformedURLException e) {
                    LogHelper.w(TAG, "updateSongDB - Invalid URL: " + link.url + " for: " + entry.getFilename());
                    throw new RuntimeException(e);
                }

                song.setDownloadURLExpiration(link.expires);

                if (existingSong != null) {

                    long savedSongId = mSongDao.insertOrReplace(song);
                    // TODO Move check/log to DAO

                    if (savedSongId < 0) {
                        LogHelper.w(TAG, "updateSongDB - Error updating song for: " + entry.getFilename() + ". DAO returned: " + savedSongId);
                    } else {
                        LogHelper.w(TAG, "updateSongDB - Updated song for: " + entry.getFilename() + " with id: " +savedSongId);
                    }

                } else {

                    song.setEntryId(entry.getId());
                    entry.setSong(song);

                    // Delay saving
                    // Retrieve metadata asynchronously
                    mMetadataSyncQueue.onNext(entry);

                    LogHelper.d(TAG, "updatedSongDb - Requested asynchronous metadata update for: " + entry.getFilename());
                }

                LogHelper.d(TAG, "updateSongDB - Finished successfully");
                return entry; // Emits, whether or not song successfully persisted

            } catch (DropboxException e) {

                LogHelper.d(TAG, "updateSongDB - Finished with error", e);
                throw new RuntimeException(e); // Is there a better way throw exceptions from within map?
            }

        });
    }

    private void updateSongMetadata(Observable<DropboxDBEntry> entryObservable) {

        entryObservable
                .subscribe(entry -> {

                    LogHelper.d(TAG,"updateSongMetadata - Started for: "+entry.getFilename()+". Observing on thread: "+Thread.currentThread().getName());

                    MediaMetadataRetriever retriever;
                    DropboxDBSong song = entry.getSong();

                    if (song == null) {
                        LogHelper.d(TAG,"updateSongMetadata - Entry for: "+entry.getFilename()+" contains no song information. Nothing to do.");
                        return;
                    }

                    // TODO Implement permanent solution to preventing repeated requests
                    song.setId(mSongDao.insertOrReplace(song));
                    LogHelper.w(TAG, "updateSongMetadata - Saved song for: " + entry.getFilename() + " with id: " + song.getId());

                    retriever = new MediaMetadataRetriever();

                    try {
                        // TODO Implement disk LRU for song files
                        retriever.setDataSource(song.getDownloadURL().toString(), new HashMap<String, String>());
                        LogHelper.w(TAG, "updateSongMetadata - Metadata retriever created for: " + entry.getFilename() + " with URL: " + song.getDownloadURL());

                    } catch (RuntimeException e) {
                        LogHelper.w(TAG, "updateSongMetadata - Unable to use as data source: " + song.getDownloadURL());
                        // TODO Flag in DB
                        return;
                    }

                    song.setAlbum(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));
                    song.setArtist(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));
                    song.setGenre(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE));
                    song.setTitle(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));

                    String tmpString;

                    if ((tmpString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) != null) {
                        try {
                            song.setDuration(Long.valueOf(tmpString));
                        } catch (NumberFormatException e) {
                            LogHelper.w(TAG, "Invalid duration: " + tmpString + " for file: " + entry.getFilename());
                        }
                    }

                    if ((tmpString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)) != null) {
                        try {
                            song.setTrackNumber(Integer.valueOf(tmpString));
                        } catch (NumberFormatException e) {
                            LogHelper.w(TAG, "Invalid track number: " + tmpString + " for file: " + entry.getFilename());
                        }
                    }

                    if ((tmpString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS)) != null) {
                        try {
                            song.setTotalTracks(Integer.valueOf(tmpString));
                        } catch (NumberFormatException e) {
                            LogHelper.w(TAG, "Invalid number of tracks: " + tmpString + " for file: " + entry.getFilename());
                        }
                    }

                    mSongDao.insertOrReplace(song);
                    LogHelper.w(TAG, "updateSongMetadata - Updated song for: " + entry.getFilename() + " with id: " + song.getId());

                    // Cache album art
                    try {
                        Glide
                                .with(mApplicationContext)
                                .load(retriever.getEmbeddedPicture())
                                .fallback(R.drawable.ic_default_art)
                                .signature(new AlbumArtLoader.Key(MusicProvider.buildMetadataFromDBEntry(entry)))
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                // TODO Capture size in constants
                                .into(480, 800)
                                .get();


                        LogHelper.w(TAG, "updateSongMetadata - Cached album art image for: " + entry.getFilename());

                    } catch (InterruptedException | ExecutionException e) {
                        LogHelper.w(TAG, "updateSongMetadata - Failed to cache album art image for: " + entry.getFilename(), e);
                    }

                });

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
                LogHelper.w(TAG, "getAlbumArt - Metadata retriever created with URL: " + mm.getString(MusicProvider.CUSTOM_METADATA_TRACK_SOURCE));

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
            LogHelper.w(TAG, "getAlbumArt - Finished succesfully for: " + mm.getString(MusicProvider.CUSTOM_METADATA_TRACK_SOURCE));

        });

    }
}
