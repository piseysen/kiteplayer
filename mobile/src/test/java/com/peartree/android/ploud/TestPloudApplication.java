package com.peartree.android.ploud;

import android.app.Application;
import android.content.Context;

import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.peartree.android.ploud.dagger.TestScope;

import javax.inject.Singleton;

import dagger.Component;
import dagger.Module;
import dagger.Provides;

import static org.mockito.Mockito.mock;

public class TestPloudApplication extends PloudApplication {

    public TestPloudApplication() {
        super();

        mApplicationComponent = DaggerTestPloudApplication_TestApplicationComponent.builder()
                .testApplicationModule(new TestApplicationModule(this))
                .build();

        mApplicationComponent.inject(this);
    }

    @Singleton
    @Component(modules = TestApplicationModule.class)
    public interface TestApplicationComponent extends PloudApplicationComponent {
    }

    @Module
    public class TestApplicationModule {

        private PloudApplication mApplication;
        private Context mApplicationContext;

        public TestApplicationModule(PloudApplication application) {
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
