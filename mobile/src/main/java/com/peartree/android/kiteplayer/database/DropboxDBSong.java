/*
 * Copyright (c) 2015 Rafael Pereira
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 *     https://mozilla.org/MPL/2.0/.
 */

package com.peartree.android.kiteplayer.database;

import java.net.URL;
import java.util.Date;

public class DropboxDBSong {

    private long id;

    private URL downloadURL;
    private Date downloadURLExpiration;
    private boolean hasLatestMetadata;
    private boolean hasValidAlbumArt;

    private String album;
    private String albumArtist;
    private String artist;
    private String genre;
    private String title;

    private long duration;
    private int trackNumber;
    private int totalTracks;

    private long entryId;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public URL getDownloadURL() {
        return downloadURL;
    }

    public void setDownloadURL(URL downloadURL) {
        this.downloadURL = downloadURL;
    }

    public Date getDownloadURLExpiration() {
        return downloadURLExpiration;
    }

    public void setDownloadURLExpiration(Date downloadURLExpiration) {
        this.downloadURLExpiration = downloadURLExpiration;
    }

    public boolean hasLatestMetadata() {
        return hasLatestMetadata;
    }

    public void setHasLatestMetadata(boolean hasLatestMetadata) {
        this.hasLatestMetadata = hasLatestMetadata;
    }

    public boolean hasValidAlbumArt() {
        return hasValidAlbumArt;
    }

    public void setHasValidAlbumArt(boolean hasValidAlbumArt) {
        this.hasValidAlbumArt = hasValidAlbumArt;
    }

    public String getAlbumArtist() {
        return albumArtist;
    }

    public void setAlbumArtist(String albumArtist) {
        this.albumArtist = albumArtist;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public int getTrackNumber() {
        return trackNumber;
    }

    public void setTrackNumber(int trackNumber) {
        this.trackNumber = trackNumber;
    }

    public int getTotalTracks() {
        return totalTracks;
    }

    public void setTotalTracks(int totalTracks) {
        this.totalTracks = totalTracks;
    }

    public long getEntryId() {
        return entryId;
    }

    public void setEntryId(long entryId) {
        this.entryId = entryId;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DropboxDBSong)) return false;

        return areEqual(this,(DropboxDBSong)o);
    }

    private static boolean areEqual(DropboxDBSong _this, DropboxDBSong _that) {

        if (_this.getId() != _that.getId()) return false;

        if (_this.getDownloadURL()!=null?!_this.getDownloadURL().equals(_that.getDownloadURL()):_that.getDownloadURL()!=null) return false;
        if (_this.getDownloadURLExpiration()!=null?!_this.getDownloadURLExpiration().equals(_that.getDownloadURLExpiration()):_that.getDownloadURLExpiration()!=null) return false;

        if (_this.getAlbum()!=null?!_this.getAlbum().equals(_that.getAlbum()):_that.getAlbum()!=null) return false;
        if (_this.getAlbumArtist()!=null?!_this.getAlbumArtist().equals(_that.getAlbumArtist()):_that.getAlbumArtist()!=null) return false;
        if (_this.getArtist()!=null?!_this.getArtist().equals(_that.getAlbum()):_that.getArtist()!=null) return false;
        if (_this.getGenre() !=null?!_this.getGenre().equals(_that.getGenre()):_that.getGenre() !=null) return false;
        if (_this.getTitle() !=null?!_this.getTitle().equals(_that.getTitle()):_that.getTitle() !=null) return false;

        if (_this.getDuration() != _that.getDuration()) return false;
        if (_this.getTrackNumber() != _that.getTrackNumber()) return false;
        if (_this.getTotalTracks() != _that.getTotalTracks()) return false;

        if (_this.getEntryId() != _that.getEntryId()) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = (int) (getId() ^ (getId() >>> 32));
        result = 31 * result + (getDownloadURL() != null ? getDownloadURL().hashCode() : 0);
        result = 31 * result + (getDownloadURLExpiration() != null ? getDownloadURLExpiration().hashCode() : 0);
        result = 31 * result + (getAlbum() != null ? getAlbum().hashCode() : 0);
        result = 31 * result + (getAlbumArtist() != null ? getAlbumArtist().hashCode() : 0);
        result = 31 * result + (getArtist() != null ? getArtist().hashCode() : 0);
        result = 31 * result + (getGenre() != null ? getGenre().hashCode() : 0);
        result = 31 * result + (getTitle() != null ? getTitle().hashCode() : 0);
        result = 31 * result + (int) (getDuration() ^ (getDuration() >>> 32));
        result = 31 * result + getTrackNumber();
        result = 31 * result + getTotalTracks();
        result = 31 * result + (int) (getEntryId() ^ (getEntryId() >>> 32));
        return result;
    }

}
