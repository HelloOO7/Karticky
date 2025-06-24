package cz.nocard.android;

import android.os.Bundle;

import androidx.annotation.Nullable;

import java.util.Comparator;
import java.util.function.Consumer;

import javax.inject.Inject;

public class ManageBlacklistActivity extends CardListBaseActivity {

    @Inject
    NoCardPreferences prefs;
    @Inject
    ConfigManager config;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        NoCardApplication.getInstance().getApplicationComponent().inject(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void populateCardList(Consumer<ProviderCardView> callMeMaybe) {
        config.getAllProviders()
                .stream()
                .flatMap(provider -> prefs.getCardBlacklist(provider)
                        .stream()
                        .map(cardNumber -> new BlacklistItem(provider, cardNumber))
                        .sorted(Comparator.comparing(BlacklistItem::cardNumber))
                )
                .map(item -> {
                    NoCardConfig.ProviderInfo pi = config.getProviderInfo(item.provider());

                    ProviderCardView.WithRemoveAction providerCard = new ProviderCardView.WithRemoveAction(this);
                    providerCard.setProvider(item.provider(), pi);
                    providerCard.overridePrimaryText(getCardInfoText(item));

                    providerCard.setOnRemoveListener(() -> {
                        prefs.removeCardFromBlacklist(item.provider(), item.cardNumber());
                        removeCardView(providerCard);
                    });

                    return providerCard;
                })
                .forEach(callMeMaybe);
    }

    @Override
    protected int getBlankPlaceholderText() {
        return R.string.blacklist_empty_placeholder;
    }

    private String getCardInfoText(BlacklistItem item) {
        return PersonalCard.formatDefaultName(config.getProviderNameOrDefault(item.provider()), item.cardNumber());
    }

    private static record BlacklistItem(String provider, String cardNumber) {

    }
}
