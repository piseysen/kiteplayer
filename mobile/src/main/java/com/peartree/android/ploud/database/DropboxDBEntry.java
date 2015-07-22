package com.peartree.android.ploud.database;

import android.support.annotation.NonNull;

public class DropboxDBEntry {

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

    private DropboxDBSong song;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public boolean isDir() {
        return isDir;
    }

    public void setIsDir(boolean isDir) {
        this.isDir = isDir;
    }

    public String getRoot() {
        return root;
    }

    public void setRoot(@NonNull String root) {
        this.root = root;
    }

    public String getParentDir() {
        return parentDir;
    }

    public void setParentDir(@NonNull String parentDir) {
        this.parentDir = parentDir;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(@NonNull String filename) {
        this.filename = filename;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public long getBytes() {
        return bytes;
    }

    public void setBytes(long bytes) {
        this.bytes = bytes;
    }

    public String getModified() {
        return modified;
    }

    public void setModified(String modified) {
        this.modified = modified;
    }

    public String getClientMtime() {
        return clientMtime;
    }

    public void setClientMtime(String clientMtime) {
        this.clientMtime = clientMtime;
    }

    public String getRev() {
        return rev;
    }

    public void setRev(String rev) {
        this.rev = rev;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public boolean thumbExists() {
        return thumbExists;
    }

    public void setThumbExists(boolean thumbExists) {
        this.thumbExists = thumbExists;
    }

    public DropboxDBSong getSong() { return song; }

    public void setSong(DropboxDBSong song) { this.song = song; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DropboxDBEntry)) return false;

        return areEqual(this,(DropboxDBEntry)o);

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
