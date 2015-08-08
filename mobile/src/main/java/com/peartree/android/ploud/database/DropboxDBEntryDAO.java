package com.peartree.android.ploud.database;


import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

import com.peartree.android.ploud.database.DropboxDBContract.Entry;
import com.peartree.android.ploud.database.DropboxDBEntryMapper.DropboxDBEntryCursorWrapper;
import com.peartree.android.ploud.utils.LogHelper;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;

@Singleton
public class DropboxDBEntryDAO {

    private static final String TAG =LogHelper.makeLogTag(DropboxDBEntryDAO.class);

    private DropboxDBHelper mDbHelper;

    @Inject
    public DropboxDBEntryDAO(DropboxDBHelper dbHelper) {
        this.mDbHelper = dbHelper;
    }

    public long insertOrReplace(DropboxDBEntry entry) {

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        long id = db.insertWithOnConflict(Entry.TABLE_NAME, null, DropboxDBEntryMapper.toContentValues(entry), SQLiteDatabase.CONFLICT_REPLACE);

        LogHelper.d(TAG,"Inserted/Updated entry with id:"+id+" for: "+entry.getFullPath());

        return id;
    }

    public DropboxDBEntry findById(long id) {

        SQLiteDatabase db = mDbHelper.getReadableDatabase();

        Cursor results;
        String selection = Entry._ID + " = ?";
        String[] selectionArgs = { Long.toString(id) };

        results = db.query(Entry.TABLE_NAME,null,selection,selectionArgs,null,null,null,"1");

        LogHelper.d("Found "+results.getCount()+" entries with id:"+id);

        if (results.getCount() > 0) {
            results.moveToFirst();
            return new DropboxDBEntryCursorWrapper(results).getEntry();
        } else {
            return null;
        }

    }

    // TODO Rethink method name
    public Observable<DropboxDBEntry> getFindByDir(@NonNull String parentDir) {
        return Observable.create(subscriber -> {
            DropboxDBEntryCursorWrapper cursor = null;
            try {
                cursor = findByParentDir(parentDir);
                if (cursor.getCount() > 0) {
                    cursor.moveToFirst();

                    do {
                        subscriber.onNext(cursor.getEntry());
                    } while (cursor.moveToNext());

                }

            } finally {
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }
                subscriber.onCompleted();
            }
        });
    }

    public DropboxDBEntryMapper.DropboxDBEntryCursorWrapper findByParentDir(String parentDir) {
        return findByParentDir(parentDir, true);

    }

    public DropboxDBEntryMapper.DropboxDBEntryCursorWrapper findByParentDir(String parentDir, boolean excludeEmpty) {

        SQLiteDatabase db = mDbHelper.getReadableDatabase();

        Cursor results;

        String excludeEmptyClause =
                "(NOT "+Entry.COLUMN_NAME_IS_DIR+
                        " OR EXISTS ("+
                        "SELECT child."+Entry._ID+" FROM "+Entry.TABLE_NAME+" AS child WHERE "+
                        "NOT child."+Entry.COLUMN_NAME_IS_DIR+" AND "+
                        "child."+Entry.COLUMN_NAME_PARENT_DIR+" LIKE "+
                        "parent."+Entry.COLUMN_NAME_PARENT_DIR+" || parent."+Entry.COLUMN_NAME_FILENAME+" || '/%' COLLATE NOCASE)) ";

        String query =
                "SELECT parent.* FROM "+
                        Entry.TABLE_NAME+" AS parent WHERE "+
                        "parent."+Entry.COLUMN_NAME_PARENT_DIR+" = ? COLLATE NOCASE "+
                        (excludeEmpty?"AND "+excludeEmptyClause:"")+
                        "ORDER BY parent."+Entry.COLUMN_NAME_IS_DIR+" DESC, parent."+Entry.COLUMN_NAME_FILENAME+" ASC";

        String[] selectionArgs = { parentDir.endsWith("/")?parentDir:(parentDir+"/") };

        results = db.rawQuery(query,selectionArgs);

        LogHelper.d("Found "+results.getCount()+" entries with parentDir:"+parentDir+(excludeEmpty?" excluding":" including")+" empty directories");

        return new DropboxDBEntryCursorWrapper(results);
    }

    public int deleteById(long id) {

        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        String selection = Entry._ID + " = ?";
        String[] selectionArgs = { Long.toString(id) };

        int deleted = db.delete(Entry.TABLE_NAME,selection,selectionArgs);

        LogHelper.d(TAG,"Deleted "+deleted+" entries with id:"+id);

        return deleted;
    }

    public int deleteByParentDirAndFilename(String parentDir, String filename) {

        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        String selection = Entry.COLUMN_NAME_PARENT_DIR+" = ? AND "+Entry.COLUMN_NAME_FILENAME+" = ?";
        String[] selectionArgs = { parentDir, filename };

        int deleted = db.delete(Entry.TABLE_NAME,selection,selectionArgs);

        LogHelper.d(TAG,"Deleted "+deleted+" entries for parent directory:"+parentDir+" and file: "+filename);

        return deleted;
    }

    public int deleteTreeByAncestorDir(String ancestorPath) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        int lastSeparator = ancestorPath.lastIndexOf("/");
        String ancestorParent = lastSeparator>=0?ancestorPath.substring(0,lastSeparator+1):null;
        String ancestorName = lastSeparator>=0?ancestorPath.substring(lastSeparator+1):ancestorPath;

        String selection = "("+Entry.COLUMN_NAME_PARENT_DIR+" = ? AND "+Entry.COLUMN_NAME_FILENAME+" = ?) OR "+Entry.COLUMN_NAME_PARENT_DIR+" LIKE ?";
        String[] selectionArgs = { ancestorParent, ancestorName, ancestorPath+"%" };

        int deleted = db.delete(Entry.TABLE_NAME,selection,selectionArgs);

        LogHelper.d(TAG,"Deleted "+deleted+" entries under "+ancestorPath+" tree");

        return deleted;
    }
}
