package cz.nocard.android;

import android.animation.LayoutTransition;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import java.util.Comparator;
import java.util.function.Consumer;

import cz.nocard.android.databinding.ActivityCardListBinding;
import cz.spojenka.android.util.ViewUtils;

public abstract class CardListBaseActivity extends AppCompatActivity {

    private ActivityCardListBinding ui;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        ui = ActivityCardListBinding.inflate(getLayoutInflater());
        ui.tvTitle.setText(getTitle());
        setContentView(ui.getRoot());

        if (isAddButtonEnabled()) {
            ViewUtils.useExplicitFitsSystemWindows(ui.fabAddCard);
            ui.llCards.setPadding(0, 0, 0, getResources().getDimensionPixelSize(R.dimen.fab_overlap_adjust_height));
        } else {
            ui.fabAddCard.setVisibility(View.GONE);
        }

        buildCardList();
    }

    protected boolean isAddButtonEnabled() {
        return false;
    }

    protected void setAddButtonCallback(View.OnClickListener l) {
        ui.fabAddCard.setOnClickListener(l);
    }

    protected void removeCardView(ProviderCardView view) {
        ui.llCards.removeView(view);
        updatePlaceholder();
    }

    protected void insertCardView(ProviderCardView view, Comparator<ProviderCardView> positionDecisionMaker) {
        int insertIndex = ui.llCards.getChildCount();
        for (int i = 0; i < ui.llCards.getChildCount(); i++) {
            ProviderCardView existingView = (ProviderCardView) ui.llCards.getChildAt(i);
            if (positionDecisionMaker.compare(existingView, view) > 0) {
                insertIndex = i;
                break;
            }
        }
        ui.llCards.addView(view, insertIndex);
        updatePlaceholder();
    }

    private void buildCardList() {
        populateCardList(providerCardView -> ui.llCards.addView(providerCardView));

        int placeholderText = getBlankPlaceholderText();
        if (placeholderText != ResourcesCompat.ID_NULL) {
            ui.tvBlankPlaceholder.setText(placeholderText);
        } else {
            ui.tvBlankPlaceholder.setVisibility(View.GONE);
        }

        updatePlaceholder();
    }

    private void updatePlaceholder() {
        boolean anyCards = ui.llCards.getChildCount() > 0;

        if (anyCards) {
            if (ui.llCards.getLayoutTransition() == null) {
                ui.llCards.setLayoutTransition(new LayoutTransition());
            }
            ui.tvBlankPlaceholder.setVisibility(View.GONE);
        } else {
            ui.tvBlankPlaceholder.setVisibility(View.VISIBLE);
        }
    }

    protected abstract void populateCardList(Consumer<ProviderCardView> callMeMaybe);
    protected abstract @StringRes int getBlankPlaceholderText();
}
