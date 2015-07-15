package com.peartree.android.ploud.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.CursorWrapper;

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

    private static boolean areEqual(DropboxDBEntry _this, DropboxDBEntry _that) {

        if (_this.getId() != _that.getId()) return false;
        if (_this.isDir() != _that.isDir()) return false;
        if (_this.getBytes() != _that.getBytes()) return false;
        if (_this.thumbExists() != _that.thumbExists()) return false;
        if (!_this.getRoot().equals(_that.getRoot())) return false;
        if (!_this.getParentDir().equals(_that.getParentDir())) return false;
        if (!_this.getFilename().equals(_that.getFilename())) return false;
        if (_this.getSize() != null ? !_this.getSize().equals(_that.getSize()) : _that.getSize() != null)
            return false;
        if (_this.getModified() != null ? !_this.getModified().equals(_that.getModified()) : _that.getModified() != null)
            return false;
        if (_this.getClientMtime() != null ? !_this.getClientMtime().equals(_that.getClientMtime()) : _that.getClientMtime() != null)
            return false;
        if (_this.getRev() != null ? !_this.getRev().equals(_that.getRev()) : _that.getRev() != null)
            return false;
        if (_this.getHash() != null ? !_this.getHash().equals(_that.getHash()) : _that.getHash() != null)
            return false;
        if (_this.getMimeType() != null ? !_this.getMimeType().equals(_that.getMimeType()) : _that.getMimeType() != null)
            return false;
        return !(_this.getIcon() != null ? !_this.getIcon().equals(_that.getIcon()) : _that.getIcon() != null);
    }

    public static class DropboxDBEntryPojo implements DropboxDBEntry {

        private long id;

        private boolean isDir;
        private String root;
        private String parentDir;
        private String filename;

        private String size;
        private long bytes;

        private String modified;
        private String clientMtime;

        private String rev;
        private String hash;

        private String mimeType;
        private String icon;
        private boolean thumbExists;

        @Override
        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        @Override
        public boolean isDir() {
            return isDir;
        }

        public void setIsDir(boolean isDir) {
            this.isDir = isDir;
        }

        @Override
        public String getRoot() {
            return root;
        }

        public void setRoot(String root) {
            this.root = root;
        }

        @Override
        public String getParentDir() {
            return parentDir;
        }

        public void setParentDir(String parentDir) {
            this.parentDir = parentDir;
        }

        @Override
        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        @Override
        public String getSize() {
            return size;
        }

        public void setSize(String size) {
            this.size = size;
        }

        @Override
        public long getBytes() {
            return bytes;
        }

        public void setBytes(long bytes) {
            this.bytes = bytes;
        }

        @Override
        public String getModified() {
            return modified;
        }

        public void setModified(String modified) {
            this.modified = modified;
        }

        @Override
        public String getClientMtime() {
            return clientMtime;
        }

        public void setClientMtime(String clientMtime) {
            this.clientMtime = clientMtime;
        }

        @Override
        public String getRev() {
            return rev;
        }

        public void setRev(String rev) {
            this.rev = rev;
        }

        @Override
        public String getHash() {
            return hash;
        }

        public void setHash(String hash) {
            this.hash = hash;
        }

        @Override
        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }

        @Override
        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        @Override
        public boolean thumbExists() {
            return thumbExists;
        }

        public void setThumbExists(boolean thumbExists) {
            this.thumbExists = thumbExists;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DropboxDBEntry)) return false;

            return areEqual(this,(DropboxDBEntry)o);

        }

        @Override
        public int hashCode() {
            int result = (int) (id ^ (id >>> 32));
            result = 31 * result + (isDir ? 1 : 0);
            result = 31 * result + root.hashCode();
            result = 31 * result + parentDir.hashCode();
            result = 31 * result + filename.hashCode();
            result = 31 * result + (size != null ? size.hashCode() : 0);
            result = 31 * result + (int) (bytes ^ (bytes >>> 32));
            result = 31 * result + (modified != null ? modified.hashCode() : 0);
            result = 31 * result + (clientMtime != null ? clientMtime.hashCode() : 0);
            result = 31 * result + (rev != null ? rev.hashCode() : 0);
            result = 31 * result + (hash != null ? hash.hashCode() : 0);
            result = 31 * result + (mimeType != null ? mimeType.hashCode() : 0);
            result = 31 * result + (icon != null ? icon.hashCode() : 0);
            result = 31 * result + (thumbExists ? 1 : 0);
            return result;
        }
    }

    public static class DropboxDBEntryCursorWrapper extends CursorWrapper implements DropboxDBEntry {

        private final Cursor mCursor;

        public DropboxDBEntryCursorWrapper(Cursor cursor) {
            super(cursor);

            this.mCursor = cursor;
        }

        @Override
        public long getId() {
            return mCursor.getLong(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry._ID));
        }

        @Override
        public boolean isDir() {
            return (mCursor.getInt(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry.COLUMN_NAME_IS_DIR)) == 1);
        }

        @Override
        public String getRoot() {
            return mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry.COLUMN_NAME_ROOT));
        }

        @Override
        public String getParentDir() {
            return mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry.COLUMN_NAME_PARENT_DIR));
        }

        @Override
        public String getFilename() {
            return mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry.COLUMN_NAME_FILENAME));
        }

        @Override
        public String getSize() {
            return mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry.COLUMN_NAME_SIZE));
        }

        @Override
        public long getBytes() {
            return mCursor.getLong(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry.COLUMN_NAME_BYTES));
        }

        @Override
        public String getModified() {
            return mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry.COLUMN_NAME_MODIFIED));
        }

        @Override
        public String getClientMtime() {
            return mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry.COLUMN_NAME_CLIENT_MTIME));
        }

        @Override
        public String getRev() {
            return mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry.COLUMN_NAME_REV));
        }

        @Override
        public String getHash() {
            return mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry.COLUMN_NAME_HASH));
        }

        @Override
        public String getMimeType() {
            return mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry.COLUMN_NAME_MIME_TYPE));
        }

        @Override
        public String getIcon() {
            return mCursor.getString(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry.COLUMN_NAME_ICON));
        }

        @Override
        public boolean thumbExists() {
            return (mCursor.getInt(mCursor.getColumnIndexOrThrow(DropboxDBContract.Entry.COLUMN_NAME_THUMB_EXISTS)) == 1);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof DropboxDBEntryCursorWrapper) {

                DropboxDBEntryCursorWrapper that = (DropboxDBEntryCursorWrapper) o;

                return mCursor.equals(that.mCursor);

            } else if (o instanceof DropboxDBEntry) {
                return areEqual(this,(DropboxDBEntry)o);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return mCursor.hashCode();
        }
    }
}
