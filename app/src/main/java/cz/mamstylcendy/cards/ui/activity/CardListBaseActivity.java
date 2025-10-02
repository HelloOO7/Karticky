package cz.mamstylcendy.cards.ui.activity;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import cz.mamstylcendy.cards.R;
import cz.mamstylcendy.cards.databinding.ActivityCardListBinding;
import cz.spojenka.android.ui.helpers.EdgeToEdgeSupport;

public abstract class CardListBaseActivity extends AppCompatActivity {

    protected ActivityCardListBinding ui;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        ui = ActivityCardListBinding.inflate(getLayoutInflater());
        ui.tvTitle.setText(getTitle());
        setContentView(ui.getRoot());

        if (isAddButtonEnabled()) {
            EdgeToEdgeSupport.useExplicitFitsSystemWindows(ui.fabAddCard);
            ui.rvCards.setPadding(
                    ui.rvCards.getPaddingLeft(), ui.rvCards.getPaddingTop(), ui.rvCards.getPaddingRight(),
                    ui.rvCards.getPaddingBottom() + getResources().getDimensionPixelSize(R.dimen.fab_overlap_adjust_height)
            );
        } else {
            ui.fabAddCard.setVisibility(View.GONE);
        }

        EdgeToEdgeSupport.useExplicitFitsSystemWindows(
                ui.clRoot,
                EdgeToEdgeSupport.SIDE_HORIZONTAL | EdgeToEdgeSupport.SIDE_TOP,
                0
        );
        EdgeToEdgeSupport.useExplicitFitsSystemWindows(
                ui.rvCards,
                EdgeToEdgeSupport.SIDE_BOTTOM,
                EdgeToEdgeSupport.FLAG_APPLY_AS_PADDING
        );

        ((SimpleItemAnimator) ui.rvCards.getItemAnimator()).setSupportsChangeAnimations(false);

        buildCardList();
    }

    protected abstract RecyclerView.Adapter<?> getAdapter();

    protected void overrideTitleText(CharSequence title) {
        ui.tvTitle.setText(title);
    }

    protected @DrawableRes int getSelectionModeButtonIcon() {
        return R.drawable.empty;
    }

    protected boolean isAddButtonEnabled() {
        return false;
    }

    protected void setAddButtonCallback(View.OnClickListener l) {
        ui.fabAddCard.setOnClickListener(l);
    }

    private void buildCardList() {
        ui.rvCards.setAdapter(getAdapter());
        populateCardList();

        getAdapter().registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                updatePlaceholder();
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                onChanged();
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                onChanged();
            }
        });

        int placeholderText = getBlankPlaceholderText();
        if (placeholderText != ResourcesCompat.ID_NULL) {
            ui.tvBlankPlaceholder.setText(placeholderText);
        } else {
            ui.tvBlankPlaceholder.setVisibility(View.GONE);
        }

        updatePlaceholder();
    }

    protected void updatePlaceholder() {
        boolean anyCards = getAdapter().getItemCount() > 0;

        if (anyCards) {
            ui.tvBlankPlaceholder.setVisibility(View.GONE);
        } else {
            ui.tvBlankPlaceholder.setVisibility(View.VISIBLE);
        }
    }

    protected abstract void populateCardList();
    protected abstract @StringRes int getBlankPlaceholderText();
}
