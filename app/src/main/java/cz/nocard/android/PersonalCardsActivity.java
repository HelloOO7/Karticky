package cz.nocard.android;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import javax.inject.Inject;

public class PersonalCardsActivity extends CardListBaseActivity implements PersonalCardStore.Listener {

    @Inject
    PersonalCardStore personalCardStore;
    @Inject
    ConfigManager config;

    private final Map<PersonalCard, ProviderCardView.WithRemoveAction> cardViewMap = new HashMap<>();
    private final Map<ProviderCardView, PersonalCard> cardViewInvMap = new HashMap<>();

    private List<String> providerOrder;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        NoCardApplication.getInstance().getApplicationComponent().inject(this);
        providerOrder = config.getAllProviders();
        super.onCreate(savedInstanceState);
        personalCardStore.addListener(this);
        setAddButtonCallback(v -> startActivity(new Intent(this, ImportSpringboardActivity.class)));
    }

    @Override
    protected boolean isAddButtonEnabled() {
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        personalCardStore.removeListener(this);
    }

    @Override
    protected void populateCardList(Consumer<ProviderCardView> callMeMaybe) {
        personalCardStore.getPersonalCards()
                .stream()
                .sorted(cardOrderComparator())
                .map(this::newCardView)
                .forEach(callMeMaybe);
    }

    private Comparator<PersonalCard> cardOrderComparator() {
        return Comparator
                .comparing((PersonalCard card) -> providerOrder.indexOf(card.provider()))
                .thenComparing((PersonalCard card) -> Optional.ofNullable(card.cardNumber()).orElse(""));
    }

    private ProviderCardView newCardView(PersonalCard card) {
        ProviderCardView.WithRemoveAction cardView = new ProviderCardView.WithRemoveAction(this);

        bindCardToView(cardView, card);

        cardViewMap.put(card, cardView);
        cardViewInvMap.put(cardView, card);

        return cardView;
    }

    private void bindCardToView(ProviderCardView.WithRemoveAction cardView, PersonalCard card) {
        cardView.setProvider(card.provider(), config.getProviderInfoOrNull(card.provider()));
        cardView.overridePrimaryText(card.name());
        cardView.setOnRemoveListener(() -> personalCardStore.removeCard(card));
    }

    @Override
    protected int getBlankPlaceholderText() {
        return R.string.personal_cards_empty_placeholder;
    }

    @Override
    public void onCardAdded(PersonalCard card) {
        ProviderCardView cardView = newCardView(card);
        Comparator<PersonalCard> cardOrderComparator = cardOrderComparator();
        insertCardView(cardView, new Comparator<ProviderCardView>() {
            @Override
            public int compare(ProviderCardView o1, ProviderCardView o2) {
                PersonalCard card1 = cardViewInvMap.get(o1);
                PersonalCard card2 = cardViewInvMap.get(o2);
                if (card1 == null || card2 == null) {
                    return 0;
                }
                return cardOrderComparator.compare(card1, card2);
            }
        });
    }

    @Override
    public void onCardRemoved(PersonalCard card) {
        ProviderCardView.WithRemoveAction view = cardViewMap.get(card);
        if (view != null) {
            removeCardView(view);
            cardViewMap.remove(card);
            cardViewInvMap.remove(view);
        }
    }

    @Override
    public void onCardChanged(PersonalCard card) {
        ProviderCardView.WithRemoveAction view = cardViewMap.get(card);
        if (view != null) {
            bindCardToView(view, card);
        }
    }
}
