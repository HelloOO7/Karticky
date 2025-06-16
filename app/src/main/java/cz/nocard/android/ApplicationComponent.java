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
}
