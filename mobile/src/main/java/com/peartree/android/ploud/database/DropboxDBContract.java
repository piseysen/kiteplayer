package com.peartree.android.ploud.database;

import android.provider.BaseColumns;

public final class DropboxDBContract {

    // Never to be instantiated
    private DropboxDBContract() {}

    public static abstract class Entry implements BaseColumns {

        public static final String TABLE_NAME = "entry";

        public static final String COLUMN_NAME_IS_DIR = "is_dir";
        public static final String COLUMN_NAME_ROOT = "root";
        public static final String COLUMN_NAME_PARENT_DIR = "parent_dir";
        public static final String COLUMN_NAME_FILENAME = "filename";

        public static final String COLUMN_NAME_SIZE = "size";
        public static final String COLUMN_NAME_BYTES = "bytes";

        public static final String COLUMN_NAME_MODIFIED = "modified";
        public static final String COLUMN_NAME_CLIENT_MTIME = "client_mtime";
        public static final String COLUMN_NAME_REV = "rev";
        public static final String COLUMN_NAME_HASH = "hash";

        public static final String COLUMN_NAME_MIME_TYPE = "mime_type";
        public static final String COLUMN_NAME_ICON = "icon";
        public static final String COLUMN_NAME_THUMB_EXISTS = "thumb_exists";
    }
}
