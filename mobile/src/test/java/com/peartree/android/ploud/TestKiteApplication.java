package com.peartree.android.kiteplayer;

import android.app.Application;
import android.content.Context;

import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.peartree.android.kiteplayer.dagger.TestScope;

import javax.inject.Singleton;

import dagger.Component;
import dagger.Module;
import dagger.Provides;

import static org.mockito.Mockito.mock;

public class TestKiteApplication extends KiteApplication {

    public TestKiteApplication() {
        super();

        mApplicationComponent = DaggerTestPloudApplication_TestApplicationComponent.builder()
                .testApplicationModule(new TestApplicationModule(this))
                .build();

        mApplicationComponent.inject(this);
    }

    @Singleton
    @Component(modules = TestApplicationModule.class)
    public interface TestApplicationComponent extends KiteApplicationComponent {
    }

    @Module
    public class TestApplicationModule {

        private KiteApplication mApplication;
        private Context mApplicationContext;

        public TestApplicationModule(KiteApplication application) {
            mApplication = application;
            mApplicationContext = application.getApplicationContext();
        }

        @Provides @Singleton
        Application provideApplication() {
            return mApplication;
        }

        @Provides @Singleton
        public VideoCastManager provideTestVideoCastManager() {
            return mock(VideoCastManager.class);
        }
    }
}
