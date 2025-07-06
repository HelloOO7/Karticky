package cz.nocard.android;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiSsid;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import cz.spojenka.android.system.PermissionRequestHelper;
import cz.spojenka.android.util.AsyncUtils;
import cz.spojenka.android.util.CollectionUtils;

public class WlanFencingManager {

    public static final int SIGNAL_OUT_OF_RANGE = Integer.MIN_VALUE;

    private static final String LOG_TAG = WlanFencingManager.class.getSimpleName();

    private final Context context;
    private final ConnectivityManager connectivityManager;
    private final WifiManager wifiManager;
    private final ConfigManager config;

    private boolean registered = false;

    private ProviderAPInfo currentAPInfo = null;

    private final List<OnNearbyProviderCallback> callbacks = new ArrayList<>();

    private final ReceiverImpl receiverImpl;

    private final ConnectivityManager.NetworkCallback networkCallback;
    private WifiInfo wifiInfoFromCallback;
    private Network wifiInfoSourceNetwork;
    private boolean isDoneInitialScan = false;

    public WlanFencingManager(Context context, ConfigManager config) {
        this.context = context;
        this.connectivityManager = context.getSystemService(ConnectivityManager.class);
        this.wifiManager = context.getSystemService(WifiManager.class);
        this.config = config;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            receiverImpl = new Api30Impl();
        } else {
            receiverImpl = new LegacyImpl();
        }

        if (isNetworkCallbackNeededForWifiInfo()) {
            networkCallback = new ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {

                @Override
                public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                    if (isBackgroundedAndNotAllowed()) {
                        return;
                    }
                    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        if (networkCapabilities.getTransportInfo() instanceof WifiInfo wi) {
                            wifiInfoFromCallback = wi;
                            wifiInfoSourceNetwork = network;
                            update();
                        }
                    }
                }

