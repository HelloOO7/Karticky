package cz.nocard.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.Objects;

import javax.inject.Inject;

public class CardNotificationManager {

    private static final String CHANNEL_ID = "nocard_channel";
    private static final String EXTRA_PROVIDER = "provider";

    private static final int GLOBAL_NOTIFICATION_ID = 1;

    private final Context context;
    private final NoCardPreferences prefs;
    private final NotificationManagerCompat notificationManager;
    private NotificationChannel channel;

    @Inject
    public CardNotificationManager(NoCardPreferences prefs) {
        context = NoCardApplication.getInstance();
        this.prefs = prefs;
        notificationManager = NotificationManagerCompat.from(context);

        initNotificationChannel();
    }

    private void initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID);
            if (existingChannel != null) {
                channel = existingChannel;
            } else {
                channel = new NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.notification_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                );
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private PendingIntent createLaunchForProviderIntent(String provider) {
        // Create a PendingIntent to open the app when the notification is clicked
        return PendingIntent.getActivity(
                context,
                0,
                new Intent(context, MainActivity.class)
                        .setAction(MainActivity.ACTION_SHOW_CARD)
                        .putExtra(MainActivity.EXTRA_PROVIDER, provider)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private Bundle createNotificationExtra(String provider) {
        Bundle extras = new Bundle();
        extras.putString(EXTRA_PROVIDER, provider);
        return extras;
    }

    private Notification buildNotification(String provider) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_24px)
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(context.getString(R.string.notification_text, provider))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(createLaunchForProviderIntent(provider))
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setExtras(createNotificationExtra(provider))
                .setAutoCancel(true);

        return builder.build();
    }

    public boolean shouldShowNotificationForProviderAP(WlanFencingManager.ProviderAPInfo apInfo) {
        if (apInfo == null) {
            return true; //always cancel
        }
        if (Objects.equals(prefs.getLastNofificationKey(), prefs.makeNotificationKey(apInfo))) {
            return false;
        }
        for (StatusBarNotification notification : notificationManager.getActiveNotifications()) {
            if (notification.getId() == GLOBAL_NOTIFICATION_ID) {
                Bundle extras = notification.getNotification().extras;
                if (extras != null) {
                    String existingProvider = extras.getString(EXTRA_PROVIDER);
                    if (existingProvider != null && existingProvider.equals(apInfo.provider())) {
                        return false; // Already showing notification for this provider
                    }
                }
            }
        }
        return true;
    }

    public void clearNotification() {
        showNotificationForProviderAP(null);
    }

    public void showNotificationForProviderAP(WlanFencingManager.ProviderAPInfo apInfo) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (apInfo == null) {
            notificationManager.cancel(GLOBAL_NOTIFICATION_ID);
        } else {
            Notification notification = buildNotification(apInfo.provider());
            notificationManager.notify(GLOBAL_NOTIFICATION_ID, notification);
            prefs.putLastNotificationKey(apInfo);
        }
    }
}
