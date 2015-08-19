package com.peartree.android.kiteplayer.dropbox;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.peartree.android.kiteplayer.BuildConfig;
import com.peartree.android.kiteplayer.TestKiteApplication;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, application = TestKiteApplication.class, sdk = Build.VERSION_CODES.LOLLIPOP)
public class DropboxSyncServiceTest {

    @BeforeClass
    public static void setupLogging() throws Exception {
        ShadowLog.stream = System.out;
    }

    @Test
    public void testSyncService() {

        Context ctx = RuntimeEnvironment.application;

        Intent i = new Intent(ctx,DropboxSyncService.class);

        DropboxSyncService service = Robolectric
                .buildService(DropboxSyncService.class)
                .create()
                .startCommand(0,1999)
                .get();

        service.onHandleIntent(i);
        service.stopSelf(1999);

    }


}
