package com.peartree.android.ploud.dropbox;

import android.app.Application;
import android.content.Context;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.peartree.android.ploud.database.DropboxDBEntryDAO;
import com.peartree.android.ploud.database.DropboxDBEntryMapper;
import com.peartree.android.ploud.utils.LogHelper;
import com.peartree.android.ploud.utils.PrefUtils;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;

@Singleton
public class DropboxSyncService {

    private static final String TAG = LogHelper.makeLogTag(DropboxSyncService.class);

    private Context mApplicationContext;
    private DropboxAPI<AndroidAuthSession> mDropboxApi;
    private DropboxDBEntryDAO mEntryDao;

    @Inject
    public DropboxSyncService(Application application, DropboxAPI<AndroidAuthSession> dbApi, DropboxDBEntryDAO entryDao) {
        this.mApplicationContext = application.getApplicationContext();
        this.mDropboxApi = dbApi;
        this.mEntryDao = entryDao;

    }

    public Observable<Long> getDBEntrySync() {

        LogHelper.d(TAG,"Dropbox auth status: " + mDropboxApi.getSession().isLinked());

        return Observable.create(subscriber -> {

                    DropboxAPI.DeltaPage<DropboxAPI.Entry> deltaPage;
                    DropboxAPI.Entry dbEntry;
                    DropboxDBEntryMapper.DropboxDBEntryPojo entry;
                    String deltaCursor;

                    deltaCursor = PrefUtils.getDropboxDeltaCursor(mApplicationContext);

                    try {

                        do {

                            deltaPage = mDropboxApi.delta(deltaCursor);
                            deltaCursor = deltaPage.cursor;

                            LogHelper.d(TAG, "Delta page size: " + deltaPage.entries.size());

                            for (DropboxAPI.DeltaEntry<DropboxAPI.Entry> deltaEntry:deltaPage.entries) {

                                dbEntry = deltaEntry.metadata;

                                entry = new DropboxDBEntryMapper.DropboxDBEntryPojo();

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

                                LogHelper.d(TAG, "Saved entry for "+dbEntry.path+" with id "+id);

                                subscriber.onNext(id);

                            }

                        } while (deltaPage.hasMore);

                        PrefUtils.setDropboxDeltaCursor(mApplicationContext, deltaCursor);
                        subscriber.onCompleted();

                    } catch (DropboxException dbe) {

                        LogHelper.w(TAG,dbe);
                        subscriber.onError(dbe);

                    }

                }
        );

    }
}
