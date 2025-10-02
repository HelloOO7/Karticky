package cz.mamstylcendy.cards;

import javax.inject.Singleton;

import cz.mamstylcendy.cards.beacon.CardNotificationManager;
import cz.mamstylcendy.cards.beacon.WlanFencingManager;
import cz.mamstylcendy.cards.data.CardsPreferences;
import cz.mamstylcendy.cards.sharing.NfcExportServiceState;
import cz.mamstylcendy.cards.data.ConfigManager;
import cz.mamstylcendy.cards.data.PersonalCardStore;
import dagger.Module;
import dagger.Provides;

@Module
public class ApplicationModule {

    private final CardsApplication application;

    public ApplicationModule(CardsApplication application) {
        this.application = application;
    }

    @Provides
    @Singleton
    public CardsPreferences appPreferences() {
        return new CardsPreferences(application);
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
    public CardNotificationManager cardNotificationManager(CardsPreferences prefs, ConfigManager configManager) {
        return new CardNotificationManager(application, prefs, configManager);
    }

    @Provides
    @Singleton
    public PersonalCardStore personalCardStore(CardsPreferences preferences) {
        return new PersonalCardStore(preferences);
    }

    @Provides
    @Singleton
    public NfcExportServiceState nfcExportServiceState() {
        return new NfcExportServiceState();
    }
}
