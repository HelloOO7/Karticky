package cz.nocard.android;

import android.net.wifi.WifiManager;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class ApplicationModule {

    private final NoCardApplication application;

    public ApplicationModule(NoCardApplication application) {
        this.application = application;
    }

    @Provides
    @Singleton
    public ConfigManager configManager() {
        return new ConfigManager(application);
    }

    @Provides
    @Singleton
    public WlanFencingManager wlanFencingManager(ConfigManager config) {
        return new WlanFencingManager(application, config);
    }
}
