package com.peartree.android.kiteplayer.database;


import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.peartree.android.kiteplayer.database.DropboxDBContract.Song;
import com.peartree.android.kiteplayer.database.DropboxDBSongMapper.DropboxDBSongCursorWrapper;
import com.peartree.android.kiteplayer.utils.LogHelper;

import org.w3c.dom.Text;

import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.ArrayList;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;

@Singleton
public class DropboxDBSongDAO {

    private static final String TAG = LogHelper.makeLogTag(DropboxDBSongDAO.class);
    private DropboxDBHelper mDbHelper;

    @Inject
    public DropboxDBSongDAO(DropboxDBHelper dbHelper) {
        this.mDbHelper = dbHelper;
    }

    public long insertOrReplace(DropboxDBSong song) {

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        long id = db.insertWithOnConflict(
                Song.TABLE_NAME,
                null,
                DropboxDBSongMapper.toContentValues(song),
                SQLiteDatabase.CONFLICT_REPLACE);

        song.setId(id);

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

    public DropboxDBSong findByEntryId(long id) {

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

    public Observable<DropboxDBSong> query(
            @Nullable String genre,
            @Nullable String artist,
            @Nullable String album,
            @Nullable String title) {

        LogHelper.d(TAG,
                "query - Query with genre=",genre,
                ", artist=",artist,", album=",album,", title=",title);

        SQLiteDatabase db = mDbHelper.getReadableDatabase();

        String selection = "";
        ArrayList<String> selectionArgs = new ArrayList<>();

        if (genre != null) {
            selection += Song.COLUMN_NAME_GENRE+" MATCH ?";
            selectionArgs.add(genre);
        }

        if (artist != null) {
            selection += selectionArgs.size()>0?" AND ":"";
            selection += Song.COLUMN_NAME_ARTIST+" MATCH ?";
            selectionArgs.add(artist);
        }

        if (album != null) {
            selection += selectionArgs.size()>0?" AND ":"";
            selection += Song.COLUMN_NAME_ALBUM+" MATCH ?";
            selectionArgs.add(album);
        }

        if (title != null) {
            selection += selectionArgs.size()>0?" AND ":"";
            selection += Song.COLUMN_NAME_TITLE+" MATCH ?";
            selectionArgs.add(title);
        }

        // No keywords provided. Match nothing.
        if (TextUtils.isEmpty(selection)) {
            return Observable.empty();
        }

        Cursor results = db.query(
                true, Song.FTS4_TABLE_NAME, null,
                selection, selectionArgs.toArray(new String[selectionArgs.size()]),
                null, null, null, null);

        LogHelper.d(TAG,
                "query - Query with genre=",genre,
                ", artist=",artist,", album=",album,", title=",title,
                " returned ",results.getCount()," matches.");

        return new DropboxDBSongCursorWrapper(results).getObservable();
    }

    public Observable<DropboxDBSong> queryByKeyword(String query) {

        LogHelper.d(TAG,"queryByKeyword - Query with: ",query);

        SQLiteDatabase db = mDbHelper.getReadableDatabase();

        String selection = Song.FTS4_TABLE_NAME+" MATCH ?";
        String[] selectionArgs = new String[] {query};

        Cursor results =
                db.query(true, Song.FTS4_TABLE_NAME, null,
                        selection, selectionArgs,
                        null, null, null, null);

        LogHelper.d(TAG,
                "queryByKeyword - Query with term=",query,
                " returned ",results.getCount()," matches.");

        return new DropboxDBSongCursorWrapper(results).getObservable();
    }
}
