/*
 * Copyright (c) 2015 Rafael Pereira
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 *     https://mozilla.org/MPL/2.0/.
 */

package com.misterpereira.android.kiteplayer.database;


import android.annotation.SuppressLint;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.misterpereira.android.kiteplayer.database.DropboxDBContract.Entry;
import com.misterpereira.android.kiteplayer.database.DropboxDBEntryMapper.DropboxDBEntryCursorWrapper;
import com.misterpereira.android.kiteplayer.utils.LogHelper;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;

@Singleton
public class DropboxDBEntryDAO {

    private static final String TAG = LogHelper.makeLogTag(DropboxDBEntryDAO.class);

    private final DropboxDBHelper mDbHelper;

    @Inject
    public DropboxDBEntryDAO(DropboxDBHelper dbHelper) {
        this.mDbHelper = dbHelper;
    }

    public long insertOrReplace(DropboxDBEntry entry) {

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        long id = db.insertWithOnConflict(
                Entry.TABLE_NAME,
                null,
                DropboxDBEntryMapper.toContentValues(entry),
                SQLiteDatabase.CONFLICT_REPLACE);

        entry.setId(id);
        LogHelper.d(TAG, "Inserted/Updated entry with id=", id, " and path=", entry.getFullPath());

        return id;
    }

    @SuppressLint("Recycle")
    public DropboxDBEntry findById(long id) {

        SQLiteDatabase db = mDbHelper.getReadableDatabase();

        Cursor results;
        String selection = Entry._ID + " = ?";
        String[] selectionArgs = {Long.toString(id)};

        results = db.query(Entry.TABLE_NAME, null, selection, selectionArgs, null, null, null, "1");

        LogHelper.d(TAG, "Found ", results.getCount(), " entries with id=", id);

        if (results.getCount() > 0) {
            results.moveToFirst();
            return new DropboxDBEntryCursorWrapper(results).getEntry();
        } else {
            return null;
        }

    }

    public Observable<DropboxDBEntry> findByParentDir(String parentDir) {
        return findByParentDir(parentDir, true);

    }

    @SuppressLint("Recycle")
    public Observable<DropboxDBEntry> findByParentDir(String parentDir, boolean excludeEmpty) {

        SQLiteDatabase db = mDbHelper.getReadableDatabase();

        Cursor results;

        String excludeEmptyClause =
                "(NOT " + Entry.COLUMN_NAME_IS_DIR +
                        " OR EXISTS (" +
                        "SELECT child." + Entry._ID + " FROM " + Entry.TABLE_NAME + " AS child WHERE " +
                        "NOT child." + Entry.COLUMN_NAME_IS_DIR + " AND " +
                        "child." + Entry.COLUMN_NAME_PARENT_DIR + " LIKE " +
                        "parent." + Entry.COLUMN_NAME_PARENT_DIR +
                        " || parent." + Entry.COLUMN_NAME_FILENAME + " || '/%' COLLATE NOCASE)) ";

        String query =
                "SELECT parent.* FROM " +
                        Entry.TABLE_NAME + " AS parent WHERE " +
                        "parent." + Entry.COLUMN_NAME_PARENT_DIR + " = ? COLLATE NOCASE " +
                        (excludeEmpty ? "AND " + excludeEmptyClause : "") +
                        "ORDER BY parent." + Entry.COLUMN_NAME_IS_DIR + " DESC, " +
                        "parent." + Entry.COLUMN_NAME_FILENAME + " ASC";

        String[] selectionArgs = {parentDir.endsWith("/") ? parentDir : (parentDir + "/")};

        results = db.rawQuery(query, selectionArgs);

        LogHelper.d(TAG,
                "Found ", results.getCount(), " entries for parentDir=", parentDir,
                (excludeEmpty ? " excluding" : " including"), " empty directories");

        return new DropboxDBEntryCursorWrapper(results).getObservable();
    }

    public Observable<DropboxDBEntry> findRandom(int count) {

        SQLiteDatabase db = mDbHelper.getReadableDatabase();

        @SuppressLint("Recycle") Cursor results = db.query(
                Entry.TABLE_NAME,
                null, null, null, null, null,
                "RANDOM()",
                String.valueOf(count));

        LogHelper.d(TAG,
                "Found ", results.getCount(), " entries for random count=", count);

        return new DropboxDBEntryCursorWrapper(results).getObservable();
    }

    public Observable<DropboxDBEntry> queryByFilenameKeyword(String query) {

        LogHelper.d(TAG,"queryByFilenameKeyword - Query with: ",query);

        SQLiteDatabase db = mDbHelper.getReadableDatabase();

        String selection = DropboxDBContract.Entry.FTS4_TABLE_NAME+" MATCH ? AND " +
                "NOT "+Entry.COLUMN_NAME_IS_DIR;
        String[] selectionArgs = new String[] {query};

        @SuppressLint("Recycle") Cursor results = db.rawQuery(
                "SELECT e.* " +
                        "FROM " + Entry.TABLE_NAME + " AS e " +
                        "INNER JOIN " + Entry.FTS4_TABLE_NAME + " AS ei " +
                        "ON e.rowid = ei.docid " +
                        "WHERE " + selection,
                selectionArgs);

        LogHelper.d(TAG,
                "queryByFilenameKeyword - Query with term=",query,
                " returned ",results.getCount()," matches.");

        return new DropboxDBEntryMapper.DropboxDBEntryCursorWrapper(results).getObservable();
    }

    public int deleteById(long id) {

        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        String selection = Entry._ID + " = ?";
        String[] selectionArgs = {Long.toString(id)};

        int deleted = db.delete(Entry.TABLE_NAME, selection, selectionArgs);

        LogHelper.d(TAG, "Deleted ", deleted, " entries with id=", id);

        return deleted;
    }

    public int deleteByParentDirAndFilename(String parentDir, String filename) {

        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        String selection = Entry.COLUMN_NAME_PARENT_DIR + " = ? COLLATE NOCASE AND "
                + Entry.COLUMN_NAME_FILENAME + " = ? COLLATE NOCASE";
        String[] selectionArgs = {parentDir, filename};

        int deleted = db.delete(Entry.TABLE_NAME, selection, selectionArgs);

        LogHelper.d(TAG,
                "Deleted ", deleted, " entries for parentDir=", parentDir,
                " and filename=", filename);

        return deleted;
    }

    public int deleteTreeByAncestorDir(String ancestorPath) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        int lastSeparator = ancestorPath.lastIndexOf("/");
        String ancestorParent =
                lastSeparator >= 0 ? ancestorPath.substring(0, lastSeparator + 1) : null;
        String ancestorName =
                lastSeparator >= 0 ? ancestorPath.substring(lastSeparator + 1) : ancestorPath;

        String selection = "(" + Entry.COLUMN_NAME_PARENT_DIR + " = ? COLLATE NOCASE AND " +
                Entry.COLUMN_NAME_FILENAME + " = ? COLLATE NOCASE) OR " +
                Entry.COLUMN_NAME_PARENT_DIR + " LIKE ? COLLATE NOCASE";
        String[] selectionArgs = {ancestorParent, ancestorName, ancestorPath + "%"};

        int deleted = db.delete(Entry.TABLE_NAME, selection, selectionArgs);

        LogHelper.d(TAG, "Deleted ", deleted, " entries for ancestorPath=", ancestorPath, " tree");

        return deleted;
    }

    public int deleteAll() {

        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        int deleted = db.delete(Entry.TABLE_NAME,null,null);

        LogHelper.d(TAG,"Deleted all ", deleted, " entries.");

        return deleted;
    }


}
