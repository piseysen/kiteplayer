package com.peartree.android.ploud.database;

import android.app.Application;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.peartree.android.ploud.database.DropboxDBContract.Entry;

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

    @Inject
    public DropboxDBHelper(Application app) {
        super(app.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_ENTRY_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