                @Override
                public void onLost(@NonNull Network network) {
                    if (Objects.equals(network, wifiInfoSourceNetwork)) {
                        wifiInfoFromCallback = null;
                        wifiInfoSourceNetwork = null;
                        update();
                    }
                }
            };
        } else {
            networkCallback = new ConnectivityManager.NetworkCallback() {

                @Override
                public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                    if (isBackgroundedAndNotAllowed()) {
                        return;
                    }
                    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        wifiInfoSourceNetwork = network;
                        update();
                    }
                }

                @Override
                public void onLost(@NonNull Network network) {
                    if (Objects.equals(network, wifiInfoSourceNetwork)) {
                        wifiInfoSourceNetwork = null;
                        update();
                    }
                }
            };
        }
    }

    public boolean isDoneInitialScan() {
        return isDoneInitialScan;
    }

    public synchronized void registerOnNearbyProviderCallback(OnNearbyProviderCallback callback, boolean callIfCurrent) {
        if (!callbacks.contains(callback)) {
            callbacks.add(Objects.requireNonNull(callback));
            register();
        }
        if (callIfCurrent && currentAPInfo != null) {
            callback.providerNearby(currentAPInfo);
        }
    }

    public synchronized void unregisterOnNearbyProviderCallback(OnNearbyProviderCallback callback) {
        callbacks.remove(Objects.requireNonNull(callback));
        if (callbacks.isEmpty()) {
            unregister();
        }
    }

    private void invokeOnNearbyProviderCallbacks(ProviderAPInfo provider) {
        Log.d(LOG_TAG, "Provider nearby: " + provider + ", callback count=" + callbacks.size());
        for (OnNearbyProviderCallback callback : callbacks) {
            callback.providerNearby(provider);
        }
    }

    private void invokeOnProviderLostCallback(ProviderAPInfo provider) {
        Log.d(LOG_TAG, "Provider lost: " + provider + ", callback count=" + callbacks.size());
        for (OnNearbyProviderCallback callback : callbacks) {
            callback.providerLost(provider);
        }
    }

    private void invokeNoProviderCallback() {
        for (OnNearbyProviderCallback callback : callbacks) {
            callback.noProvider();
        }
    }

    private boolean holdsNeededPermissions() {
        return PermissionRequestHelper.hasFineLocationPermission(context);
    }

    public static boolean canWorkInBackground(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            //on older Android versions, the foreground location permission is sufficient
            return PermissionRequestHelper.hasFineLocationPermission(context);
        }
    }

    private boolean canWorkInBackground() {
        return canWorkInBackground(context);
    }

    public synchronized void register() {
        if (registered) {
            return;
        }
        if (!holdsNeededPermissions()) {
            return;
        }
        receiverImpl.register();
        connectivityManager.registerDefaultNetworkCallback(networkCallback);
        registered = true;
    }

    private synchronized void unregister() {
        if (!registered) {
            return;
        }
        connectivityManager.unregisterNetworkCallback(networkCallback);
        receiverImpl.unregister();
        registered = false;
    }

    public boolean isExplicitScanNeeded() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q;
    }

    @SuppressWarnings("deprecation")
    public synchronized void performExplicitScan() {
        if (!holdsNeededPermissions()) {
            return;
        }
        Log.d(LOG_TAG, "Performing explicit WLAN scan");
        wifiManager.startScan();
    }

    public synchronized boolean isCurrent(String provider) {
        return currentAPInfo != null && currentAPInfo.provider().equals(provider);
    }

    private boolean isBackgroundedAndNotAllowed() {
        return !NoCardApplication.isAppInForeground() && !canWorkInBackground();
    }

    public synchronized ProviderAPInfo update() {
        if (!holdsNeededPermissions()) {
            return null;
        }
        if (isBackgroundedAndNotAllowed()) {
            return null;
        }
        try {
            List<WlanAPInfo> lastScanResults = wifiManager.getScanResults()
                    .stream().map(WlanAPInfo::new)
                    .collect(Collectors.toList());

            if (!lastScanResults.isEmpty()) {
                //force initial scan done if OS was able to return some results from scans not initiated by this app
                isDoneInitialScan = true;
            }

            WlanAPInfo connectedResult = createCurrentNetworkPlaceholderResult();
            if (connectedResult != null) {
                Log.d(LOG_TAG, "Active WLAN: " + connectedResult);
                implantResult(lastScanResults, connectedResult);
            }

            Log.d(LOG_TAG, "Received " + lastScanResults.size() + " scan results");
            for (var result : lastScanResults) {
                Log.d(LOG_TAG, "SSID: " + result.ssid() + ", Level: " + result.signal());
            }
            Optional<WlanAPInfo> nearest = getNearestKnownWlan(lastScanResults);

            if (nearest.isPresent()) {
                WlanAPInfo nearestScanResult = nearest.get();
                String ssid = nearest.get().ssid();
                Log.d(LOG_TAG, "Nearest known WLAN: " + ssid);

                ProviderAPInfo newAPInfo = new ProviderAPInfo(
                        nearestScanResult,
                        getSameSSIDAPs(lastScanResults, ssid),
                        Objects.requireNonNull(config.getProviderForWlan(ssid))
                );
                ProviderAPInfo previousAPInfo = currentAPInfo;
                if (currentAPInfo != null && newAPInfo.transitiveMatches(currentAPInfo)) {
                    newAPInfo = newAPInfo.mergeFrom(currentAPInfo);
                }
                currentAPInfo = newAPInfo;

                if (previousAPInfo == null || !newAPInfo.apSetMatches(previousAPInfo)) {
                    Log.d(LOG_TAG, "WLAN changed, new provider: " + currentAPInfo);

                    invokeOnNearbyProviderCallbacks(currentAPInfo);
                }
            } else {
                ProviderAPInfo lostProvider = currentAPInfo;
                currentAPInfo = null;
                if (lostProvider != null) {
                    invokeOnProviderLostCallback(lostProvider);
                }
                invokeNoProviderCallback();
            }
        } catch (SecurityException ex) {
            Log.e(LOG_TAG, "Failed to get scan results due to missing permissions (unexpected)", ex);
        }
        return getCurrentProviderAP();
    }

    private WlanAPInfo createCurrentNetworkPlaceholderResult() {
        WifiInfo wi = getCurrentWifiInfo();
        if (wi == null) {
            return null;
        }
        return new WlanAPInfo(wi.getBSSID(), wi.getSSID(), wi.getRssi());
    }

    @SuppressWarnings("deprecation")
    private WifiInfo getCurrentWifiInfo() {
        if (isNetworkCallbackNeededForWifiInfo()) {
            return wifiInfoFromCallback;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) {
                return null;
            }
            NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(network);
            if (networkCapabilities == null || !networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return null;
            }
            if (networkCapabilities.getTransportInfo() instanceof WifiInfo wi) {
                return wi;
            }
            return null;
        } else {
            return wifiManager.getConnectionInfo();
        }
    }

    private boolean isNetworkCallbackNeededForWifiInfo() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    private void implantResult(List<WlanAPInfo> results, WlanAPInfo result) {
        if (results.stream().noneMatch(result2 -> Objects.equals(result2.bssid(), result.bssid()))) {
            results.add(result);
        }
    }

    public ProviderAPInfo getCurrentProviderAP() {
        return currentAPInfo;
    }

    private Optional<WlanAPInfo> getNearestKnownWlan(List<WlanAPInfo> scanResults) {
        return scanResults.stream()
                .filter(scanResult -> config.isWlanCompatible(scanResult.ssid()))
                .max((o1, o2) -> WifiManager.compareSignalLevel(o1.signal(), o2.signal()));
    }

    private Set<WlanAPInfo> getSameSSIDAPs(List<WlanAPInfo> scanResults, String ssid) {
        return scanResults.stream()
                .filter(scanResult -> Objects.equals(scanResult.ssid(), ssid))
                .collect(Collectors.toSet());
    }

    @SuppressWarnings("deprecation")
    private static String getWlanSSID(ScanResult result) {
        String ssid;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            WifiSsid wifiSsid = result.getWifiSsid();
            ssid = wifiSsid != null ? wifiSsid.toString() : null;
        } else {
            ssid = result.SSID;
        }
        return ssid != null ? ssid : "";
    }

    public static interface OnNearbyProviderCallback {

        public void providerNearby(ProviderAPInfo provider);

        public void providerLost(ProviderAPInfo provider);

        public default void noProvider() {

        }
    }

    public static record ProviderAPInfo(
            WlanAPInfo primaryAP,
            Set<WlanAPInfo> transitiveClosureAPs,
            String provider
    ) {

        public Set<String> getBSSIDClosure() {
            return transitiveClosureAPs().stream()
                    .map(WlanAPInfo::bssid)
                    .collect(Collectors.toSet());
        }

        public boolean transitiveMatches(ProviderAPInfo other) {
            if (!Objects.equals(provider(), other.provider())) {
                return false;
            }
            return CollectionUtils.setIntersects(getBSSIDClosure(), other.getBSSIDClosure());
        }

        public boolean apSetMatches(ProviderAPInfo other) {
            if (!Objects.equals(provider(), other.provider())) {
                return false;
            }
            return CollectionUtils.setEquals(getBSSIDClosure(), other.getBSSIDClosure());
        }

        public ProviderAPInfo mergeFrom(ProviderAPInfo other) {
            Set<String> myBSSIDs = getBSSIDClosure();
            Set<WlanAPInfo> aps = new HashSet<>(transitiveClosureAPs());
            for (WlanAPInfo ap : other.transitiveClosureAPs()) {
                if (!myBSSIDs.contains(ap.bssid())) {
                    aps.add(new WlanAPInfo(
                            ap.bssid(),
                            ap.ssid(),
                            SIGNAL_OUT_OF_RANGE
                    ));
                }
            }
            return new ProviderAPInfo(primaryAP(), aps, provider());
        }
    }

    private interface ReceiverImpl {

        public void register();

        public void unregister();
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private class Api30Impl implements ReceiverImpl {

        private final WifiManager.ScanResultsCallback callback = new WifiManager.ScanResultsCallback() {
            @Override
            public void onScanResultsAvailable() {
                isDoneInitialScan = true;
                update();
            }
        };

        @Override
        public void register() {
            Log.d(LOG_TAG, "Registering scan results callback for Android >=R");
            wifiManager.registerScanResultsCallback(AsyncUtils.getMainThreadExecutor(), callback);
        }

        @Override
        public void unregister() {
            wifiManager.unregisterScanResultsCallback(callback);
        }
    }

    private class LegacyImpl implements ReceiverImpl {

        private final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                    isDoneInitialScan = true;
                    update();
                }
            }
        };

        @Override
        public void register() {
            Log.d(LOG_TAG, "Registering scan results receiver for Android <=Q");
            context.registerReceiver(receiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        }

        @Override
        public void unregister() {
            context.unregisterReceiver(receiver);
        }
    }

    public record WlanAPInfo(String bssid, String ssid, int signal) {

        public WlanAPInfo(ScanResult scanResult) {
            this(scanResult.BSSID, getWlanSSID(scanResult), scanResult.level);
        }
    }
}
