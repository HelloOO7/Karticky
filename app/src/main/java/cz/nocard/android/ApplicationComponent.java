package cz.nocard.android;

import javax.inject.Singleton;

import cz.nocard.android.beacon.BackgroundWlanCheckWorker;
import cz.nocard.android.sharing.CardTransfer;
import cz.nocard.android.sharing.NfcExportHceService;
import cz.nocard.android.ui.activity.ExportActivityCommon;
import cz.nocard.android.ui.activity.ImportSpringboardActivity;
import cz.nocard.android.ui.activity.MainActivity;
import cz.nocard.android.ui.activity.ManageBlacklistActivity;
import cz.nocard.android.ui.activity.NfcExportActivity;
import cz.nocard.android.ui.activity.NfcImportActivity;
import cz.nocard.android.ui.activity.PersonalCardsActivity;
import cz.nocard.android.ui.dialogs.SettingsSheetFragment;
import dagger.Component;

@Singleton
@Component(modules = {ApplicationModule.class})
public interface ApplicationComponent {
    void inject(NoCardApplication noCardApplication);
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
