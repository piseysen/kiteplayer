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
                    Song.COLUMN_NAME_HAS_LATEST_METADATA + " BOOLEAN," +
                    Song.COLUMN_NAME_HAS_VALID_ALBUM_ART + " BOOLEAN DEFAULT 1," +

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

    private static final String CREATE_ENTRY_FTS4 =
            "CREATE VIRTUAL TABLE " + Entry.FTS4_TABLE_NAME + " " +
                    "USING fts4(" +
                    "content=\"" + Entry.TABLE_NAME + "\"," +
                    Entry.COLUMN_NAME_PARENT_DIR + ", " +
                    Entry.COLUMN_NAME_FILENAME + ")";

    private static final String CREATE_ENTRY_BU_TRIGGER =
            "CREATE TRIGGER entry_bu " +
                    "BEFORE UPDATE ON " + Entry.TABLE_NAME + " "+
                    "BEGIN\n" +
                        "DELETE FROM " + Entry.FTS4_TABLE_NAME + " WHERE docid=old.rowid;\n" +
                    "END;";

    private static final String CREATE_ENTRY_BD_TRIGGER =
            "CREATE TRIGGER entry_bd " +
                    "BEFORE DELETE ON " + Entry.TABLE_NAME + " " +
                    "BEGIN\n" +
                        "DELETE FROM " + Entry.FTS4_TABLE_NAME + " WHERE docid=old.rowid;\n" +
                    "END;";

    private static final String CREATE_ENTRY_AU_TRIGGER =
            "CREATE TRIGGER entry_au " +
                    "AFTER UPDATE ON " + Entry.TABLE_NAME + " " +
                    "BEGIN\n" +
                        "INSERT INTO " + Entry.FTS4_TABLE_NAME +
                            "(docid, " +
                                Entry.COLUMN_NAME_PARENT_DIR + ", " +
                                Entry.COLUMN_NAME_FILENAME + ") " +
                        "VALUES(new.rowid, " +
                            "new." + Entry.COLUMN_NAME_PARENT_DIR + ", " +
                            "new." + Entry.COLUMN_NAME_FILENAME + ");\n" +
                    "END;";

    private static final String CREATE_ENTRY_AI_TRIGGER =
            "CREATE TRIGGER entry_ai " +
                    "AFTER INSERT ON " + Entry.TABLE_NAME + " " +
                    "BEGIN\n" +
                        "INSERT INTO " + Entry.FTS4_TABLE_NAME +
                            "(docid, " +
                            Entry.COLUMN_NAME_PARENT_DIR + ", " +
                            Entry.COLUMN_NAME_FILENAME + ") " +
                        "VALUES(new.rowid, " +
                            "new." + Entry.COLUMN_NAME_PARENT_DIR + ", " +
                            "new." + Entry.COLUMN_NAME_FILENAME + ");\n" +
                    "END;";

    private static final String CREATE_SONG_FTS4 =
            "CREATE VIRTUAL TABLE " + Song.FTS4_TABLE_NAME + " " +
                    "USING fts4(" +
                        "content=\"" + Song.TABLE_NAME + "\", " +
                            Song.COLUMN_NAME_GENRE + ", " +
                            Song.COLUMN_NAME_ARTIST + ", " +
                            Song.COLUMN_NAME_ALBUM + ", " +
                            Song.COLUMN_NAME_TITLE + ")";

    private static final String CREATE_SONG_BU_TRIGGER =
            "CREATE TRIGGER song_bu " +
                    "BEFORE UPDATE ON " + Song.TABLE_NAME + " "+
                    "BEGIN\n" +
                        "DELETE FROM " + Song.FTS4_TABLE_NAME + " WHERE docid=old.rowid;\n" +
                    "END;";

    private static final String CREATE_SONG_BD_TRIGGER =
            "CREATE TRIGGER song_bd " +
                    "BEFORE DELETE ON " + Song.TABLE_NAME + " " +
                    "BEGIN\n" +
                        "DELETE FROM " + Song.FTS4_TABLE_NAME + " WHERE docid=old.rowid;\n" +
                    "END;";

    private static final String CREATE_SONG_AU_TRIGGER =
            "CREATE TRIGGER song_au " +
                    "AFTER UPDATE ON " + Song.TABLE_NAME + " " +
                    "BEGIN\n" +
                        "INSERT INTO " + Song.FTS4_TABLE_NAME +
                            "(docid, " +
                            Song.COLUMN_NAME_GENRE + ", " +
                            Song.COLUMN_NAME_ARTIST + ", " +
                            Song.COLUMN_NAME_ALBUM + ", " +
                            Song.COLUMN_NAME_TITLE + ") " +
                        "VALUES(new.rowid, " +
                            "new." + Song.COLUMN_NAME_GENRE + ", " +
                            "new." + Song.COLUMN_NAME_ARTIST + ", " +
                            "new." + Song.COLUMN_NAME_ALBUM + ", " +
                            "new." + Song.COLUMN_NAME_TITLE + ");\n" +
                    "END;";

    private static final String CREATE_SONG_AI_TRIGGER =
            "CREATE TRIGGER song_ai " +
                    "AFTER INSERT ON " + Song.TABLE_NAME + " " +
                    "BEGIN\n" +
                        "INSERT INTO " + Song.FTS4_TABLE_NAME +
                            "(docid, " +
                            Song.COLUMN_NAME_GENRE + ", " +
                            Song.COLUMN_NAME_ARTIST + ", " +
                            Song.COLUMN_NAME_ALBUM + ", " +
                            Song.COLUMN_NAME_TITLE + ") " +
                        "VALUES(new.rowid, " +
                            "new." + Song.COLUMN_NAME_GENRE + ", " +
                            "new." + Song.COLUMN_NAME_ARTIST + ", " +
                            "new." + Song.COLUMN_NAME_ALBUM + ", " +
                            "new." + Song.COLUMN_NAME_TITLE + ");\n" +
                    "END;";

    @Inject
    public DropboxDBHelper(Application app) {
        super(app.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        db.execSQL(CREATE_ENTRY_TABLE);
        db.execSQL(CREATE_PARENT_DIR_INDEX);
        db.execSQL(CREATE_ENTRY_FTS4);
        db.execSQL(CREATE_ENTRY_BU_TRIGGER);
        db.execSQL(CREATE_ENTRY_BD_TRIGGER);
        db.execSQL(CREATE_ENTRY_AU_TRIGGER);
        db.execSQL(CREATE_ENTRY_AI_TRIGGER);
        db.execSQL(CREATE_SONG_TABLE);
        db.execSQL(CREATE_SONG_FTS4);
        db.execSQL(CREATE_SONG_BU_TRIGGER);
        db.execSQL(CREATE_SONG_BD_TRIGGER);
        db.execSQL(CREATE_SONG_AU_TRIGGER);
        db.execSQL(CREATE_SONG_AI_TRIGGER);

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
