package cz.mamstylcendy.cards.ui.view;

import android.view.ViewGroup;

import androidx.annotation.NonNull;

import cz.mamstylcendy.cards.data.ConfigManager;
import cz.mamstylcendy.cards.data.CardsConfig;
import cz.mamstylcendy.cards.data.PersonalCard;
import cz.mamstylcendy.cards.data.PersonalCardStore;

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

        CardsConfig.ProviderInfo providerInfo = personalCardStore.getCardProviderInfo(personalCard, configManager);
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
