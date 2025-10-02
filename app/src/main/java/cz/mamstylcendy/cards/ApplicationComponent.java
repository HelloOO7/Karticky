package cz.mamstylcendy.cards;

import javax.inject.Singleton;

import cz.mamstylcendy.cards.beacon.BackgroundWlanCheckWorker;
import cz.mamstylcendy.cards.sharing.CardTransfer;
import cz.mamstylcendy.cards.sharing.NfcExportHceService;
import cz.mamstylcendy.cards.ui.activity.ExportActivityCommon;
import cz.mamstylcendy.cards.ui.activity.ImportSpringboardActivity;
import cz.mamstylcendy.cards.ui.activity.MainActivity;
import cz.mamstylcendy.cards.ui.activity.ManageBlacklistActivity;
import cz.mamstylcendy.cards.ui.activity.NfcExportActivity;
import cz.mamstylcendy.cards.ui.activity.NfcImportActivity;
import cz.mamstylcendy.cards.ui.activity.PersonalCardsActivity;
import cz.mamstylcendy.cards.ui.dialogs.SettingsSheetFragment;
import dagger.Component;

@Singleton
@Component(modules = {ApplicationModule.class})
public interface ApplicationComponent {
    void inject(CardsApplication cardsApplication);
    void inject(MainActivity mainActivity);
    void inject(BackgroundWlanCheckWorker backgroundWlanCheckWorker);
    void inject(SettingsSheetFragment settingsSheetFragment);
    void inject(ManageBlacklistActivity manageBlacklistActivity);
    void inject(ImportSpringboardActivity importSpringboardActivity);
    void inject(PersonalCardsActivity personalCardsActivity);
    void inject(NfcExportHceService nfcExportHceService);
    void inject(CardTransfer cardTransfer);
    void inject(NfcImportActivity nfcImportActivity);
    void inject(NfcExportActivity nfcExportActivity);
    void inject(ExportActivityCommon exportActivityBase);
}
