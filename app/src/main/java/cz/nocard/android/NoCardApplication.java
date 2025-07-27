package cz.nocard.android;

import android.app.ActivityManager;
import android.app.Application;

import com.google.android.material.color.DynamicColors;

import javax.inject.Inject;

import cz.nocard.android.beacon.BackgroundWlanCheckWorker;
import cz.nocard.android.beacon.WlanFencingManager;
import cz.nocard.android.sharing.NfcExportServiceState;
import cz.nocard.android.data.NoCardPreferences;

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
    @Inject
    NfcExportServiceState nfcExportServiceState;

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);

        applicationComponent = DaggerApplicationComponent.builder()
                .applicationModule(new ApplicationModule(this))
                .build();

        applicationComponent.inject(this);

        // if the app crashed before, do not allow sneaking into the NFC service
        // (until the appropriate activity is explicitly launched by the user)
        nfcExportServiceState.setEnabled(false);

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

    public static boolean isAppInForeground() {
        ActivityManager.RunningAppProcessInfo state = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(state);
        return state.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND || state.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
    }
}
