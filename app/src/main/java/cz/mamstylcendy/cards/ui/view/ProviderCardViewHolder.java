package cz.mamstylcendy.cards.ui.view;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;
import java.util.function.Function;

import cz.spojenka.android.ui.helpers.ListReorderHelper;

public class ProviderCardViewHolder<PCV extends ProviderCardView> extends RecyclerView.ViewHolder implements ListReorderHelper.DraggableViewHolder {

    private final PCV pcv;
    private float baseElevation;

    @SuppressWarnings("unchecked")
    public ProviderCardViewHolder(Context context, Function<Context, ? extends PCV> ctor) {
        super(ctor.apply(context));
        pcv = (PCV) itemView;
        baseElevation = pcv.getElevation();
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

    @Override
    public void onDragStart() {
        if (pcv != null) {
            pcv.setElevation(baseElevation * 2);
        }
    }

    @Override
    public void onDragClear() {
        if (pcv != null) {
            pcv.setElevation(baseElevation);
        }
    }
}
