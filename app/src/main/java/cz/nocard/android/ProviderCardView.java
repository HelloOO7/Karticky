package cz.nocard.android;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.os.BundleCompat;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.function.Consumer;

import cz.nocard.android.databinding.ProviderCardBinding;

public class ProviderCardView extends FrameLayout {

    private static final String STATE_PROVIDER_ID = "provider_id";

    protected ProviderCardBinding binding;

    private String providerId;

    private ActionButtonState abStateBeforeSelection;

    public ProviderCardView(Context context) {
        super(context);
        init();
    }

    public ProviderCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ProviderCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    protected void init() {
        binding = ProviderCardBinding.inflate(LayoutInflater.from(getContext()), this, true);
        installActionButton();
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener l) {
        binding.getRoot().setOnClickListener(l);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        binding.getRoot().setEnabled(enabled);
    }

    public void removeBottomMargin() {
        MarginLayoutParams lp = (MarginLayoutParams) binding.getRoot().getLayoutParams();
        lp.bottomMargin = 0;
        binding.getRoot().setLayoutParams(lp);
    }

    @Nullable
    @Override
    protected Parcelable onSaveInstanceState() {
        Bundle state = new Bundle();
        state.putParcelable("super", super.onSaveInstanceState());
        state.putString(STATE_PROVIDER_ID, getProviderId());
        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle bundle) {
            super.onRestoreInstanceState(BundleCompat.getParcelable(bundle, "super", Parcelable.class));
            providerId = bundle.getString(STATE_PROVIDER_ID);
        } else {
            super.onRestoreInstanceState(state);
        }
    }

    public void setProvider(String providerId, NoCardConfig.ProviderInfo providerInfo) {
        this.providerId = providerId;
        binding.tvProviderName.setText(providerInfo != null && providerInfo.providerName() != null ? providerInfo.providerName() : providerId);
        if (providerInfo != null && providerInfo.brandColor() != null) {
            setBrandColorChipTint(providerInfo.brandColor());
        } else {
            binding.ivBrandColor.setVisibility(GONE);
        }
    }

    public void setBrandColorChipTint(int tint) {
        binding.ivBrandColor.setImageTintList(ColorStateList.valueOf(tint));
        binding.ivBrandColor.setVisibility(VISIBLE);
    }

    public void setCustomChipGradient(int[] colors) {
        Drawable drawable = binding.ivBrandColor.getDrawable();
        if (drawable instanceof GradientDrawable) {
            drawable = drawable.mutate();
            GradientDrawable gradient = (GradientDrawable) drawable;
            gradient.setColors(colors);
            binding.ivBrandColor.setImageDrawable(gradient);
            binding.ivBrandColor.setVisibility(VISIBLE);
        }
    }

    public String getProviderId() {
        return providerId;
    }

    public void overridePrimaryText(CharSequence text) {
        binding.tvProviderName.setText(text);
    }

    public void setIcon(@DrawableRes int resId) {
        binding.ivCardIcon.setImageResource(resId);
        binding.ivCardIcon.setVisibility(VISIBLE);
    }

    protected void installActionButton() {

    }

    public void enterSelectionMode() {
        MaterialCheckBox defaultCheckbox = new MaterialCheckBox(getContext());
        abStateBeforeSelection = saveActionButtonState();
        binding.btnActionButton.setButtonDrawable(defaultCheckbox.getButtonDrawable());
        binding.btnActionButton.setButtonIconDrawable(defaultCheckbox.getButtonIconDrawable());
        binding.btnActionButton.setVisibility(VISIBLE);
        binding.btnActionButton.setOnClickListener(null);
        binding.btnActionButton.setChecked(false);
    }

    public void exitSelectionMode() {
        binding.btnActionButton.setButtonIconDrawableResource(R.drawable.empty);
        restoreActionButtonState(abStateBeforeSelection);
    }

    public boolean isUserSelected() {
        return binding.btnActionButton.isChecked();
    }

    public void setUserSelected(boolean selected) {
        binding.btnActionButton.setChecked(selected);
    }

    public void setActionButtonEnabled(boolean enabled) {
        binding.btnActionButton.setEnabled(enabled);
    }

    private ActionButtonState saveActionButtonState() {
        MaterialCheckBox ab = binding.btnActionButton;
        return new ActionButtonState(ab.isChecked(), ab.getVisibility());
    }

    private void restoreActionButtonState(ActionButtonState state) {
        binding.btnActionButton.setChecked(state.checked());
        binding.btnActionButton.setVisibility(state.visibility());
        installActionButton();
    }

    public static class WithFavouriteAction extends ProviderCardView {

        private OnClickListener favouriteButtonListener;

        public WithFavouriteAction(Context context) {
            super(context);
        }

        public WithFavouriteAction(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public WithFavouriteAction(Context context, AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
        }

        @Override
        protected void installActionButton() {
            binding.btnActionButton.setButtonDrawable(R.drawable.ic_favourite);
            binding.btnActionButton.setOnClickListener(favouriteButtonListener);
        }

        public void setOnFavouriteChangeListener(Consumer<Boolean> func) {
            binding.btnActionButton.setOnClickListener(favouriteButtonListener = v -> func.accept(binding.btnActionButton.isChecked()));
        }

        public void setFavourited(boolean favourited) {
            binding.btnActionButton.setChecked(favourited);
        }
    }

    public static class WithRemoveAction extends ProviderCardView {

        private OnClickListener removeButtonListener;

        public WithRemoveAction(Context context) {
            super(context);
        }

        public WithRemoveAction(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public WithRemoveAction(Context context, AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
        }

        @Override
        protected void installActionButton() {
            binding.btnActionButton.setButtonDrawable(R.drawable.ic_close_small_40px);
            binding.btnActionButton.setOnClickListener(removeButtonListener);
        }

        public void setOnRemoveListener(Runnable listener) {
            binding.btnActionButton.setOnClickListener(removeButtonListener = v -> listener.run());
        }
    }

    public static class WithContextMenu extends ProviderCardView {

        private Consumer<PopupMenu> popupMenuHandler;

        public WithContextMenu(Context context) {
            super(context);
        }

        public WithContextMenu(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public WithContextMenu(Context context, AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
        }

        @Override
        protected void installActionButton() {
            binding.btnActionButton.setButtonDrawable(R.drawable.ic_more_vert_40px);
            binding.btnActionButton.setOnClickListener(v -> {
                if (popupMenuHandler == null) {
                    return;
                }
                PopupMenu popupMenu = new PopupMenu(getContext(), binding.btnActionButton);
                popupMenu.setGravity(Gravity.END);
                popupMenuHandler.accept(popupMenu);
                popupMenu.show();
            });
        }

        public void setPopupMenuHandler(Consumer<PopupMenu> popupMenuHandler) {
            this.popupMenuHandler = popupMenuHandler;
        }
    }

    public static class WithoutAction extends ProviderCardView {

        public WithoutAction(Context context) {
            super(context);
        }

        public WithoutAction(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public WithoutAction(Context context, AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
        }

        @Override
        protected void installActionButton() {
            binding.btnActionButton.setVisibility(GONE);
        }
    }

    private static record ActionButtonState(boolean checked, int visibility) {

    }
}
