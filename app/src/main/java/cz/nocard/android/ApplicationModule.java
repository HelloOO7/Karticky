package cz.nocard.android;

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
    public NoCardPreferences appPreferences() {
        return new NoCardPreferences(application);
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

    @Provides
    @Singleton
    public CardNotificationManager cardNotificationManager(NoCardPreferences prefs, ConfigManager configManager) {
        return new CardNotificationManager(application, prefs, configManager);
    }

    @Provides
    @Singleton
    public PersonalCardStore personalCardStore(NoCardPreferences preferences) {
        return new PersonalCardStore(preferences);
    }
}
