package com.peartree.android.kiteplayer.utils;

import android.support.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import rx.Observable;
import rx.schedulers.Schedulers;

public class ImmutableFileLRUCache {

    public static final String TAG = LogHelper.makeLogTag(ImmutableFileLRUCache.class);

    private String mParentDirPath = null;
    private long mSizeLimitInBytes = 0;
    private Map<String, Lock> mWriters;

    public ImmutableFileLRUCache(String parentDirPath, long sizeLimitInBytes) {

        if (!validParentDir(parentDirPath)) {
            throw new ImmutableFileLRUCacheException("Parent directory does not exist, cannot be created or is not writeable.");
        }

        this.mParentDirPath = parentDirPath;
        this.mSizeLimitInBytes = sizeLimitInBytes;
        this.mWriters = new ConcurrentHashMap<>();


    }

    private boolean validParentDir(String parentDirPath) {
        File parentDir = new File(parentDirPath);

        if (!parentDir.exists()) {
            parentDir.mkdir();
        }

        return parentDir.exists() && parentDir.canWrite();
    }

    public
    @Nullable
    File newFile(String filename, ImmutableFileWriter writer) {

        // Filename must be defined
        // TODO Fix callers which pass null names in
        if (filename == null || filename.trim().equals("")) return null;

        Lock writerLock;
        if (mWriters.get(filename) != null) { // Another active writer must not exist
            LogHelper.w(TAG, "newFile - Another writer already exists for: " + filename + ". Returning null.");
            return null;
        } else {
            mWriters.put(filename, new ReentrantLock());
            if ((writerLock = mWriters.get(filename)) != null) {
                writerLock.lock();
            } else {
                LogHelper.e(TAG, "newFile - Unable to find active writer's lock for: " + filename + ". Should never happen.");
                return null; // Should never happen
            }
        }

        File newFile = new File(mParentDirPath, filename);
        File tmpFile = null;
        FileOutputStream tmpFOS = null;

        try {

            if (newFile.exists()) { // File must not be overwritten
                LogHelper.w(TAG, "newFile - Already exists: " + filename);
                return null;
            } else {

                tmpFile = new File(mParentDirPath, filename + ".tmp");
                tmpFOS = new FileOutputStream(tmpFile);

                writer.write(tmpFOS);

                tmpFOS.flush();
                CloseableHelper.closeQuietly(tmpFOS);

                tmpFile.renameTo(newFile);
            }

            startCleanupRoutine();
            return newFile;

        } catch (Exception e) {
            LogHelper.w(TAG, "newFile - Unable to create cache file: " + filename, e);

            if (tmpFile != null) tmpFile.delete();
            if (newFile != null) newFile.delete();
            return null;
        } finally {
            writerLock.unlock();
            mWriters.remove(filename);

            CloseableHelper.closeQuietly(tmpFOS);
        }
    }

    private void startCleanupRoutine() {

        File parentDir = new File(mParentDirPath);
        File[] cachedFiles = parentDir.listFiles();

        long cummDirSize = 0;

        for (File file : cachedFiles) { // Non-recursive
            cummDirSize += file.length();
        }

        if (cummDirSize > mSizeLimitInBytes) {

            final long delta = cummDirSize - mSizeLimitInBytes;

            Observable
                    .from(cachedFiles)
                    .subscribeOn(Schedulers.io())
                    .toSortedList((f1, f2) -> {
                        if (f1.lastModified() > f2.lastModified()) return 1;
                        else if (f1.lastModified() == f2.lastModified()) return 0;
                        else return -1;
                    })
                    .subscribe(files -> {
                        long cummDeletedBytes = 0;
                        for (File file : files) {

                            long fileSize = file.length();
                            boolean successfulDelete = file.delete();

                            if (successfulDelete) {
                                cummDeletedBytes += fileSize;
                            }

                            if (cummDeletedBytes >= delta) break;
                        }
                    });
        }
    }

    public
    @Nullable
    File get(String filename, long timeout) {

        // Filename must be defined
        // TODO Fix callers which pass null names in
        if (filename == null || filename.trim().equals("")) return null;

        File existingFile = new File(mParentDirPath, filename);
        Lock writerLock;

        if (!existingFile.exists()) {
            return null;
        } else {

            // Checks for active writers
            if ((writerLock = mWriters.get(filename)) != null) {
                if (timeout >= 0) {
                    try {

                        boolean lockAcquired = writerLock.tryLock(timeout, TimeUnit.MILLISECONDS);
                        if (!lockAcquired) return null;

                    } catch (InterruptedException e) {
                        return null;
                    }
                } else {
                    writerLock.lock(); // Must wait for writer to finish
                }
                writerLock.unlock();
            }

            existingFile.setLastModified(new Date().getTime());
            existingFile.setReadOnly();

            return existingFile;
        }
    }

    public static class ImmutableFileLRUCacheException extends RuntimeException {
        public ImmutableFileLRUCacheException(String message) {
            super(message);
        }
    }

    public interface ImmutableFileWriter {
        void write(OutputStream os) throws Exception;
    }
}

