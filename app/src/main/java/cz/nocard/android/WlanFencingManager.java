package cz.nocard.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class WlanFencingManager {

    private static final String LOG_TAG = WlanFencingManager.class.getSimpleName();

    private final Context context;
    private final ConnectivityManager connectivityManager;
    private final WifiManager wifiManager;
    private final ConfigManager config;

    private boolean registered = false;

    private WlanAPInfo currentScanResult = null;
    private String currentProvider = null;

    private final List<OnNearbyProviderCallback> callbacks = new ArrayList<>();

    private final ReceiverImpl receiverImpl;

    private final ConnectivityManager.NetworkCallback networkCallback;
    private WifiInfo wifiInfoFromCallback;
    private Network wifiInfoSourceNetwork;

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
                        update();
                    }
                }
            };
        } else {
            networkCallback = null;
        }
    }

    public void registerOnNearbyProviderCallback(OnNearbyProviderCallback callback, boolean callIfCurrent) {
        if (!callbacks.contains(callback)) {
            callbacks.add(Objects.requireNonNull(callback));
            register();
        }
        if (callIfCurrent && currentProvider != null) {
            callback.providerNearby(currentProvider);
        }
    }

    public void unregisterOnNearbyProviderCallback(OnNearbyProviderCallback callback) {
        callbacks.remove(Objects.requireNonNull(callback));
        if (callbacks.isEmpty()) {
            unregister();
        }
    }

    private void invokeOnNearbyProviderCallbacks(String provider) {
        Log.d(LOG_TAG, "Provider nearby: " + provider + ", callback count=" + callbacks.size());
        for (OnNearbyProviderCallback callback : callbacks) {
            callback.providerNearby(provider);
        }
    }

    private void invokeOnProviderLostCallback(String provider) {
        Log.d(LOG_TAG, "Provider lost: " + provider + ", callback count=" + callbacks.size());
        for (OnNearbyProviderCallback callback : callbacks) {
            callback.providerLost(provider);
        }
    }

    private boolean holdsNeededPermissions() {
        return PermissionRequestHelper.hasFineLocationPermission(NoCardApplication.getInstance());
    }

    public void register() {
        if (registered) {
            return;
        }
        if (!holdsNeededPermissions()) {
            return;
        }
        receiverImpl.register();
        if (networkCallback != null) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        }
        registered = true;
    }

    private void unregister() {
        if (!registered) {
            return;
        }
        if (networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
        receiverImpl.unregister();
        registered = false;
    }

    public boolean isExplicitScanNeeded() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q;
    }

    @SuppressWarnings("deprecation")
    public void performExplicitScan() {
        if (!holdsNeededPermissions()) {
            return;
        }
        Log.d(LOG_TAG, "Performing explicit WLAN scan");
        wifiManager.startScan();
    }

    public boolean isCurrent(String provider) {
        return Objects.equals(currentProvider, provider);
    }

    public ProviderAPInfo update() {
        if (!holdsNeededPermissions()) {
            return null;
        }
        try {
            List<WlanAPInfo> lastScanResults = wifiManager.getScanResults()
                    .stream().map(WlanAPInfo::new)
                    .collect(Collectors.toList());

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
                String ssid = nearest.get().ssid();
                Log.d(LOG_TAG, "Nearest known WLAN: " + ssid);
                if (currentScanResult == null || !Objects.equals(ssid, currentScanResult.ssid())) {
                    currentScanResult = nearest.get();
                    currentProvider = Objects.requireNonNull(config.getProviderForWlan(ssid));
                    Log.d(LOG_TAG, "WLAN changed, new provider: " + currentProvider);

                    invokeOnNearbyProviderCallbacks(currentProvider);
                }
            } else {
                String lostProvider = currentProvider;
                currentScanResult = null;
                currentProvider = null;
                if (lostProvider != null) {
                    invokeOnProviderLostCallback(lostProvider);
                }
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
        if (currentProvider == null) {
            return null;
        }
        return new ProviderAPInfo(currentScanResult.ssid(), currentScanResult.bssid(), currentScanResult.signal(), currentProvider);
    }

    private Optional<WlanAPInfo> getNearestKnownWlan(List<WlanAPInfo> scanResults) {
        return scanResults.stream()
                .filter(scanResult -> config.isWlanCompatible(scanResult.ssid()))
                .max((o1, o2) -> WifiManager.compareSignalLevel(o1.signal(), o2.signal()));
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

        public void providerNearby(String provider);

        public void providerLost(String provider);
    }

    public static record ProviderAPInfo(String ssid, String bssid, int signalLevel,
                                        String provider) {

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
                    update();
                }
            }
        };

        @Override
        public void register() {
            Log.d(LOG_TAG, "Registering scan results receiver for Android <=Q");
            NoCardApplication.getInstance().registerReceiver(receiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        }

        @Override
        public void unregister() {
            NoCardApplication.getInstance().unregisterReceiver(receiver);
        }
    }

    private record WlanAPInfo(String bssid, String ssid, int signal) {

        public WlanAPInfo(ScanResult scanResult) {
            this(scanResult.BSSID, getWlanSSID(scanResult), scanResult.level);
        }
    }
}
