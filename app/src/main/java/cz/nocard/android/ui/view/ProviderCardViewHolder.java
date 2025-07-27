package cz.nocard.android.ui.view;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;
import java.util.function.Function;

public class ProviderCardViewHolder<PCV extends ProviderCardView> extends RecyclerView.ViewHolder {

    private final PCV pcv;

    @SuppressWarnings("unchecked")
    public ProviderCardViewHolder(Context context, Function<Context, ? extends PCV> ctor) {
        super(ctor.apply(context));
        pcv = (PCV) itemView;
    }

    private ProviderCardViewHolder(View extraView) {
        super(extraView);
        pcv = null;
    }

    public static <PCV extends ProviderCardView> ProviderCardViewHolder<PCV> createExtraViewHolder(View extraView) {
        return new ProviderCardViewHolder<>(extraView);
    }

    public boolean hasPCV() {
        return pcv != null;
    }

    public PCV requirePCV() {
        return Objects.requireNonNull(pcv);
    }
}
