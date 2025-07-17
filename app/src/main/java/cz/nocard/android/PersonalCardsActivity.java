package cz.nocard.android;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;

import androidx.annotation.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import javax.inject.Inject;

import cz.spojenka.android.util.AsyncUtils;

public class PersonalCardsActivity extends CardListBaseActivity implements PersonalCardStore.Listener {

    @Inject
    PersonalCardStore personalCardStore;
    @Inject
    ConfigManager config;

    private final Map<PersonalCard, ProviderCardView.WithContextMenu> cardViewMap = new HashMap<>();
    private final Map<ProviderCardView, PersonalCard> cardViewInvMap = new HashMap<>();

    private List<String> providerOrder;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        NoCardApplication.getInstance().getApplicationComponent().inject(this);
        providerOrder = config.getAllProviders();
        super.onCreate(savedInstanceState);
        personalCardStore.addListener(this, AsyncUtils.getLifecycleExecutor(this));
        setAddButtonCallback(v -> startActivity(new Intent(this, ImportSpringboardActivity.class)));
    }

    @Override
    protected boolean isAddButtonEnabled() {
        return true;
    }

    @Override
    protected boolean isSelectionModeEnabled() {
        return true;
    }

    @Override
    protected void enterSelectionMode() {
        super.enterSelectionMode();
        overrideTitleText(getString(R.string.share_select_cards));
    }

    @Override
    protected void exitSelectionMode() {
        super.exitSelectionMode();
        overrideTitleText(getTitle());
    }

    @Override
    protected int getSelectionModeButtonIcon() {
        return R.drawable.ic_share_48px;
    }

    @Override
    protected void onSelectionDone(List<ProviderCardView> selected) {
        int[] cardIDs = selected
                .stream()
                .map(cardViewInvMap::get)
                .peek(Objects::requireNonNull)
                .mapToInt(PersonalCard::id)
                .toArray();

        if (cardIDs.length == cardViewInvMap.size()) {
            cardIDs = null; //all
        }

        callCardSharing(cardIDs);
    }

    private void callCardSharing(int... cardIDs) {
        startActivity(ExportMethodJunctionActivity.newIntent(this, cardIDs));
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
        ProviderCardView.WithContextMenu cardView = new ProviderCardView.WithContextMenu(this);

        bindCardToView(cardView, card);

        cardViewMap.put(card, cardView);
        cardViewInvMap.put(cardView, card);

        return cardView;
    }

    private void bindCardToView(ProviderCardView.WithContextMenu cardView, PersonalCard card) {
        cardView.setProvider(card.provider(), personalCardStore.getCardProviderInfo(card, config));
        cardView.overridePrimaryText(personalCardStore.getCardName(card, config));
        cardView.setPopupMenuHandler(popupMenu -> {
            popupMenu.inflate(R.menu.personal_card_context_menu);
            popupMenu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.miRemove) {
                    personalCardStore.removeCard(card);
                } else if (item.getItemId() == R.id.miEasyShare) {
                    callCardSharing(card.id());
                } else if (item.getItemId() == R.id.miRename) {
                    callCardRename(card);
                } else {
                    return false;
                }
                return true;
            });
        });
    }

    private void callCardRename(PersonalCard card) {
        EditText et = new EditText(this);
        et.setText(personalCardStore.getCardName(card, config));
        et.setSelectAllOnFocus(true);
        CommonDialogs
                .newTextInputDialog(this, et)
                .setTitle(R.string.rename_card_prompt)
                .setPositiveButton(R.string.card_action_rename, (dialog, which) -> {
                    personalCardStore.renameCard(card, et.getText().toString());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
        ProviderCardView.WithContextMenu view = cardViewMap.get(card);
        if (view != null) {
            removeCardView(view);
            cardViewMap.remove(card);
            cardViewInvMap.remove(view);
        }
    }

    @Override
    public void onCardChanged(PersonalCard card) {
        ProviderCardView.WithContextMenu view = cardViewMap.get(card);
        if (view != null) {
            bindCardToView(view, card);
        }
    }
}
