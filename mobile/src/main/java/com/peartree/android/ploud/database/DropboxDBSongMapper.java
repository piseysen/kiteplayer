package com.peartree.android.ploud.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.CursorWrapper;

import com.peartree.android.ploud.utils.LogHelper;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;

public class DropboxDBSongMapper {

    public static ContentValues toContentValues(DropboxDBSong song) {
        ContentValues cv = new ContentValues();

        if (song.getId() != 0) // Equivalent to a null id
            cv.put(DropboxDBContract.Entry._ID,song.getId());

        if (song.getDownloadURL() != null)
            cv.put(DropboxDBContract.Song.COLUMN_NAME_DOWNLOAD_URL,song.getDownloadURL().toString());

        if (song.getDownloadURLExpiration() != null)
            cv.put(DropboxDBContract.Song.COLUMN_NAME_DOWNLOAD_URL_EXPIRATION, DateFormat.getDateTimeInstance().format(song.getDownloadURLExpiration()));

        cv.put(DropboxDBContract.Song.COLUMN_NAME_ALBUM,song.getAlbum());
        cv.put(DropboxDBContract.Song.COLUMN_NAME_ARTIST,song.getArtist());
        cv.put(DropboxDBContract.Song.COLUMN_NAME_GENRE,song.getGenre());
        cv.put(DropboxDBContract.Song.COLUMN_NAME_TITLE,song.getTitle());

        cv.put(DropboxDBContract.Song.COLUMN_NAME_DURATION,song.getDuration());
        cv.put(DropboxDBContract.Song.COLUMN_NAME_TRACK_NUMBER,song.getTrackNumber());
        cv.put(DropboxDBContract.Song.COLUMN_NAME_TOTAL_TRACKS,song.getTotalTracks());

        cv.put(DropboxDBContract.Song.COLUMN_NAME_ENTRY_ID,song.getEntryId());

        return cv;
    }

    public static class DropboxDBSongCursorWrapper extends CursorWrapper {

        private final Cursor mCursor;

        public DropboxDBSongCursorWrapper(Cursor cursor) {
            super(cursor);

            this.mCursor = cursor;
        }

        public DropboxDBSong getSong() throws MalformedURLException, ParseException {

            DropboxDBSong song = new DropboxDBSong();

            song.setId(mCursor.getLong(mCursor.getColumnIndexOrThrow(DropboxDBContract.Song._ID)));
            song.setDownloadURL(new URL(mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Song.COLUMN_NAME_DOWNLOAD_URL))));
            song.setDownloadURLExpiration(DateFormat.getDateTimeInstance().parse(mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Song.COLUMN_NAME_DOWNLOAD_URL_EXPIRATION))));

            song.setAlbum(mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Song.COLUMN_NAME_ALBUM)));
            song.setArtist(mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Song.COLUMN_NAME_ARTIST)));
            song.setGenre(mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Song.COLUMN_NAME_GENRE)));
            song.setTitle(mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Song.COLUMN_NAME_TITLE)));

            song.setDuration(mCursor.getLong(mCursor.getColumnIndexOrThrow(DropboxDBContract.Song.COLUMN_NAME_DURATION)));
            song.setTrackNumber(mCursor.getInt(mCursor.getColumnIndexOrThrow(DropboxDBContract.Song.COLUMN_NAME_TRACK_NUMBER)));
            song.setTotalTracks(mCursor.getInt(mCursor.getColumnIndexOrThrow(DropboxDBContract.Song.COLUMN_NAME_TOTAL_TRACKS)));

            song.setEntryId(mCursor.getLong(mCursor.getColumnIndexOrThrow(DropboxDBContract.Song.COLUMN_NAME_ENTRY_ID)));

            return song;
        }

    }
}
