package cz.nocard.android;

import android.content.SharedPreferences;

import java.time.Instant;

import javax.inject.Inject;

public class NoCardPreferences {

    private static final String PK_WLAN_AUTO_DETECT = "wlan_auto_detect";
    private static final String PK_NOTIFICATION_ENABLED = "notification_enabled";
    private static final String PK_NOTIFICATION_NAG_DISABLED = "notification_nag_disabled";
    private static final String PK_LAST_NOTIFICATION_KEY = "last_notification_key";
    private static final String PK_BACKGROUND_CHECK_INTERVAL = "background_check_interval";
    private static final String PK_LAST_REMOTE_UPDATE = "last_remote_update";
    private static final String PK_LAST_REMOTE_ETAG = "last_remote_etag";
    private static final String PK_MIN_WLAN_DBM = "min_wlan_dbm";

    private final SharedPreferences prefs;

    @Inject
    public NoCardPreferences() {
        prefs = NoCardApplication.getInstance().getSharedPreferences("nocard", android.content.Context.MODE_PRIVATE);
    }

    public SharedPreferences getPrefs() {
        return prefs;
    }

    public boolean getWlanAutoDetect() {
        return prefs.getBoolean(PK_WLAN_AUTO_DETECT, false);
    }

    public void putWlanAutoDetect(boolean enabled) {
        prefs.edit().putBoolean(PK_WLAN_AUTO_DETECT, enabled).apply();
    }

    public String getLastNofificationKey() {
        return prefs.getString(PK_LAST_NOTIFICATION_KEY, null);
    }

    public void putLastNotificationKey(WlanFencingManager.ProviderAPInfo apInfo) {
        prefs.edit().putString(PK_LAST_NOTIFICATION_KEY, makeNotificationKey(apInfo)).apply();
    }

    public String makeNotificationKey(WlanFencingManager.ProviderAPInfo apInfo) {
        return apInfo.bssid() + "|" + apInfo.ssid();
    }

    public boolean isBGNotificationEnabled() {
        return prefs.getBoolean(PK_NOTIFICATION_ENABLED, false);
    }

    public void putBGNotificationEnabled(boolean enabled) {
        prefs.edit().putBoolean(PK_NOTIFICATION_ENABLED, enabled).apply();
    }

    public int getBackgroundCheckInterval() {
        return prefs.getInt(PK_BACKGROUND_CHECK_INTERVAL, 5);
    }

    public void putBackgroundCheckInterval(int minutes) {
        prefs.edit().putInt(PK_BACKGROUND_CHECK_INTERVAL, minutes).apply();
    }

    public boolean isNotificationNagDisabled() {
        return prefs.getBoolean(PK_NOTIFICATION_NAG_DISABLED, false);
    }

    public void putNotificationNagDisabled(boolean disabled) {
        prefs.edit().putBoolean(PK_NOTIFICATION_NAG_DISABLED, disabled).apply();
    }

    public Instant getLastRemoteUpdate() {
        long millis = prefs.getLong(PK_LAST_REMOTE_UPDATE, 0);
        return millis == 0 ? null : Instant.ofEpochMilli(millis);
    }

    public void putLastRemoteUpdate(Instant instant) {
        prefs.edit().putLong(PK_LAST_REMOTE_UPDATE, instant.toEpochMilli()).apply();
    }

    public String getLastRemoteEtag() {
        return prefs.getString(PK_LAST_REMOTE_ETAG, null);
    }

    public void putLastRemoteEtag(String etag) {
        prefs.edit().putString(PK_LAST_REMOTE_ETAG, etag).apply();
    }

    public int getMinWlanDbm() {
        return prefs.getInt(PK_MIN_WLAN_DBM, -100);
    }

    public void putMinWlanDbm(int dbm) {
        prefs.edit().putInt(PK_MIN_WLAN_DBM, dbm).apply();
    }
}
