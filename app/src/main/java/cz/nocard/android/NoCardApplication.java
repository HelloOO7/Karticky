package cz.nocard.android;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

import javax.inject.Inject;

public class NoCardApplication extends Application {

    private static NoCardApplication INSTANCE;

    {
        INSTANCE = this;
    }

    private ApplicationComponent applicationComponent;

    @Inject
    WlanFencingManager wlanFencingManager;
    @Inject
    NoCardPreferences prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);

        applicationComponent = DaggerApplicationComponent.builder()
                .applicationModule(new ApplicationModule(this))
                .build();

        applicationComponent.inject(this);

        if (prefs.isBGNotificationEnabled() && BackgroundWlanCheckWorker.isUseable(this)) {
            BackgroundWlanCheckWorker.scheduleWork(this, prefs);
        }
    }

    public ApplicationComponent getApplicationComponent() {
        return applicationComponent;
    }

    public static NoCardApplication getInstance() {
        return INSTANCE;
    }
}
