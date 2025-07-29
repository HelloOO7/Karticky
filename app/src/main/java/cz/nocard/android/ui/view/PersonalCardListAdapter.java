package cz.nocard.android.ui.view;

import android.view.ViewGroup;

import androidx.annotation.NonNull;

import cz.nocard.android.data.ConfigManager;
import cz.nocard.android.data.NoCardConfig;
import cz.nocard.android.data.PersonalCard;
import cz.nocard.android.data.PersonalCardStore;

public class PersonalCardListAdapter extends PersonalCardListAdapterBase<PersonalCard, ProviderCardView.WithoutAction> {

    private final ConfigManager configManager;
    private final PersonalCardStore personalCardStore;

    public PersonalCardListAdapter(ConfigManager configManager, PersonalCardStore personalCardStore) {
        super(personalCardStore);
        this.configManager = configManager;
        this.personalCardStore = personalCardStore;
    }

    public void onPersonalCardClicked(PersonalCard personalCard) {

    }

    @NonNull
    @Override
    public ProviderCardViewHolder<ProviderCardView.WithoutAction> onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ProviderCardViewHolder<>(parent.getContext(), ProviderCardView.WithoutAction::new);
    }

    @Override
    public void onBindViewHolder(@NonNull ProviderCardViewHolder<ProviderCardView.WithoutAction> holder, int position) {
        PersonalCard personalCard = get(position);
        ProviderCardView providerCard = holder.requirePCV();

        NoCardConfig.ProviderInfo providerInfo = personalCardStore.getCardProviderInfo(personalCard, configManager);
        providerCard.setTag(personalCard);
        providerCard.setProvider(personalCard.provider(), providerInfo);
        providerCard.setOnClickListener(v -> onPersonalCardClicked(personalCard));
    }

    @Override
    protected PersonalCard personalCardToElement(PersonalCard card) {
        return card;
    }

    @Override
    protected PersonalCard elementToPersonalCard(PersonalCard element) {
        return element;
    }
}
