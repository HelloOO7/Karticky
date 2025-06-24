package cz.nocard.android;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import java.util.function.Consumer;

import cz.nocard.android.databinding.ProviderCardBinding;

public class ProviderCardView extends FrameLayout {

    private static final String STATE_PROVIDER_ID = "provider_id";

    protected ProviderCardBinding binding;

    private String providerId;

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
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener l) {
        binding.getRoot().setOnClickListener(l);
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
            super.onRestoreInstanceState(bundle.getParcelable("super"));
            providerId = bundle.getString(STATE_PROVIDER_ID);
        } else {
            super.onRestoreInstanceState(state);
        }
    }

    public void setProvider(String providerId, NoCardConfig.ProviderInfo providerInfo) {
        this.providerId = providerId;
        binding.tvProviderName.setText(providerInfo != null && providerInfo.providerName() != null ? providerInfo.providerName() : providerId);
        if (providerInfo != null && providerInfo.brandColor() != null) {
            binding.ivBrandColor.setImageTintList(ColorStateList.valueOf(providerInfo.brandColor()));
        } else {
            binding.ivBrandColor.setVisibility(View.GONE);
        }
    }

    public String getProviderId() {
        return providerId;
    }

    public void overridePrimaryText(String text) {
        binding.tvProviderName.setText(text);
    }

    public static class WithFavouriteAction extends ProviderCardView {

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
        protected void init() {
            super.init();
            binding.btnActionButton.setButtonDrawable(R.drawable.ic_favourite);
        }

        public void setOnFavouriteChangeListener(Consumer<Boolean> func) {
            binding.btnActionButton.setOnClickListener(v -> func.accept(binding.btnActionButton.isChecked()));
        }

        public void setFavourited(boolean favourited) {
            binding.btnActionButton.setChecked(favourited);
        }
    }

    public static class WithRemoveAction extends ProviderCardView {

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
        protected void init() {
            super.init();
            binding.btnActionButton.setButtonDrawable(R.drawable.ic_close_24px);
        }

        public void setOnRemoveListener(Runnable listener) {
            binding.btnActionButton.setOnClickListener(v -> listener.run());
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
        protected void init() {
            super.init();
            binding.btnActionButton.setVisibility(GONE);
        }
    }
}
