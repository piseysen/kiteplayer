package com.peartree.android.ploud.database;


import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.peartree.android.ploud.database.DropboxDBContract.Song;
import com.peartree.android.ploud.database.DropboxDBSongMapper.DropboxDBSongCursorWrapper;

import java.net.MalformedURLException;
import java.text.ParseException;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DropboxDBSongDAO {

    private DropboxDBHelper mDbHelper;

    @Inject
    public DropboxDBSongDAO(DropboxDBHelper dbHelper) {
        this.mDbHelper = dbHelper;
    }

    public long insertOrReplace(DropboxDBSong song) {

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        long id = db.insertWithOnConflict(Song.TABLE_NAME, null, DropboxDBSongMapper.toContentValues(song), SQLiteDatabase.CONFLICT_REPLACE);

        // TODO Is this a good idea?
        assert(song.getId() == 0 || song.getId() == id);

        return id;
    }

    public DropboxDBSong findById(long id) throws MalformedURLException, ParseException {

        SQLiteDatabase db = mDbHelper.getReadableDatabase();

        Cursor results;
        String selection = Song._ID + " = ?";
        String[] selectionArgs = { Long.toString(id) };

        results = db.query(Song.TABLE_NAME,null,selection,selectionArgs,null,null,null,"1");

        if (results.getCount() > 0) {
            results.moveToFirst();
            return new DropboxDBSongCursorWrapper(results).getSong();
        } else {
            return null;
        }

    }

    public DropboxDBSong findByEntryId(long id) throws MalformedURLException, ParseException {

        SQLiteDatabase db = mDbHelper.getReadableDatabase();

        Cursor results;
        String selection = Song.COLUMN_NAME_ENTRY_ID + " = ?";
        String[] selectionArgs = { Long.toString(id) };

        results = db.query(Song.TABLE_NAME,null,selection,selectionArgs,null,null,null,"1");

        if (results.getCount() > 0) {
            results.moveToFirst();
            return new DropboxDBSongCursorWrapper(results).getSong();
        } else {
            return null;
        }

    }

    public int deleteById(long id) {

        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        String selection = Song._ID + " = ?";
        String[] selectionArgs = { Long.toString(id) };

        return db.delete(Song.TABLE_NAME,selection,selectionArgs);

    }

}
