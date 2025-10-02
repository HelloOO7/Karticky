package cz.mamstylcendy.cards.ui.activity;

import android.os.Bundle;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Comparator;

import javax.inject.Inject;

import cz.mamstylcendy.cards.data.ConfigManager;
import cz.mamstylcendy.cards.CardsApplication;
import cz.mamstylcendy.cards.ui.view.ProviderCardView;
import cz.mamstylcendy.cards.R;
import cz.mamstylcendy.cards.data.CardsConfig;
import cz.mamstylcendy.cards.data.CardsPreferences;
import cz.mamstylcendy.cards.data.PersonalCard;
import cz.mamstylcendy.cards.ui.view.ProviderCardViewHolder;
import cz.spojenka.android.ui.helpers.ArrayListAdapter;

public class ManageBlacklistActivity extends CardListBaseActivity {

    @Inject
    CardsPreferences prefs;
    @Inject
    ConfigManager config;

    private ArrayListAdapter<BlacklistItem, ProviderCardViewHolder<ProviderCardView.WithRemoveAction>> adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        CardsApplication.getInstance().getApplicationComponent().inject(this);
        initAdapter();
        super.onCreate(savedInstanceState);
    }

    private void initAdapter() {
        adapter = new ArrayListAdapter<>() {
            @NonNull
            @Override
            public ProviderCardViewHolder<ProviderCardView.WithRemoveAction> onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new ProviderCardViewHolder<>(parent.getContext(), ProviderCardView.WithRemoveAction::new);
            }

            @Override
            public void onBindViewHolder(@NonNull ProviderCardViewHolder<ProviderCardView.WithRemoveAction> holder, int position) {
                BlacklistItem item = get(position);
                CardsConfig.ProviderInfo pi = config.getProviderInfo(item.provider());
                ProviderCardView.WithRemoveAction providerCard = holder.requirePCV();

                providerCard.setProvider(item.provider(), pi);
                providerCard.overridePrimaryText(getCardInfoText(item));

                providerCard.setOnRemoveListener(() -> {
                    prefs.removeCardFromBlacklist(item.provider(), item.cardNumber());
                    adapter.remove(item);
                });
            }
        };
    }

    @Override
    protected RecyclerView.Adapter<?> getAdapter() {
        return adapter;
    }

    @Override
    protected void populateCardList() {
        config.getAllProviders()
                .stream()
                .flatMap(provider -> prefs.getCardBlacklist(provider)
                        .stream()
                        .map(cardNumber -> new BlacklistItem(provider, cardNumber))
                        .sorted(Comparator.comparing(BlacklistItem::cardNumber))
                )
                .forEach(adapter::add);
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
