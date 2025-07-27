package cz.nocard.android.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.google.zxing.BarcodeFormat;

import java.util.function.Consumer;

import javax.inject.Inject;

import cz.nocard.android.ui.dialogs.CommonDialogs;
import cz.nocard.android.data.ConfigManager;
import cz.nocard.android.NoCardApplication;
import cz.nocard.android.ui.view.ProviderCardView;
import cz.nocard.android.R;
import cz.nocard.android.data.NoCardConfig;
import cz.nocard.android.data.PersonalCard;
import cz.nocard.android.data.PersonalCardStore;
import cz.nocard.android.ui.view.ProviderCardViewHolder;
import cz.nocard.android.ui.view.UniversalCardListAdapter;

public class ImportSpringboardActivity extends CardListBaseActivity {

    private static final String STATE_IMPORTING_PROVIDER = "importing_provider";

    @Inject
    ConfigManager config;
    @Inject
    PersonalCardStore personalCardStore;

    private String importingProvider;

    private ActivityResultLauncher<BarcodeFormat> codeImportLauncher;
    private ActivityResultLauncher<Void> newCustomCardLauncher;
    private UniversalCardListAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        NoCardApplication.getInstance().getApplicationComponent().inject(this);
        initAdapter();
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

    private void initAdapter() {
        adapter = new UniversalCardListAdapter(config) {

            private static final int VIEW_TYPE_NORMAL = 0;
            private static final int VIEW_TYPE_BTN_IMPORT = 1;
            private static final int VIEW_TYPE_BTN_CUSTOM = 2;

            @Override
            public int getItemViewType(int position) {
                return switch (position) {
                    case 0 -> VIEW_TYPE_BTN_IMPORT;
                    case 1 -> VIEW_TYPE_BTN_CUSTOM;
                    default -> VIEW_TYPE_NORMAL;
                };
            }

            @Override
            protected void onProviderClicked(String provider) {
                callCardImport(provider);
            }

            @NonNull
            @Override
            public ProviderCardViewHolder<ProviderCardView.WithFavouriteAction> onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                if (viewType == VIEW_TYPE_BTN_IMPORT) {
                    return ProviderCardViewHolder.createExtraViewHolder(createImportBtnView());
                } else if (viewType == VIEW_TYPE_BTN_CUSTOM) {
                    return ProviderCardViewHolder.createExtraViewHolder(createCustomCardBtnView());
                }
                return super.onCreateViewHolder(parent, viewType);
            }

            @Override
            public void onBindViewHolder(@NonNull ProviderCardViewHolder<ProviderCardView.WithFavouriteAction> holder, int position) {
                if (get(position) == null) {
                    return;
                }
                super.onBindViewHolder(holder, position);
            }
        };
    }

    @Override
    public UniversalCardListAdapter getAdapter() {
        return adapter;
    }

    private void onNewCardResultReceived(PersonalCard card) {
        if (personalCardStore.cardAlreadyExists(card)) {
            CommonDialogs.newInfoDialog(this, R.string.card_already_exists_title, R.string.card_already_exists_desc).show();
        } else {
            personalCardStore.addCard(card);
            finish();
        }
    }

    private ProviderCardView createImportBtnView() {
        ProviderCardView batchImportCard = new ProviderCardView.WithoutAction(this);
        batchImportCard.overridePrimaryText(getString(R.string.title_batch_import));
        batchImportCard.setIcon(R.drawable.ic_p2p_40px);
        batchImportCard.setOnClickListener(v -> {
            finish();
            startActivity(new Intent(this, NfcImportActivity.class));
        });
        return batchImportCard;
    }

    private ProviderCardView createCustomCardBtnView() {
        ProviderCardView customImportCard = new ProviderCardView.WithoutAction(this);
        customImportCard.overridePrimaryText(getString(R.string.title_custom_card));
        customImportCard.setIcon(R.drawable.ic_brush_40px);
        customImportCard.setCustomChipGradient(getResources().getIntArray(R.array.rainbow_gradient));
        customImportCard.setOnClickListener(v -> newCustomCardLauncher.launch(null));
        return customImportCard;
    }

    @Override
    protected void populateCardList() {
        adapter.add(null);
        adapter.add(null);
        adapter.addAll(config.getAllProviders());
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
