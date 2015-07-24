package com.peartree.android.ploud.dropbox;

import android.app.Application;
import android.content.Context;
import android.media.MediaMetadataRetriever;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.peartree.android.ploud.database.DropboxDBEntry;
import com.peartree.android.ploud.database.DropboxDBEntryDAO;
import com.peartree.android.ploud.database.DropboxDBSong;
import com.peartree.android.ploud.database.DropboxDBSongDAO;
import com.peartree.android.ploud.utils.LogHelper;
import com.peartree.android.ploud.utils.PrefUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;

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

    private final PublishSubject<DropboxDBEntry> mMetadataSyncSubject;

    @Inject
    public DropboxSyncService(Application application, DropboxAPI<AndroidAuthSession> dbApi, DropboxDBEntryDAO entryDao, DropboxDBSongDAO songDao) {
        this.mApplicationContext = application.getApplicationContext();
        this.mDropboxApi = dbApi;
        this.mEntryDao = entryDao;
        this.mSongDao = songDao;
        this.mMetadataSyncSubject = PublishSubject.create(); // No need to serialize

        this.mMetadataSyncSubject.observeOn(Schedulers.newThread()).onBackpressureBuffer().subscribe(this::updateSongMetadata);
    }

    public Observable<Long> getDBEntrySyncronizer() {

        LogHelper.d(TAG, "Dropbox auth status: " + mDropboxApi.getSession().isLinked());

        return Observable.create(subscriber -> {

                    DropboxAPI.DeltaPage<DropboxAPI.Entry> deltaPage;
                    DropboxAPI.Entry dbEntry;
                    DropboxDBEntry entry;
                    String deltaCursor;

                    deltaCursor = PrefUtils.getDropboxDeltaCursor(mApplicationContext);

                    try {

                        do {

                            deltaPage = mDropboxApi.delta(deltaCursor);
                            deltaCursor = deltaPage.cursor;

                            LogHelper.d(TAG, "Delta page size: " + deltaPage.entries.size());

                            for (DropboxAPI.DeltaEntry<DropboxAPI.Entry> deltaEntry : deltaPage.entries) {

                                dbEntry = deltaEntry.metadata;

                                if (dbEntry == null || dbEntry.isDeleted) {
                                    mEntryDao.deleteTreeByAncestorDir(deltaEntry.lcPath);
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

                                LogHelper.d(TAG, "Saved entry for " + dbEntry.parentPath() + " with id " + id);

                                subscriber.onNext(id);

                            }

                            PrefUtils.setDropboxDeltaCursor(mApplicationContext, deltaPage.cursor);

                        } while (deltaPage.hasMore);

                        subscriber.onCompleted();

                    } catch (DropboxException dbe) {

                        LogHelper.w(TAG, dbe);
                        subscriber.onError(dbe);

                    }

                }
        );
    }

    public Observable<DropboxDBEntry> getDBSongSyncronizer(Observable<DropboxDBEntry> entries) {
        return entries.map(entry -> {

            if (entry.isDir()) {
                return entry;
            }

            DropboxDBSong existingSong;

            try {
                existingSong = mSongDao.findByEntryId(entry.getId());
            } catch (MalformedURLException|ParseException e) {
                LogHelper.w(TAG, "Bad song data in the database for filename: " + entry.getFilename());
                existingSong = null; // Proceed to replace song in DB
            }

            if (existingSong != null && existingSong.getDownloadURLExpiration() != null && existingSong.getDownloadURLExpiration().compareTo(new Date()) > 0) {
                entry.setSong(existingSong);
                return entry;
            }

            try {

                DropboxAPI.DropboxLink link;
                MediaMetadataRetriever retriever;
                DropboxDBSong song;

                // TODO Can this be deferred until link is needed (either to play or retrieve md)?
                link = mDropboxApi.media(entry.getParentDir() + entry.getFilename(), false);

                // Songs are deleted from the DB automatically when entry/file is update
                // A song found in the database is guaranteed to have fresh metadata
                // In that case, only the streaming/download link needs to be refreshed

                song = existingSong!=null?existingSong:new DropboxDBSong();

                try {
                    song.setDownloadURL(new URL(link.url));
                } catch (MalformedURLException e) {
                    LogHelper.w("Invalid URL: " + link.url + " for file: " + entry.getFilename());
                    throw new RuntimeException(e);
                }

                song.setDownloadURLExpiration(link.expires);

                if (existingSong != null) {

                    // TODO Prevent repeated requests

                    long savedSongId = mSongDao.insertOrReplace(song);

                    // TODO Move check/log to DAO

                    if (savedSongId < 0) {
                        LogHelper.w(TAG, "Error persisting song for file: " + entry.getFilename() + ". DAO returned: " + savedSongId);
                    }

                } else {

                    song.setEntryId(entry.getId());
                    entry.setSong(song);

                    // Delay saving
                    // Retrieve metadata asynchronously
                    mMetadataSyncSubject.onNext(entry);
                    LogHelper.d(TAG, "Update Song Metadata - Requested: " + entry.getParentDir() + entry.getFilename());
                }

                return entry; // Emits, whether or not song successfully persisted

            } catch (DropboxException e) {
                throw new RuntimeException(e); // Is there a better way throw exceptions from within map?
            }

        });
    }

    private void updateSongMetadata(DropboxDBEntry entry) {

        MediaMetadataRetriever retriever;
        DropboxDBSong song = entry.getSong();

        // TODO Implement permanent solution to preventing repeated requests
        song.setId(mSongDao.insertOrReplace(song));

        LogHelper.d(TAG,"Update Song Metadata - Request Received: "+entry.getParentDir()+entry.getFilename());

        if (song == null) {
            // Nothing to update
            return;
        }

        retriever = new MediaMetadataRetriever();

        try {
            retriever.setDataSource(song.getDownloadURL().toString(), new HashMap<String, String>());
        } catch (RuntimeException e) {
            LogHelper.w(TAG, "Unable to use as data source: " + song.getDownloadURL());
            // TODO Flag in DB
            return;
        }

        LogHelper.d(TAG,"Update Song Metadata - Retriever created: "+entry.getParentDir()+entry.getFilename());

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

        LogHelper.d(TAG, "Update Song Metadata - Database Updated: " + entry.getParentDir() + entry.getFilename());

    }
}
