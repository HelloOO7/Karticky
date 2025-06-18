package cz.nocard.android;

import android.animation.LayoutTransition;
import android.content.res.ColorStateList;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.inject.Inject;

import cz.nocard.android.databinding.ActivityBlacklistBinding;
import cz.nocard.android.databinding.ProviderCardBinding;

public class ManageBlacklistActivity extends AppCompatActivity {

    @Inject
    NoCardPreferences prefs;
    @Inject
    ConfigManager config;

    private ActivityBlacklistBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityBlacklistBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        NoCardApplication.getInstance().getApplicationComponent().inject(this);

        List<BlacklistItem> items = new ArrayList<>();

        for (String provider : config.getAllProviders()) {
            prefs.getCardBlacklist(provider)
                    .stream()
                    .map(cardNumber -> new BlacklistItem(provider, cardNumber))
                    .sorted(Comparator.comparing(BlacklistItem::cardNumber))
                    .forEach(items::add);
        }

        binding.llBlacklistItems.setLayoutTransition(new LayoutTransition());

        for (BlacklistItem item : items) {
            ProviderCardBinding cardBinding = ProviderCardBinding.inflate(getLayoutInflater(), binding.llBlacklistItems, false);
            NoCardConfig.ProviderInfo pi = config.getProviderInfo(item.provider());
            cardBinding.tvProviderName.setText(getCardInfoText(item));
            if (pi.brandColor() != null) {
                cardBinding.ivBrandColor.setImageTintList(ColorStateList.valueOf(pi.brandColor()));
            }
            cardBinding.btnToggleFavourite.setButtonDrawable(R.drawable.ic_close_24px);
            cardBinding.btnToggleFavourite.setOnClickListener(v -> {
                prefs.removeCardFromBlacklist(item.provider(), item.cardNumber());
                binding.llBlacklistItems.removeView(cardBinding.getRoot());
            });
            binding.llBlacklistItems.addView(cardBinding.getRoot());
        }
    }

    private String getCardInfoText(BlacklistItem item) {
        return config.getProviderInfo(item.provider()).providerName() + "\n" + item.cardNumber();
    }

    private static record BlacklistItem(String provider, String cardNumber) {

    }
}
