package cz.nocard.android;

import android.content.res.Resources;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.google.zxing.BarcodeFormat;

import java.util.function.Consumer;

import javax.inject.Inject;

public class ImportSpringboardActivity extends CardListBaseActivity {

    private static final String STATE_IMPORTING_PROVIDER = "importing_provider";

    @Inject
    ConfigManager config;
    @Inject
    PersonalCardStore personalCardStore;

    private String importingProvider;

    private ActivityResultLauncher<BarcodeFormat> importLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        NoCardApplication.getInstance().getApplicationComponent().inject(this);
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            importingProvider = savedInstanceState.getString(STATE_IMPORTING_PROVIDER, null);
        }

        importLauncher = registerForActivityResult(ImportMethodJunctionActivity.IMPORT_CARD_CODE, cardCode -> {
            if (cardCode != null) {
                PersonalCard card = new PersonalCard(
                        personalCardStore.newCardId(),
                        PersonalCard.formatDefaultName(config.getProviderNameOrDefault(importingProvider), cardCode),
                        importingProvider,
                        cardCode
                );
                personalCardStore.addCard(card);
                finish();
            }
        });
    }

    @Override
    protected void populateCardList(Consumer<ProviderCardView> callMeMaybe) {
        config.getAllProviders()
                .stream()
                .map(provider -> {
                    NoCardConfig.ProviderInfo pi = config.getProviderInfo(provider);

                    ProviderCardView card = new ProviderCardView.WithoutAction(this);
                    card.setProvider(provider, pi);
                    card.setOnClickListener(v -> callCardImport(provider));

                    return card;
                })
                .forEach(callMeMaybe);
    }

    private void callCardImport(String providerId) {
        importingProvider = providerId;
        importLauncher.launch(config.getProviderInfo(providerId).format());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_IMPORTING_PROVIDER, importingProvider);
    }

    @Override
    protected int getBlankPlaceholderText() {
        return ResourcesCompat.ID_NULL;
    }
}
