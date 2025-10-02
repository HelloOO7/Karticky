package cz.mamstylcendy.cards.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import cz.mamstylcendy.cards.beacon.WlanFencingManager;
import cz.spojenka.android.util.CollectionUtils;

public class CardsPreferences {

    private static final int AP_CONTINUITY_DROPOUT_MAX_SECONDS = 90;

    private static final String PK_WLAN_AUTO_DETECT = "wlan_auto_detect";
    private static final String PK_NOTIFICATION_ENABLED = "notification_enabled";
    private static final String PK_NOTIFICATION_NAG_DISABLED = "notification_nag_disabled";
    private static final String PK_LAST_NOTIFICATION_PROVIDER = "last_notification_provider";
    private static final String PK_LAST_NOTIFICATION_BSSID_CLOSURE = "last_notification_bssid_closure";
    private static final String PK_LAST_CONNECTED_TS = "last_connected_ts";
    private static final String PK_LAST_CONNECTION_LOST_TS = "last_connection_lost_ts";
    private static final String PK_BACKGROUND_CHECK_INTERVAL = "background_check_interval";
    private static final String PK_LAST_REMOTE_UPDATE = "last_remote_update";
    private static final String PK_LAST_REMOTE_ETAG = "last_remote_etag";
    private static final String PK_MIN_WLAN_DBM = "min_wlan_dbm";
    private static final String PK_FAVOURITE_PROVIDERS = "favourite_providers";
    private static final String PK_CARD_BLACKLIST_PREFIX = "card_blacklist_";
    private static final String PK_LAST_CARD_LIST_TAB = "last_card_list_tab";
    private static final String PK_BG_LOCATION_PERMISSION_ATTEMPTED = "bg_location_permission_attempted";

    private final SharedPreferences prefs;

    private Map<String, Set<String>> cardBlacklistCache = new HashMap<>();

    public CardsPreferences(Context context) {
        prefs = context.getSharedPreferences("karticky", android.content.Context.MODE_PRIVATE);
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

    private Set<String> getLastNotificationBSSIDClosure() {
        return prefs.getStringSet(PK_LAST_NOTIFICATION_BSSID_CLOSURE, Set.of());
    }

    public boolean checkLastAPsNeighboring(WlanFencingManager.ProviderAPInfo currentAPs) {
        Instant now = Instant.now();
        Instant connectionLostAt = null;
        Instant connectedAt = null;

        if (prefs.contains(PK_LAST_CONNECTION_LOST_TS)) {
            connectionLostAt = Instant.ofEpochMilli(prefs.getLong(PK_LAST_CONNECTION_LOST_TS, 0));
        }
        if (prefs.contains(PK_LAST_CONNECTED_TS)) {
            connectedAt = Instant.ofEpochMilli(prefs.getLong(PK_LAST_CONNECTED_TS, 0));
        }

        if (connectedAt != null) {
            if (connectionLostAt == null || connectionLostAt.isBefore(connectedAt)) {
                if (CollectionUtils.setIntersects(getLastNotificationBSSIDClosure(), currentAPs.getBSSIDClosure())) {
                    return true;
                }
            }

            String currentProvider = getLastNofificationProvider();
            if (Objects.equals(currentProvider, currentAPs.provider())) {
                return ChronoUnit.SECONDS.between(connectedAt, now) < AP_CONTINUITY_DROPOUT_MAX_SECONDS;
            }
        }

        return false;
    }

    public void putLastNotificationAPInfo(WlanFencingManager.ProviderAPInfo apInfo) {
        if (apInfo != null) {
            Set<String> currentClosure = getLastNotificationBSSIDClosure();
            Set<String> newClosure = apInfo.getBSSIDClosure();

            if (checkLastAPsNeighboring(apInfo)) {
                newClosure = CollectionUtils.setUnion(currentClosure, newClosure);
            }

            prefs.edit()
                    .putString(PK_LAST_NOTIFICATION_PROVIDER, apInfo.provider())
                    .putStringSet(PK_LAST_NOTIFICATION_BSSID_CLOSURE, newClosure)
                    .putLong(PK_LAST_CONNECTED_TS, Instant.now().toEpochMilli())
                    .apply();
        } else {
            prefs.edit()
                    .putLong(PK_LAST_CONNECTION_LOST_TS, Instant.now().toEpochMilli())
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

    public void putLastCardListTab(int tabIndex) {
        prefs.edit().putInt(PK_LAST_CARD_LIST_TAB, tabIndex).apply();
    }

    public int getLastCardListTab() {
        return prefs.getInt(PK_LAST_CARD_LIST_TAB, 0);
    }

    public boolean isBGLocationPermissionAttempted() {
        return prefs.getBoolean(PK_BG_LOCATION_PERMISSION_ATTEMPTED, false);
    }

    public void putBGLocationPermissionAttempted(boolean attempted) {
        prefs.edit().putBoolean(PK_BG_LOCATION_PERMISSION_ATTEMPTED, attempted).apply();
    }
}
