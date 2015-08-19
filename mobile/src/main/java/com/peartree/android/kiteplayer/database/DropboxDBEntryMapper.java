package com.peartree.android.kiteplayer.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.CursorWrapper;

import rx.Observable;

public class DropboxDBEntryMapper {

    public static ContentValues toContentValues(DropboxDBEntry entry) {
        ContentValues cv = new ContentValues();

        if (entry.getId() != 0) // Equivalent to a null id
            cv.put(DropboxDBContract.Entry._ID,entry.getId());

        cv.put(DropboxDBContract.Entry.COLUMN_NAME_IS_DIR,entry.isDir());
        cv.put(DropboxDBContract.Entry.COLUMN_NAME_ROOT,entry.getRoot());
        cv.put(DropboxDBContract.Entry.COLUMN_NAME_PARENT_DIR,entry.getParentDir());
        cv.put(DropboxDBContract.Entry.COLUMN_NAME_FILENAME,entry.getFilename());

        cv.put(DropboxDBContract.Entry.COLUMN_NAME_BYTES,entry.getBytes());
        cv.put(DropboxDBContract.Entry.COLUMN_NAME_SIZE,entry.getSize());

        cv.put(DropboxDBContract.Entry.COLUMN_NAME_MODIFIED,entry.getModified());
        cv.put(DropboxDBContract.Entry.COLUMN_NAME_CLIENT_MTIME,entry.getClientMtime());
        cv.put(DropboxDBContract.Entry.COLUMN_NAME_REV,entry.getRev());
        cv.put(DropboxDBContract.Entry.COLUMN_NAME_HASH,entry.getHash());

        cv.put(DropboxDBContract.Entry.COLUMN_NAME_MIME_TYPE,entry.getMimeType());
        cv.put(DropboxDBContract.Entry.COLUMN_NAME_ICON,entry.getIcon());
        cv.put(DropboxDBContract.Entry.COLUMN_NAME_THUMB_EXISTS,entry.thumbExists());

        return cv;
    }

    public static class DropboxDBEntryCursorWrapper extends CursorWrapper {

        private final Cursor mCursor;

        public DropboxDBEntryCursorWrapper(Cursor cursor) {
            super(cursor);

            this.mCursor = cursor;
        }

        public DropboxDBEntry getEntry() {

            DropboxDBEntry entry = new DropboxDBEntry();

            entry.setId(mCursor.getLong(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry._ID)));

            entry.setIsDir(mCursor.getInt(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry.COLUMN_NAME_IS_DIR)) == 1);
            entry.setRoot(mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry.COLUMN_NAME_ROOT)));
            entry.setParentDir(mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry.COLUMN_NAME_PARENT_DIR)));
            entry.setFilename(mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry.COLUMN_NAME_FILENAME)));

            entry.setSize(mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry.COLUMN_NAME_SIZE)));
            entry.setBytes(mCursor.getLong(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry.COLUMN_NAME_BYTES)));

            entry.setModified(mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry.COLUMN_NAME_MODIFIED)));
            entry.setClientMtime(mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry.COLUMN_NAME_CLIENT_MTIME)));
            entry.setRev(mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry.COLUMN_NAME_REV)));
            entry.setHash(mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry.COLUMN_NAME_HASH)));

            entry.setMimeType(mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry.COLUMN_NAME_MIME_TYPE)));
            entry.setIcon(mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry.COLUMN_NAME_ICON)));
            entry.setThumbExists(mCursor.getInt(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry.COLUMN_NAME_THUMB_EXISTS)) == 1);

            return entry;
        }

        public Observable<DropboxDBEntry> getObservable() {
            return Observable.create(subscriber -> {
                try {

                    if (this.isClosed()) {
                        subscriber.onError(new IllegalStateException("Cursor already closed."));
                    }

                    if (this.getCount() > 0) {
                        this.moveToFirst();

                        do {
                            subscriber.onNext(this.getEntry());
                        } while (this.moveToNext());

                    }

                } finally {
                    this.close();
                    subscriber.onCompleted();
                }
            });
        }

    }
}
