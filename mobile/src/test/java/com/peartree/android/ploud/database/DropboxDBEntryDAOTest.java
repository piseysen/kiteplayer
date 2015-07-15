package com.peartree.android.ploud.database;

import android.os.Build;

import com.peartree.android.ploud.BuildConfig;
import com.peartree.android.ploud.TestPloudApplication;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, application = TestPloudApplication.class, sdk = Build.VERSION_CODES.LOLLIPOP)
public class DropboxDBEntryDAOTest {

    private static DropboxDBEntryDAO mDao;
    private static DropboxDBHelper mHelper;

    @Before
    public void setUp() {

        mHelper = new DropboxDBHelper(RuntimeEnvironment.application);
        mDao = new DropboxDBEntryDAO(mHelper);

    }

    @Test
    public void testInsertOrReplace() {

        Long insertId;
        Long updateId;

        DropboxDBEntryMapper.DropboxDBEntryPojo entry = buildDropboxDBEntry();

        insertId = mDao.insertOrReplace(entry);

        assertThat("Insert successful", insertId, greaterThan(new Long(-1)));

        entry.setId(insertId);
        updateId = mDao.insertOrReplace(entry);

        assertThat("Update successful with ID value set", updateId, greaterThan(new Long(-1)));
        assertThat("Update successful, updated with same id", updateId, is(insertId));

        entry.setId(0);
        updateId = mDao.insertOrReplace(entry);

        assertThat("Update successful with ID value unset", updateId, greaterThan(new Long(-1)));

        DropboxDBEntry entryReplaced = mDao.findById(insertId);
        assertThat("Update successful, replaced based on unique key", entryReplaced, nullValue());

    }

    @Test
    public void testFindById() {

        DropboxDBEntryMapper.DropboxDBEntryPojo entry = buildDropboxDBEntry();

        long insertId = mDao.insertOrReplace(entry);

        DropboxDBEntry foundEntry = mDao.findById(insertId);

        assertThat("Entry found by ID", foundEntry, notNullValue());

        entry.setId(insertId);

        assertThat("Inserted and found objects match", foundEntry, equalTo((DropboxDBEntry) entry));
    }

    @Test
    public void testFindByParentDir() {

        DropboxDBEntryMapper.DropboxDBEntryPojo entry = buildDropboxDBEntry();

        mDao.insertOrReplace(entry);

        DropboxDBEntryMapper.DropboxDBEntryCursorWrapper results = mDao.findByParentDir(entry.getParentDir());

        assertThat("Entries found", results.getCount(), greaterThan(0));

        results.moveToFirst();

        assertThat("First entry' parent folder match", results.getParentDir(), equalTo(entry.getParentDir()));
    }

    @Test
    public void testDeleteById() {

        DropboxDBEntryMapper.DropboxDBEntryPojo entry = buildDropboxDBEntry();

        long id = mDao.insertOrReplace(entry);

        int rows = mDao.deleteById(id);

        assertThat("Entry deleted successfully", rows, greaterThan(0));
    }

    @Test
    public void testDeleteByParentDirAndFilename() {

        DropboxDBEntryMapper.DropboxDBEntryPojo entry = buildDropboxDBEntry();

        long id = mDao.insertOrReplace(entry);

        int rows = mDao.deleteByParentDirAndFilename(entry.getParentDir(),entry.getFilename());

        assertThat("Entry deleted successfully", rows, greaterThan(0));
    }

    private DropboxDBEntryMapper.DropboxDBEntryPojo buildDropboxDBEntry() {

        // TODO Improve test fixture

        DropboxDBEntryMapper.DropboxDBEntryPojo entry = new DropboxDBEntryMapper.DropboxDBEntryPojo();

        entry.setRoot("dropbox");

        entry.setIsDir(false);
        entry.setParentDir("/parentdir");
        entry.setFilename("filename");

        entry.setBytes(1024);
        entry.setSize("1 Kb");

        entry.setModified("");
        entry.setClientMtime("");

        entry.setRev("abcdefghijklmnop");

        entry.setMimeType("audio/mpeg");
        entry.setIcon("http://www.dropbox.com/icon.jpg");
        entry.setThumbExists(false);
        return entry;
    }

}
