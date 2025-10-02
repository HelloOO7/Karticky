package cz.mamstylcendy.cards;

import android.app.ActivityManager;
import android.app.Application;

import com.google.android.material.color.DynamicColors;

import javax.inject.Inject;

import cz.mamstylcendy.cards.beacon.BackgroundWlanCheckWorker;
import cz.mamstylcendy.cards.beacon.WlanFencingManager;
import cz.mamstylcendy.cards.data.CardsPreferences;
import cz.mamstylcendy.cards.sharing.NfcExportServiceState;

public class CardsApplication extends Application {

    private static CardsApplication INSTANCE;

    {
        INSTANCE = this;
    }

    private ApplicationComponent applicationComponent;

    @Inject
    WlanFencingManager wlanFencingManager;
    @Inject
    CardsPreferences prefs;
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

    public static CardsApplication getInstance() {
        return INSTANCE;
    }

    public static boolean isAppInForeground() {
        ActivityManager.RunningAppProcessInfo state = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(state);
        return state.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND || state.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
    }
}
