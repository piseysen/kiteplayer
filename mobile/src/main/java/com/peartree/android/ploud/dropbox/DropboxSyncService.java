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

import java.net.URI;
import java.util.Date;
import java.util.HashMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;

@Singleton
public class DropboxSyncService {

    private static final String TAG = LogHelper.makeLogTag(DropboxSyncService.class);

    private Context mApplicationContext;
    private DropboxAPI<AndroidAuthSession> mDropboxApi;
    private DropboxDBEntryDAO mEntryDao;
    private DropboxDBSongDAO mSongDao;


    @Inject
    public DropboxSyncService(Application application, DropboxAPI<AndroidAuthSession> dbApi, DropboxDBEntryDAO entryDao, DropboxDBSongDAO songDao) {
        this.mApplicationContext = application.getApplicationContext();
        this.mDropboxApi = dbApi;
        this.mEntryDao = entryDao;
        this.mSongDao = songDao;
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

                                if (dbEntry.isDeleted) {
                                    mEntryDao.deleteByParentDirAndFilename(dbEntry.parentPath(), dbEntry.fileName());
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

                            PrefUtils.setDropboxDeltaCursor(mApplicationContext, deltaCursor);

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
        return Observable.create(subscriber -> {
            entries.subscribe(entry -> {

                if (entry.isDir()) {
                    subscriber.onNext(entry);
                    return;
                }

                DropboxDBSong song;

                try {
                    song = mSongDao.findByEntryId(entry.getId());
                } catch (Exception e) {
                    LogHelper.w(TAG, "Bad song data in the database for filename: " + entry.getFilename());
                    song = null; // Proceed to replace song in DB
                }

                if (song != null && song.getDownloadURLExpiration() != null && song.getDownloadURLExpiration().compareTo(new Date()) > 0) {
                    entry.setSong(song);
                    subscriber.onNext(entry);
                    return;
                }

                try {

                    DropboxAPI.DropboxLink link;
                    MediaMetadataRetriever retriever;
                    DropboxDBSong newSong;

                    link = mDropboxApi.media(entry.getParentDir() + entry.getFilename(), false);

                    retriever = new MediaMetadataRetriever();
                    retriever.setDataSource(link.url, new HashMap<String, String>());

                    newSong = new DropboxDBSong();

                    try {
                        newSong.setDownloadURL(new URI(link.url).toURL());
                    } catch (Exception e) {
                        LogHelper.w("Invalid URL: " + link.url + " for file: " + entry.getFilename());
                    }

                    newSong.setDownloadURLExpiration(link.expires);

                    newSong.setAlbum(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));
                    newSong.setArtist(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));
                    newSong.setGenre(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE));
                    newSong.setTitle(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));

                    String tmpString;

                    if ((tmpString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) != null) {
                        try {
                            newSong.setDuration(Long.valueOf(tmpString));
                        } catch (NumberFormatException e) {
                            LogHelper.w(TAG, "Invalid duration: " + tmpString + " for file: " + entry.getFilename());
                        }
                    }

                    if ((tmpString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)) != null) {
                        try {
                            newSong.setTrackNumber(Integer.valueOf(tmpString));
                        } catch (NumberFormatException e) {
                            LogHelper.w(TAG, "Invalid track number: " + tmpString + " for file: " + entry.getFilename());
                        }
                    }

                    if ((tmpString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS)) != null)
                        try {
                            newSong.setTotalTracks(Integer.valueOf(tmpString));
                        } catch (NumberFormatException e) {
                            LogHelper.w(TAG, "Invalid number of tracks: " + tmpString + " for file: " + entry.getFilename());
                        }

                    newSong.setEntryId(entry.getId());

                    long newSongId = mSongDao.insertOrReplace(newSong);

                    if (newSongId > 0) {
                        newSong.setId(newSongId);
                    } else {
                        LogHelper.w(TAG, "Error persisting song for file: " + entry.getFilename() + ". DAO returned: " + newSongId);
                    }

                    entry.setSong(newSong);
                    subscriber.onNext(entry); // Emits, whether or not song successfully persisted

                } catch (DropboxException e) {
                    subscriber.onError(e);
                }

            }, error -> {
                subscriber.onError(error);
            }, () -> {
                subscriber.onCompleted();
            });
        });
    }
}
