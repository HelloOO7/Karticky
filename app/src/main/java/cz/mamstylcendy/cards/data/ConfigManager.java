package cz.mamstylcendy.cards.data;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Predicate;

import cz.mamstylcendy.cards.BuildConfig;
import cz.spojenka.android.polyfills.InputStreamCompat;

public class ConfigManager {

    private static final String LOG_TAG = ConfigManager.class.getSimpleName();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String CONFIG_FILE_NAME = "config.json";

    private final Random codeRandom = new Random();

    private final Context appContext;
    private CardsConfig config;

    public ConfigManager(Context appContext) {
        this.appContext = appContext;
        try {
            boolean useAssets;
            if (loadFromAppData()) {
                useAssets = isBuiltAfterLocalConfig();
            } else {
                useAssets = true;
            }
            if (useAssets) {
                copyFromAssets();
                if (!loadFromAppData()) {
                    throw new IllegalStateException("Upon copying from assets, the config file still could not be loaded.");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize ConfigManager", e);
        }
    }

    private boolean isBuiltAfterLocalConfig() {
        File localConfigFile = getLocalConfigFile();
        if (localConfigFile.exists()) {
            long localConfigTime = localConfigFile.lastModified();
            return BuildConfig.BUILD_TIME.isAfter(Instant.ofEpochMilli(localConfigTime));
        }
        return true; // If the file does not exist, we assume we need to use the assets version
    }

    private File getLocalConfigFile() {
        return appContext.getDatabasePath(CONFIG_FILE_NAME);
    }

    private boolean loadFromAppData() {
        File file = getLocalConfigFile();
        if (file.exists()) {
            try {
                config = OBJECT_MAPPER.readValue(file, CardsConfig.class);
                return true;
            } catch (IOException e) {
                Log.e(LOG_TAG, "Failed to load config from app data", e);
            }
        }
        return false;
    }

    private void copyFromAssets() throws IOException {
        try (InputStream in = appContext.getAssets().open(CONFIG_FILE_NAME); OutputStream out = new FileOutputStream(getLocalConfigFile())) {
            InputStreamCompat.transferTo(in, out);
        }
    }

    public synchronized CardsConfig getCurrentConfig() {
        return config;
    }

    public synchronized void updateConfig(CardsConfig newConfig) throws IOException {
        this.config = newConfig;
        OBJECT_MAPPER.writeValue(getLocalConfigFile(), newConfig);
    }

    public List<String> getAllProviders() {
        return new ArrayList<>(getCurrentConfig().cardData().keySet());
    }

    public CardsConfig.ProviderInfo getProviderInfo(String key) {
        return Objects.requireNonNull(getProviderInfoOrNull(key));
    }

    public CardsConfig.ProviderInfo getProviderInfoOrNull(String key) {
        return getCurrentConfig().cardData().get(key);
    }

    public String getProviderNameOrDefault(String key) {
        CardsConfig.ProviderInfo pi = getProviderInfoOrNull(key);
        if (pi != null && !TextUtils.isEmpty(pi.providerName())) {
            return pi.providerName();
        } else {
            return key;
        }
    }

    public String getRandomCode(CardsConfig.ProviderInfo providerInfo) {
        return getRandomCode(providerInfo, code -> true);
    }

    public String getRandomCode(CardsConfig.ProviderInfo providerInfo, Predicate<String> filter) {
        List<String> filteredCodes = providerInfo.codes().stream().filter(filter).toList();
        if (filteredCodes.isEmpty()) {
            return null;
        }
        return filteredCodes.get(codeRandom.nextInt(filteredCodes.size()));
    }

    public boolean isWlanCompatible(String ssid) {
        return getCurrentConfig().wlanMappings().containsKey(unquoteSsid(ssid));
    }

    private String unquoteSsid(String ssid) {
        // textova SSID maji v androidu na zacatku a konci uvozovky
        if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            return ssid.substring(1, ssid.length() - 1);
        }
        return ssid;
    }

    public String getProviderForWlan(String ssid) {
        return getCurrentConfig().wlanMappings().get(unquoteSsid(ssid));
    }
}
