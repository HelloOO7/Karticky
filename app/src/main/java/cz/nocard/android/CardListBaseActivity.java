package cz.nocard.android;

import android.animation.LayoutTransition;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import cz.nocard.android.databinding.ActivityCardListBinding;
import cz.nocard.android.databinding.MultiSelectionControlsBinding;
import cz.spojenka.android.util.ViewUtils;

public abstract class CardListBaseActivity extends AppCompatActivity {

    private static final String STATE_IN_SELECTION_MODE = "in_selection_mode";

    private ActivityCardListBinding ui;
    private MultiSelectionControlsBinding multiSelectionControls;
    private boolean inSelectionMode = false;

    private final OnBackPressedCallback exitSelectionModeOnBack = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            exitSelectionMode();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        ui = ActivityCardListBinding.inflate(getLayoutInflater());
        ui.tvTitle.setText(getTitle());
        setContentView(ui.getRoot());

        if (savedInstanceState != null) {
            inSelectionMode = savedInstanceState.getBoolean(STATE_IN_SELECTION_MODE);
        }

        if (isAddButtonEnabled()) {
            ViewUtils.useExplicitFitsSystemWindows(ui.fabAddCard);
            ui.llCards.setPadding(0, 0, 0, getResources().getDimensionPixelSize(R.dimen.fab_overlap_adjust_height));
        } else {
            ui.fabAddCard.setVisibility(View.GONE);
        }

        if (isSelectionModeEnabled()) {
            ui.btnSelectionMode.setVisibility(View.VISIBLE);
            ui.btnSelectionMode.setImageResource(getSelectionModeButtonIcon());
            ui.btnSelectionMode.setOnClickListener(v -> {
                if (inSelectionMode) {
                    List<ProviderCardView> selected = collectSelectedViews();
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
            Consumer<Boolean> omniSelectFunc = (state) -> forAllCardViews(cardView -> cardView.setUserSelected(state));
            multiSelectionControls.btnSelectAll.setOnClickListener(v -> omniSelectFunc.accept(true));
            multiSelectionControls.btnSelectNone.setOnClickListener(v -> omniSelectFunc.accept(false));
            getOnBackPressedDispatcher().addCallback(exitSelectionModeOnBack);
        }

        buildCardList();

        if (inSelectionMode) {
            enterSelectionMode();
        }
    }

    protected void overrideTitleText(CharSequence title) {
        ui.tvTitle.setText(title);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_IN_SELECTION_MODE, inSelectionMode);
    }

    protected void enterSelectionMode() {
        inSelectionMode = true;
        ui.btnExitSelectionMode.setVisibility(View.VISIBLE);
        ui.btnSelectionMode.setImageResource(R.drawable.ic_check_48px);
        exitSelectionModeOnBack.setEnabled(true);
        ui.llCards.addView(multiSelectionControls.getRoot(), 0);
        ui.fabAddCard.setVisibility(View.GONE);
        forAllCardViews(ProviderCardView::enterSelectionMode);
    }

    protected void exitSelectionMode() {
        inSelectionMode = false;
        ui.llCards.removeView(multiSelectionControls.getRoot());
        ui.btnExitSelectionMode.setVisibility(View.GONE);
        ui.btnSelectionMode.setImageResource(getSelectionModeButtonIcon());
        exitSelectionModeOnBack.setEnabled(false);
        if (isAddButtonEnabled()) {
            ui.fabAddCard.setVisibility(View.VISIBLE);
        }
        forAllCardViews(ProviderCardView::exitSelectionMode);
    }

    protected @DrawableRes int getSelectionModeButtonIcon() {
        return R.drawable.empty;
    }

    private List<ProviderCardView> collectSelectedViews() {
        List<ProviderCardView> out = new ArrayList<>();
        forAllCardViews(pcv -> {
            if (pcv.isUserSelected()) {
                out.add(pcv);
            }
        });
        return out;
    }

    private void forAllCardViews(Consumer<ProviderCardView> consumer) {
        for (int i = 0; i < ui.llCards.getChildCount(); i++) {
            if (ui.llCards.getChildAt(i) instanceof ProviderCardView pcv) {
                consumer.accept(pcv);
            }
        }
    }

    protected boolean isAddButtonEnabled() {
        return false;
    }

    protected boolean isSelectionModeEnabled() {
        return false;
    }

    protected void onSelectionDone(List<ProviderCardView> selectedViews) {

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
