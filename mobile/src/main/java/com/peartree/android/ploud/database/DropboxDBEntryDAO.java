package com.peartree.android.ploud.database;


import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.peartree.android.ploud.database.DropboxDBContract.Entry;
import com.peartree.android.ploud.database.DropboxDBEntryMapper.DropboxDBEntryCursorWrapper;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DropboxDBEntryDAO {

    private DropboxDBHelper mDbHelper;

    @Inject
    public DropboxDBEntryDAO(DropboxDBHelper dbHelper) {
        this.mDbHelper = dbHelper;
    }

    public long insertOrReplace(DropboxDBEntry entry) {

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        long id = db.insertWithOnConflict(Entry.TABLE_NAME, null, DropboxDBEntryMapper.toContentValues(entry), SQLiteDatabase.CONFLICT_REPLACE);

        // TODO Is this a good idea?
        assert(entry.getId() == 0 || entry.getId() == id);

        return id;
    }

    public DropboxDBEntry findById(long id) {

        SQLiteDatabase db = mDbHelper.getReadableDatabase();

        Cursor results;
        String selection = Entry._ID + " = ?";
        String[] selectionArgs = { Long.toString(id) };

        results = db.query(Entry.TABLE_NAME,null,selection,selectionArgs,null,null,null,"1");

        if (results.getCount() > 0) {
            results.moveToFirst();
            return new DropboxDBEntryCursorWrapper(results);
        } else {
            return null;
        }

    }

    public DropboxDBEntryMapper.DropboxDBEntryCursorWrapper findByParentDir(String parentDir) {

        SQLiteDatabase db = mDbHelper.getReadableDatabase();

        Cursor results;
        String selection = Entry.COLUMN_NAME_PARENT_DIR + " = ?";
        String[] selectionArgs = { parentDir };

        results = db.query(Entry.TABLE_NAME,null,selection,selectionArgs,null,null,null);

        return new DropboxDBEntryCursorWrapper(results);
    }

    public int deleteById(long id) {

        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        String selection = Entry._ID + " = ?";
        String[] selectionArgs = { Long.toString(id) };

        return db.delete(Entry.TABLE_NAME,selection,selectionArgs);

    }

    public int deleteByParentDirAndFilename(String parentDir, String filename) {

        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        String selection = Entry.COLUMN_NAME_PARENT_DIR+" = ? AND "+Entry.COLUMN_NAME_FILENAME+" = ?";
        String[] selectionArgs = { parentDir, filename };

        return db.delete(Entry.TABLE_NAME,selection,selectionArgs);

    }
}
