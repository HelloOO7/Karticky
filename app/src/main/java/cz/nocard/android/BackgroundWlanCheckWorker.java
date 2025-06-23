package cz.nocard.android;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

public class BackgroundWlanCheckWorker extends Worker {

    private static final String TAG = BackgroundWlanCheckWorker.class.getSimpleName();

    private static final String WORK_NAME = "BackgroundWlanCheck";

    @Inject
    WlanFencingManager wlanFencingManager;
    @Inject
    CardNotificationManager cardNotificationManager;
    @Inject
    NoCardPreferences prefs;

    private static WlanFencingManager.OnNearbyProviderCallback lastGlobalCallback = null;

    public BackgroundWlanCheckWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        NoCardApplication.getInstance().getApplicationComponent().inject(this);
    }

    public static boolean isUseable(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        if (!prefs.isBGNotificationEnabled() || !isUseable(getApplicationContext())) {
            return Result.failure();
        }

        WlanFencingManager.ProviderAPInfo provider = filterProviderInfo(wlanFencingManager.update());
        if (!cardNotificationManager.shouldShowNotificationForProviderAP(provider)) {
            Log.d(TAG, "No notification needed for AP: " + provider);
            reschedule();
            return Result.success();
        }

        Log.d(TAG, "Showing notification for AP: " + provider);
        if (!isAppInForeground()) {
            cardNotificationManager.showNotificationForProviderAP(provider);
        } else {
            Log.d(TAG, "App is in foreground, notification is suppressed.");
        }
        reschedule();

        if (lastGlobalCallback != null) {
            wlanFencingManager.unregisterOnNearbyProviderCallback(lastGlobalCallback);
        }

        wlanFencingManager.registerOnNearbyProviderCallback(lastGlobalCallback = new WlanFencingManager.OnNearbyProviderCallback() {
            @Override
            public void providerNearby(WlanFencingManager.ProviderAPInfo provider) {
                WlanFencingManager.ProviderAPInfo apInfo = filterProviderInfo(wlanFencingManager.getCurrentProviderAP());
                if (cardNotificationManager.shouldShowNotificationForProviderAP(apInfo)) {
                    Log.d(TAG, "Provider nearby: " + provider + ", attempting to show notification.");
                    cardNotificationManager.showNotificationForProviderAP(apInfo);
                }
            }

            @Override
            public void providerLost(WlanFencingManager.ProviderAPInfo provider) {
                cardNotificationManager.clearNotification();
            }
        }, false);

        return Result.success();
    }

    private WlanFencingManager.ProviderAPInfo filterProviderInfo(WlanFencingManager.ProviderAPInfo providerAPInfo) {
        if (providerAPInfo != null && providerAPInfo.signalLevel() < prefs.getMinWlanDbm()) {
            return null; //act as if no AP is found
        }
        return providerAPInfo;
    }

    private void reschedule() {
        Context context = getApplicationContext();
        WorkManager workManager = WorkManager.getInstance(context);
        UUID id = getId();

        ContextCompat.getMainExecutor(getApplicationContext()).execute(() -> {
            LiveData<WorkInfo> wi = workManager.getWorkInfoByIdLiveData(id);
            wi.observeForever(new Observer<>() {
                @Override
                public void onChanged(WorkInfo workInfo) {
                    boolean keepObserver = false;
                    try {
                        if (workInfo == null) {
                            Log.w(TAG, "WorkInfo not found for id " + id);
                        } else if (workInfo.getState().isFinished()) {
                            scheduleWork(context, prefs, ExistingWorkPolicy.REPLACE);
                        } else {
                            keepObserver = true;
                        }
                    } finally {
                        if (!keepObserver) {
                            wi.removeObserver(this);
                        }
                    }
                }
            });
        });
    }

    private static boolean isAppInForeground() {
        ActivityManager.RunningAppProcessInfo state = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(state);
        return state.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND || state.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
    }

    public static synchronized void scheduleWork(Context context, NoCardPreferences prefs) {
        scheduleWork(context, prefs, ExistingWorkPolicy.KEEP);
    }

    public static synchronized void scheduleWork(Context context, NoCardPreferences prefs, ExistingWorkPolicy existingWorkPolicy) {
        Log.d(TAG, "Scheduling BackgroundWlanCheckWorker to run in " + prefs.getBackgroundCheckInterval() + " minutes.");
        WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                existingWorkPolicy,
                new OneTimeWorkRequest.Builder(BackgroundWlanCheckWorker.class)
                        .setInitialDelay(prefs.getBackgroundCheckInterval(), TimeUnit.MINUTES)
                        .build()
        );
    }

    public static synchronized void unregister(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
        Log.d(TAG, "BackgroundWlanCheckWorker unregistered.");
    }
}
