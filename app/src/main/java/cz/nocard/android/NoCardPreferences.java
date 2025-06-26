package cz.nocard.android;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cz.spojenka.android.util.CollectionUtils;

public class NoCardPreferences {

    private static final String PK_WLAN_AUTO_DETECT = "wlan_auto_detect";
    private static final String PK_NOTIFICATION_ENABLED = "notification_enabled";
    private static final String PK_NOTIFICATION_NAG_DISABLED = "notification_nag_disabled";
    private static final String PK_LAST_NOTIFICATION_PROVIDER = "last_notification_provider";
    private static final String PK_LAST_NOTIFICATION_BSSID_CLOSURE = "last_notification_bssid_closure";
    private static final String PK_BACKGROUND_CHECK_INTERVAL = "background_check_interval";
    private static final String PK_LAST_REMOTE_UPDATE = "last_remote_update";
    private static final String PK_LAST_REMOTE_ETAG = "last_remote_etag";
    private static final String PK_MIN_WLAN_DBM = "min_wlan_dbm";
    private static final String PK_FAVOURITE_PROVIDERS = "favourite_providers";
    private static final String PK_CARD_BLACKLIST_PREFIX = "card_blacklist_";

    private final SharedPreferences prefs;

    private Map<String, Set<String>> cardBlacklistCache = new HashMap<>();

    public NoCardPreferences(Context context) {
        prefs = context.getSharedPreferences("nocard", android.content.Context.MODE_PRIVATE);
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

    public String getLastNofificationProvider() {
        return prefs.getString(PK_LAST_NOTIFICATION_PROVIDER, null);
    }

    public Set<String> getLastNotificationBSSIDClosure() {
        return prefs.getStringSet(PK_LAST_NOTIFICATION_BSSID_CLOSURE, Set.of());
    }

    public void putLastNotificationAPInfo(WlanFencingManager.ProviderAPInfo apInfo) {
        if (apInfo != null) {
            Set<String> currentClosure = getLastNotificationBSSIDClosure();
            Set<String> newClosure = apInfo.getBSSIDClosure();

            if (CollectionUtils.setIntersects(currentClosure, newClosure)) {
                newClosure = CollectionUtils.setUnion(currentClosure, newClosure);
            }

            prefs.edit()
                    .putString(PK_LAST_NOTIFICATION_PROVIDER, apInfo.provider())
                    .putStringSet(PK_LAST_NOTIFICATION_BSSID_CLOSURE, newClosure)
                    .apply();
        } else {
            prefs.edit()
                    .remove(PK_LAST_NOTIFICATION_PROVIDER)
                    .remove(PK_LAST_NOTIFICATION_BSSID_CLOSURE)
                    .apply();
        }
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

    public List<String> getFavouriteProviders() {
        String list = prefs.getString(PK_FAVOURITE_PROVIDERS, "");
        if (list.isEmpty()) {
            return List.of();
        }
        return List.of(list.split(","));
    }

    public void putFavouriteProviders(List<String> providers) {
        String list = String.join(",", providers);
        prefs.edit().putString(PK_FAVOURITE_PROVIDERS, list).apply();
    }

    public Set<String> getCardBlacklist(String provider) {
        return cardBlacklistCache.computeIfAbsent(
                provider,
                (provider2) -> new HashSet<>(prefs.getStringSet(PK_CARD_BLACKLIST_PREFIX + provider2, Set.of()))
        );
    }

    public void putCardBlacklist(String provider, Set<String> blacklist) {
        cardBlacklistCache.put(provider, blacklist);
        prefs.edit().putStringSet(PK_CARD_BLACKLIST_PREFIX + provider, blacklist).apply();
    }

    public void addCardToBlacklist(String provider, String cardId) {
        Set<String> blacklist = getCardBlacklist(provider);
        blacklist.add(cardId);
        putCardBlacklist(provider, blacklist);
    }

    public void removeCardFromBlacklist(String provider, String cardId) {
        Set<String> blacklist = getCardBlacklist(provider);
        blacklist.remove(cardId);
        putCardBlacklist(provider, blacklist);
    }
}
