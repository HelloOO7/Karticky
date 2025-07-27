package cz.nocard.android.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.inject.Inject;

import cz.nocard.android.databinding.MultiSelectionControlsBinding;
import cz.nocard.android.ui.dialogs.CommonDialogs;
import cz.nocard.android.data.ConfigManager;
import cz.nocard.android.NoCardApplication;
import cz.nocard.android.ui.view.ProviderCardView;
import cz.nocard.android.R;
import cz.nocard.android.data.PersonalCard;
import cz.nocard.android.data.PersonalCardStore;
import cz.nocard.android.ui.view.ProviderCardViewHolder;
import cz.spojenka.android.ui.helpers.ArrayListAdapter;
import cz.spojenka.android.util.AsyncUtils;

public class PersonalCardsActivity extends CardListBaseActivity implements PersonalCardStore.Listener {

    private static final String STATE_IN_SELECTION_MODE = "in_selection_mode";

    @Inject
    PersonalCardStore personalCardStore;
    @Inject
    ConfigManager config;

    private final Map<PersonalCard, PersonalCardState> cardToStateMap = new HashMap<>();

    private List<String> providerOrder;

    private MultiSelectionControlsBinding multiSelectionControls;
    private boolean inSelectionMode = false;

    private final OnBackPressedCallback exitSelectionModeOnBack = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            exitSelectionMode();
        }
    };

    private ArrayListAdapter<PersonalCardState, ProviderCardViewHolder<ProviderCardView.WithContextMenu>> adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        NoCardApplication.getInstance().getApplicationComponent().inject(this);
        providerOrder = config.getAllProviders();
        initAdapter();
        super.onCreate(savedInstanceState);
        personalCardStore.addListener(this, AsyncUtils.getLifecycleExecutor(this));
        setAddButtonCallback(v -> startActivity(new Intent(this, ImportSpringboardActivity.class)));

        if (savedInstanceState != null) {
            inSelectionMode = savedInstanceState.getBoolean(STATE_IN_SELECTION_MODE);
        }

        ui.btnSelectionMode.setVisibility(View.VISIBLE);
        ui.btnSelectionMode.setImageResource(getSelectionModeButtonIcon());
        ui.btnSelectionMode.setOnClickListener(v -> {
            if (inSelectionMode) {
                List<PersonalCardState> selected = collectSelectedStates();
                exitSelectionMode();
                if (!selected.isEmpty()) {
                    onSelectionDone(selected);
                }
            } else {
                enterSelectionMode();
            }
        });
        ui.btnExitSelectionMode.setOnClickListener(v -> exitSelectionMode());
        multiSelectionControls = MultiSelectionControlsBinding.inflate(getLayoutInflater());
        Consumer<Boolean> omniSelectFunc = (state) -> forAllCardStates((index, cardView) -> {
            if (cardView.selected != state) {
                cardView.selected = state;
                adapter.notifyItemChanged(index);
            }
        });
        multiSelectionControls.btnSelectAll.setOnClickListener(v -> omniSelectFunc.accept(true));
        multiSelectionControls.btnSelectNone.setOnClickListener(v -> omniSelectFunc.accept(false));
        getOnBackPressedDispatcher().addCallback(exitSelectionModeOnBack);

        if (inSelectionMode) {
            enterSelectionMode();
        }
    }

    private void initAdapter() {
        adapter = new ArrayListAdapter<>() {
            @NonNull
            @Override
            public ProviderCardViewHolder<ProviderCardView.WithContextMenu> onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new ProviderCardViewHolder<>(parent.getContext(), ProviderCardView.WithContextMenu::new);
            }

            @Override
            public void onBindViewHolder(@NonNull ProviderCardViewHolder<ProviderCardView.WithContextMenu> holder, int position) {
                ProviderCardView.WithContextMenu cardView = holder.requirePCV();
                PersonalCardState cardState = get(position);
                PersonalCard card = cardState.getCard();

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

                if (cardState.selecting) {
                    cardView.enterSelectionMode();
                } else {
                    cardView.exitSelectionMode();
                }
                cardView.setUserSelected(cardState.selected);
            }
        };
    }

    private void forAllCardStates(BiConsumer<Integer, PersonalCardState> action) {
        for (int i = 0; i < adapter.getItemCount(); i++) {
            action.accept(i, adapter.get(i));
        }
    }

    @Override
    protected RecyclerView.Adapter<?> getAdapter() {
        return adapter;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_IN_SELECTION_MODE, inSelectionMode);
    }

    @Override
    protected boolean isAddButtonEnabled() {
        return true;
    }

    protected List<PersonalCardState> collectSelectedStates() {
        return adapter.stream()
                .filter(cardState -> cardState.selected)
                .toList();
    }

    protected void enterSelectionMode() {
        inSelectionMode = true;
        ui.btnExitSelectionMode.setVisibility(View.VISIBLE);
        ui.btnSelectionMode.setImageResource(R.drawable.ic_check_48px);
        exitSelectionModeOnBack.setEnabled(true);
        ui.llTopControls.addView(multiSelectionControls.getRoot(), 0);
        ui.llTopControls.setVisibility(View.VISIBLE);
        ui.fabAddCard.setVisibility(View.GONE);
        forAllCardStates((index, pc) -> pc.enterSelectionMode());
        adapter.notifyDataSetChanged();
        overrideTitleText(getString(R.string.share_select_cards));
    }

    protected void exitSelectionMode() {
        inSelectionMode = false;
        ui.llTopControls.removeView(multiSelectionControls.getRoot());
        ui.llTopControls.setVisibility(View.GONE);
        ui.btnExitSelectionMode.setVisibility(View.GONE);
        ui.btnSelectionMode.setImageResource(getSelectionModeButtonIcon());
        exitSelectionModeOnBack.setEnabled(false);
        if (isAddButtonEnabled()) {
            ui.fabAddCard.setVisibility(View.VISIBLE);
        }
        forAllCardStates((index, pc) -> pc.exitSelectionMode());
        adapter.notifyDataSetChanged();
        overrideTitleText(getTitle());
    }

    @Override
    protected int getSelectionModeButtonIcon() {
        return R.drawable.ic_share_48px;
    }

    protected void onSelectionDone(List<PersonalCardState> selected) {
        int[] cardIDs = selected
                .stream()
                .map(PersonalCardState::getCard)
                .peek(Objects::requireNonNull)
                .mapToInt(PersonalCard::id)
                .toArray();

        if (cardIDs.length == adapter.size()) {
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
    protected void populateCardList() {
        personalCardStore.getPersonalCards()
                .stream()
                .sorted(cardOrderComparator())
                .map(personalCard -> {
                    PersonalCardState state = new PersonalCardState(personalCard);
                    cardToStateMap.put(personalCard, state);
                    return state;
                })
                .forEach(adapter::add);
    }

    private Comparator<PersonalCard> cardOrderComparator() {
        return Comparator
                .comparing((PersonalCard card) -> providerOrder.indexOf(card.provider()))
                .thenComparing((PersonalCard card) -> Optional.ofNullable(card.cardNumber()).orElse(""));
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
        PersonalCardState cardState = new PersonalCardState(card);
        Comparator<PersonalCard> cardOrderComparator = cardOrderComparator();
        insertCardState(cardState, (o1, o2) -> cardOrderComparator.compare(o1.getCard(), o2.getCard()));
    }

    protected void insertCardState(PersonalCardState state, Comparator<PersonalCardState> positionDecisionMaker) {
        int insertIndex = adapter.size();
        for (int i = 0; i < adapter.size(); i++) {
            PersonalCardState existingState = adapter.get(i);
            if (positionDecisionMaker.compare(existingState, state) > 0) {
                insertIndex = i;
                break;
            }
        }
        adapter.add(insertIndex, state);
    }

    @Override
    public void onCardRemoved(PersonalCard card) {
        PersonalCardState state = cardToStateMap.get(card);
        if (state != null) {
            adapter.remove(state);
            cardToStateMap.remove(card);
        }
    }

    @Override
    public void onCardChanged(PersonalCard card) {
        PersonalCardState state = cardToStateMap.get(card);
        if (state != null) {
            adapter.notifyItemChanged(adapter.indexOf(state));
        }
    }

    public static class PersonalCardState {

        public PersonalCard card;
        public boolean selecting;
        public boolean selected;

        public PersonalCardState(PersonalCard card) {
            this.card = card;
        }

        public PersonalCard getCard() {
            return card;
        }

        public void enterSelectionMode() {
            selecting = true;
            selected = false;
        }

        public void exitSelectionMode() {
            selecting = false;
            selected = false;
        }
    }
}
