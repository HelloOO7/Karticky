package cz.nocard.android.ui.view;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import java.util.List;

import cz.nocard.android.data.ConfigManager;
import cz.spojenka.android.ui.helpers.ArrayListAdapter;

public class UniversalCardListAdapter extends ArrayListAdapter<String, ProviderCardViewHolder<ProviderCardView.WithFavouriteAction>> {

    private final ConfigManager configManager;

    public UniversalCardListAdapter(ConfigManager configManager) {
        this.configManager = configManager;
    }

    protected List<String> getFavouriteProviders() {
        return null;
    }

    protected void onProviderFavouriteChange(String provider, boolean favourited) {

    }

    protected void onProviderClicked(String provider) {

    }

    @NonNull
    @Override
    public ProviderCardViewHolder<ProviderCardView.WithFavouriteAction> onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ProviderCardViewHolder<>(parent.getContext(), ProviderCardView.WithFavouriteAction::new);
    }

    @Override
    public void onBindViewHolder(@NonNull ProviderCardViewHolder<ProviderCardView.WithFavouriteAction> holder, int position) {
        String provider = get(position);
        List<String> favouriteProviders = getFavouriteProviders();

        ProviderCardView.WithFavouriteAction providerCard = holder.requirePCV();
        providerCard.setProvider(provider, configManager.getProviderInfo(provider));

        if (getFavouriteProviders() == null) {
            providerCard.setActionButtonVisibility(View.GONE);
        } else {
            providerCard.setActionButtonVisibility(View.VISIBLE);
            providerCard.setFavourited(favouriteProviders.contains(provider));
            providerCard.setOnFavouriteChangeListener(favourited -> onProviderFavouriteChange(provider, favourited));
        }

        providerCard.setOnClickListener(v -> onProviderClicked(provider));
    }
}
