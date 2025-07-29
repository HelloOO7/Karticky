package cz.nocard.android.ui.view;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import cz.nocard.android.data.PersonalCard;
import cz.nocard.android.data.PersonalCardStore;
import cz.spojenka.android.ui.helpers.ArrayListAdapter;
import cz.spojenka.android.ui.helpers.ListReorderHelper;

public abstract class PersonalCardListAdapterBase<E, PCV extends ProviderCardView> extends ArrayListAdapter<E, ProviderCardViewHolder<PCV>> implements PersonalCardStore.Listener {

    private final PersonalCardStore personalCardStore;
    private ItemTouchHelper itemTouchHelper;

    public PersonalCardListAdapterBase(PersonalCardStore personalCardStore) {
        this.personalCardStore = personalCardStore;
        setupCardReordering();
    }

    private void setupCardReordering() {
        ListReorderHelper reorderHelper = new ListReorderHelper();
        reorderHelper.setRowReorderListener((fromPosition, toPosition) -> {
            if (fromPosition != toPosition) {
                E before = (toPosition == 0) ? null : get(toPosition - 1);
                personalCardStore.muteListenerForNextOperation(this);
                personalCardStore.reorderCardAfter(elementToPersonalCard(get(fromPosition)), before != null ? elementToPersonalCard(before) : null);
                onRowMoved(fromPosition, toPosition);
            }
        });

        itemTouchHelper = new ItemTouchHelper(reorderHelper);
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        itemTouchHelper.attachToRecyclerView(recyclerView);
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        itemTouchHelper.attachToRecyclerView(null);
    }

    protected abstract E personalCardToElement(PersonalCard card);
    protected abstract PersonalCard elementToPersonalCard(E element);

    @Override
    public void onCardAdded(PersonalCard card) {
        add(personalCardStore.getCardOrdinal(card), personalCardToElement(card));
    }

    @Override
    public void onCardRemoved(PersonalCard card) {
        remove(card);
    }

    @Override
    public void onCardChanged(PersonalCard card) {
        int index = personalCardStore.getCardOrdinal(card);
        if (index != -1) {
            notifyItemChanged(index);
        }
    }
}
