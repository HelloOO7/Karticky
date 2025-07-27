package cz.nocard.android.ui.activity;

import android.os.Bundle;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Comparator;

import javax.inject.Inject;

import cz.nocard.android.data.ConfigManager;
import cz.nocard.android.NoCardApplication;
import cz.nocard.android.ui.view.ProviderCardView;
import cz.nocard.android.R;
import cz.nocard.android.data.NoCardConfig;
import cz.nocard.android.data.NoCardPreferences;
import cz.nocard.android.data.PersonalCard;
import cz.nocard.android.ui.view.ProviderCardViewHolder;
import cz.spojenka.android.ui.helpers.ArrayListAdapter;

public class ManageBlacklistActivity extends CardListBaseActivity {

    @Inject
    NoCardPreferences prefs;
    @Inject
    ConfigManager config;

    private ArrayListAdapter<BlacklistItem, ProviderCardViewHolder<ProviderCardView.WithRemoveAction>> adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        NoCardApplication.getInstance().getApplicationComponent().inject(this);
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
                NoCardConfig.ProviderInfo pi = config.getProviderInfo(item.provider());
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
