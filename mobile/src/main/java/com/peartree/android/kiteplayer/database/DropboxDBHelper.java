package com.peartree.android.kiteplayer.database;

import android.app.Application;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.peartree.android.kiteplayer.database.DropboxDBContract.Entry;
import com.peartree.android.kiteplayer.database.DropboxDBContract.Song;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DropboxDBHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "dropbox";
    public static final int DATABASE_VERSION = 1;

    private static final String CREATE_ENTRY_TABLE =
            "CREATE TABLE " + Entry.TABLE_NAME + "(" +
                Entry._ID + " INTEGER PRIMARY KEY," +
                Entry.COLUMN_NAME_IS_DIR + " BOOLEAN NOT NULL," +
                Entry.COLUMN_NAME_ROOT + " VARCHAR NOT NULL," +
                Entry.COLUMN_NAME_PARENT_DIR + " VARCHAR NOT NULL," +
                Entry.COLUMN_NAME_FILENAME + " VARCHAR NOT NULL," +

                Entry.COLUMN_NAME_BYTES + " INTEGER," +
                Entry.COLUMN_NAME_SIZE + " VARCHAR," +

                Entry.COLUMN_NAME_MODIFIED +  " VARCHAR," +
                Entry.COLUMN_NAME_CLIENT_MTIME + " VARCHAR," +
                Entry.COLUMN_NAME_REV +  " VARCHAR," +
                Entry.COLUMN_NAME_HASH + " VARCHAR," +

                Entry.COLUMN_NAME_MIME_TYPE +  " VARCHAR," +
                Entry.COLUMN_NAME_ICON +  " VARCHAR," +
                Entry.COLUMN_NAME_THUMB_EXISTS +  " BOOLEAN,   " +

                "CONSTRAINT uq_canonical_name UNIQUE (" +
                    Entry.COLUMN_NAME_PARENT_DIR + "," +
                    Entry.COLUMN_NAME_FILENAME + "))";

    private static final String CREATE_PARENT_DIR_INDEX =
            "CREATE INDEX parent_dir ON "+
                    Entry.TABLE_NAME+"("+Entry.COLUMN_NAME_PARENT_DIR+
                    ") WHERE "+Entry.COLUMN_NAME_IS_DIR;

    private static final String CREATE_SONG_TABLE =
            "CREATE TABLE " + Song.TABLE_NAME + "(" +
                    Song._ID + " INTEGER PRIMARY KEY," +
                    Song.COLUMN_NAME_DOWNLOAD_URL + " VARCHAR," +
                    Song.COLUMN_NAME_DOWNLOAD_URL_EXPIRATION + " VARCHAR," +

                    Song.COLUMN_NAME_ALBUM + " VARCHAR," +
                    Song.COLUMN_NAME_ALBUM_ARTIST + " VARCHAR," +
                    Song.COLUMN_NAME_ARTIST + " VARCHAR," +
                    Song.COLUMN_NAME_GENRE + " VARCHAR," +
                    Song.COLUMN_NAME_TITLE + " VARCHAR," +

                    Song.COLUMN_NAME_DURATION + " INTEGER," +
                    Song.COLUMN_NAME_TRACK_NUMBER + " INTEGER," +
                    Song.COLUMN_NAME_TOTAL_TRACKS + " INTEGER," +

                    Song.COLUMN_NAME_ENTRY_ID + " INTEGER NOT NULL," +

                    "FOREIGN KEY ("+
                        Song.COLUMN_NAME_ENTRY_ID+") REFERENCES "+
                        Entry.TABLE_NAME+"("+Entry._ID+") ON UPDATE CASCADE ON DELETE CASCADE,"+

                    "CONSTRAINT uq_entry_id UNIQUE ("+
                        Song.COLUMN_NAME_ENTRY_ID+"))";

    @Inject
    public DropboxDBHelper(Application app) {
        super(app.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        db.execSQL(CREATE_ENTRY_TABLE);
        db.execSQL(CREATE_SONG_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
