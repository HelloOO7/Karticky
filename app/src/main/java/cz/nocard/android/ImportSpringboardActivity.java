package cz.nocard.android;

import android.content.Intent;
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

    private ActivityResultLauncher<BarcodeFormat> codeImportLauncher;
    private ActivityResultLauncher<Void> newCustomCardLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        NoCardApplication.getInstance().getApplicationComponent().inject(this);
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            importingProvider = savedInstanceState.getString(STATE_IMPORTING_PROVIDER, null);
        }

        codeImportLauncher = registerForActivityResult(ImportMethodJunctionActivity.IMPORT_CARD_CODE, cardCode -> {
            if (cardCode != null) {
                PersonalCard card = new PersonalCard(
                        personalCardStore.newCardId(),
                        null,
                        importingProvider,
                        cardCode
                );
                onNewCardResultReceived(card);
            }
        });

        newCustomCardLauncher = registerForActivityResult(NewCustomCardActivity.NEW_CUSTOM_CARD, newCard -> {
            if (newCard != null) {
                onNewCardResultReceived(newCard);
            }
        });
    }

    private void onNewCardResultReceived(PersonalCard card) {
        if (personalCardStore.cardAlreadyExists(card)) {
            CommonDialogs.newInfoDialog(this, R.string.card_already_exists_title, R.string.card_already_exists_desc).show();
        } else {
            personalCardStore.addCard(card);
            finish();
        }
    }

    @Override
    protected void populateCardList(Consumer<ProviderCardView> callMeMaybe) {
        ProviderCardView batchImportCard = new ProviderCardView.WithoutAction(this);
        batchImportCard.overridePrimaryText(getString(R.string.title_batch_import));
        batchImportCard.setIcon(R.drawable.ic_p2p_40px);
        batchImportCard.setOnClickListener(v -> {
            finish();
            startActivity(new Intent(this, NfcImportActivity.class));
        });
        callMeMaybe.accept(batchImportCard);

        ProviderCardView customImportCard = new ProviderCardView.WithoutAction(this);
        customImportCard.overridePrimaryText(getString(R.string.title_custom_card));
        customImportCard.setIcon(R.drawable.ic_brush_40px);
        customImportCard.setCustomChipGradient(getResources().getIntArray(R.array.rainbow_gradient));
        customImportCard.setOnClickListener(v -> newCustomCardLauncher.launch(null));
        callMeMaybe.accept(customImportCard);

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
        codeImportLauncher.launch(config.getProviderInfo(providerId).format());
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
