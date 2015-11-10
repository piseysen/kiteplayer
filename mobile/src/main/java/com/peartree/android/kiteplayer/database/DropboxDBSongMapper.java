/*
 * Copyright (c) 2015 Rafael Pereira
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 *     https://mozilla.org/MPL/2.0/.
 */

package com.peartree.android.kiteplayer.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.CursorWrapper;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;

import rx.Observable;

public class DropboxDBSongMapper {

    public static ContentValues toContentValues(DropboxDBSong song) {
        ContentValues cv = new ContentValues();

        if (song.getId() != 0) // Equivalent to a null id
            cv.put(DropboxDBContract.Entry._ID,song.getId());

        if (song.getDownloadURL() != null)
            cv.put(DropboxDBContract.Song.COLUMN_NAME_DOWNLOAD_URL,song.getDownloadURL().toString());

        if (song.getDownloadURLExpiration() != null)
            cv.put(DropboxDBContract.Song.COLUMN_NAME_DOWNLOAD_URL_EXPIRATION, DateFormat.getDateTimeInstance().format(song.getDownloadURLExpiration()));

        cv.put(DropboxDBContract.Song.COLUMN_NAME_HAS_LATEST_METADATA, song.hasLatestMetadata());
        cv.put(DropboxDBContract.Song.COLUMN_NAME_HAS_VALID_ALBUM_ART, song.hasValidAlbumArt());

        cv.put(DropboxDBContract.Song.COLUMN_NAME_ALBUM,song.getAlbum());
        cv.put(DropboxDBContract.Song.COLUMN_NAME_ALBUM_ARTIST,song.getAlbumArtist());
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

        public DropboxDBSong getSong() {

            DropboxDBSong song = new DropboxDBSong();

            song.setId(mCursor.getLong(mCursor.getColumnIndexOrThrow(DropboxDBContract.Song._ID)));

            if (mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Song.COLUMN_NAME_DOWNLOAD_URL)) != null) {
                try {
                    song.setDownloadURL(new URL(mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Song.COLUMN_NAME_DOWNLOAD_URL))));
                    song.setDownloadURLExpiration(DateFormat.getDateTimeInstance().parse(mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Song.COLUMN_NAME_DOWNLOAD_URL_EXPIRATION))));
                } catch (MalformedURLException | ParseException e) {
                    song.setDownloadURL(null);
                    song.setDownloadURLExpiration(null);
                }
            }

            song.setHasLatestMetadata(mCursor.getInt(mCursor.getColumnIndexOrThrow(DropboxDBContract.Song.COLUMN_NAME_HAS_LATEST_METADATA)) > 0);
            song.setHasValidAlbumArt(mCursor.getInt(mCursor.getColumnIndexOrThrow(DropboxDBContract.Song.COLUMN_NAME_HAS_VALID_ALBUM_ART)) > 0);

            song.setAlbum(mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Song.COLUMN_NAME_ALBUM)));
            song.setAlbumArtist(mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Song.COLUMN_NAME_ALBUM_ARTIST)));
            song.setArtist(mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Song.COLUMN_NAME_ARTIST)));
            song.setGenre(mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Song.COLUMN_NAME_GENRE)));
            song.setTitle(mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Song.COLUMN_NAME_TITLE)));

            song.setDuration(mCursor.getLong(mCursor.getColumnIndexOrThrow(DropboxDBContract.Song.COLUMN_NAME_DURATION)));
            song.setTrackNumber(mCursor.getInt(mCursor.getColumnIndexOrThrow(DropboxDBContract.Song.COLUMN_NAME_TRACK_NUMBER)));
            song.setTotalTracks(mCursor.getInt(mCursor.getColumnIndexOrThrow(DropboxDBContract.Song.COLUMN_NAME_TOTAL_TRACKS)));

            song.setEntryId(mCursor.getLong(mCursor.getColumnIndexOrThrow(DropboxDBContract.Song.COLUMN_NAME_ENTRY_ID)));

            return song;
        }

        public Observable<DropboxDBSong> getObservable() {
            return Observable.create(subscriber -> {
                try {

                    if (this.isClosed()) {
                        throw new IllegalStateException("Cursor already closed.");
                    }

                    if (this.getCount() > 0) {
                        this.moveToFirst();

                        do {
                            subscriber.onNext(this.getSong());
                        } while (this.moveToNext() && !subscriber.isUnsubscribed());

                    }

                    if (!subscriber.isUnsubscribed()) {
                        subscriber.onCompleted();
                    }

                } catch (Exception e) {
                    if (!subscriber.isUnsubscribed()) {
                        subscriber.onError(e);
                    }
                } finally {
                    this.close();
                }
            });
        }

    }
}
