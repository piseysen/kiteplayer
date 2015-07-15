package com.peartree.android.ploud.database;

public interface DropboxDBEntry {

    long getId();
    boolean isDir();
    String getRoot();
    String getParentDir();
    String getFilename();
    String getSize();
    long getBytes();
    String getModified();
    String getClientMtime();
    String getRev();
    String getHash();
    String getMimeType();
    String getIcon();
    boolean thumbExists();
}
