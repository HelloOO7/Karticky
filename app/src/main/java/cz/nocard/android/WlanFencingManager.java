package cz.nocard.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiSsid;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class WlanFencingManager {

    private static final String LOG_TAG = WlanFencingManager.class.getSimpleName();

    private final WifiManager wifiManager;
    private final ConfigManager config;

    private boolean registered = false;

    private String currentProviderSSID = null;
    private ScanResult currentScanResult = null;
    private String currentProvider = null;

    private final List<OnNearbyProviderCallback> callbacks = new ArrayList<>();

    private final ReceiverImpl receiverImpl;

    public WlanFencingManager(WifiManager wifiManager, ConfigManager config) {
        this.wifiManager = wifiManager;
        this.config = config;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            receiverImpl = new Api30Impl();
        } else {
            receiverImpl = new LegacyImpl();
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
        registered = true;
    }

    private void unregister() {
        if (!registered) {
            return;
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
            List<ScanResult> lastScanResults = wifiManager.getScanResults();
            Log.d(LOG_TAG, "Received " + lastScanResults.size() + " scan results");
            for (var result : lastScanResults) {
                Log.d(LOG_TAG, "SSID: " + getWlanSSID(result) + ", Level: " + result.level);
            }
            Optional<ScanResult> nearest = getNearestKnownWlan(lastScanResults);

            if (nearest.isPresent()) {
                String ssid = getWlanSSID(nearest.get());
                Log.d(LOG_TAG, "Nearest known WLAN: " + ssid);
                if (!Objects.equals(ssid, currentProviderSSID)) {
                    currentProviderSSID = ssid;
                    currentScanResult = nearest.get();
                    currentProvider = Objects.requireNonNull(config.getProviderForWlan(ssid));
                    Log.d(LOG_TAG, "WLAN changed, new provider: " + currentProvider);

                    invokeOnNearbyProviderCallbacks(currentProvider);
                }
            } else {
                String lostProvider = currentProvider;
                currentProviderSSID = null;
                currentScanResult = null;
                currentProvider = null;
                invokeOnProviderLostCallback(lostProvider);
            }
        } catch (SecurityException ex) {
            Log.e(LOG_TAG, "Failed to get scan results due to missing permissions (unexpected)", ex);
        }
        return getCurrentProviderAP();
    }

    public ProviderAPInfo getCurrentProviderAP() {
        if (currentProvider == null) {
            return null;
        }
        return new ProviderAPInfo(currentProviderSSID, currentScanResult.BSSID, currentScanResult.level, currentProvider);
    }

    private Optional<ScanResult> getNearestKnownWlan(List<ScanResult> scanResults) {
        return scanResults.stream()
                .filter(scanResult -> config.isWlanCompatible(getWlanSSID(scanResult)))
                .max((o1, o2) -> WifiManager.compareSignalLevel(o1.level, o2.level));
    }

    @SuppressWarnings("deprecation")
    private String getWlanSSID(ScanResult result) {
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
}
