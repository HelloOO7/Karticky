package cz.nocard.android;

import javax.inject.Singleton;

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
