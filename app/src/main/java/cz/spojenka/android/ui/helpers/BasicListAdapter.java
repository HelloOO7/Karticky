package cz.spojenka.android.ui.helpers;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Basic implementation of a {@link RecyclerView.Adapter} that only uses one type of {@link RecyclerView.ViewHolder}
 * to bind one type of items. {@link BasicViewHolder#bind(Object)} will be called with the data item to bind the view holders,
 * and it is expected that {@link #onCreateViewHolder(ViewGroup, int)} will only return instances of the specified view holder type.
 *
 * @see ArrayListAdapter
 *
 * @param <T> The item data type
 * @param <V> The adapting view holder type
 */
public abstract class BasicListAdapter<T, V extends BasicListAdapter.BasicViewHolder<T>> extends ArrayListAdapter<T, V> {

    private final List<OnBindListener<T, V>> onBindListeners = new ArrayList<>();

    /**
     * Adds a listener that will be called each time a view holder is bound to data item.
     * Multiple listeners can be added. Make sure to unregister the listener when it is no longer needed
     * using {@link #removeOnBindListener(OnBindListener)}.
     *
     * @param listener The listener
     */
    public void addOnBindListener(OnBindListener<T, V> listener) {
        onBindListeners.add(listener);
    }

    /**
     * Removes a listener that was previously added using {@link #addOnBindListener(OnBindListener)}.
     *
     * @param listener The listener
     */
    public void removeOnBindListener(OnBindListener<T, V> listener) {
        onBindListeners.remove(listener);
    }

    /**
     * Called each time a view holder is bound to a data item.
     * In the base implementation, this method simply calls all registered {@link OnBindListener}s.
     *
     * @param item   The data item
     * @param holder The view holder to which the item is bound
     */
    @CallSuper
    protected void onBoundViewHolder(T item, V holder) {
        for (OnBindListener<T, V> listener : onBindListeners) {
            listener.onBind(item, holder);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull V holder, int position) {
        var item = items.get(position);
        holder.bind(item);
        onBoundViewHolder(item, holder);
        var onClick = onBindItemClickListener(item);
        if (onClick != null) {
            holder.itemView.setOnClickListener(onClick);
        }
    }

    /**
     * Called to optionally provide a {@link View.OnClickListener} for a given data item.
     *
     * @param item The data item
     * @return The click listener to be set on the item view, or null if no listener should be set
     */
    protected View.OnClickListener onBindItemClickListener(T item) {
        return null;
    }

    /**
     * {@link RecyclerView.ViewHolder} implementation used in all
     * {@link BasicListAdapter}s. The {@link #bind(Object)} method should be overridden to set up
     * the views accordingly for a data item.
     *
     * @param <T> The data item type
     */
    public static abstract class BasicViewHolder<T> extends RecyclerView.ViewHolder {

        /**
         * Create a new view holder.
         *
         * @param view The root item view.
         */
        public BasicViewHolder(View view) {
            super(view);
        }

        /**
         * Bind a data item from the adapter to the view holder.
         *
         * @param item The data item
         */
        public abstract void bind(T item);
    }
}
