package cz.spojenka.android.ui.helpers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * Adapter for RecyclerView that uses ArrayList as data container.
 * The adapter may be seamlessly used as a {@link List}, as all operations
 * notify the RecyclerView about the changes appropriately. Additionally,
 * the {@link cz.spojenka.android.ui.helpers.ListReorderHelper.RowReorderListener} interface is implemented
 * so that support for item reordering by dragging is provided as plug-and-play.
 *
 * @param <T> Type of the data in the list
 * @param <V> Type of the ViewHolders used in the RecyclerView
 */
public abstract class ArrayListAdapter<T, V extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<V> implements List<T>, ListReorderHelper.RowReorderListener {

    protected final List<T> items;

    /**
     * Create the adapter with the given list of items.
     * The list is not copied to save memory, but it is forbidden to modify the list
     * any further externally. If you need to make a copy, pass a list created with {@link ArrayList#ArrayList(Collection)}
     * to this constructor.
     *
     * @param items A list of items, must be mutable.
     */
    public ArrayListAdapter(List<T> items) {
        this.items = items;
    }

    /**
     * Default constructor that initializes the adapter with an empty list.
     */
    public ArrayListAdapter() {
        this(new ArrayList<>());
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public boolean contains(@Nullable Object o) {
        return items.contains(o);
    }

    @NonNull
    @Override
    public Iterator<T> iterator() {
        return items.iterator();
    }

    @NonNull
    @Override
    public Object[] toArray() {
        return items.toArray();
    }

    @NonNull
    @Override
    public <T1> T1[] toArray(@NonNull T1[] a) {
        return items.toArray(a);
    }

    @Override
    public boolean add(T t) {
        if (items.add(t)) {
            notifyItemInserted(items.size() - 1);
            return true;
        }
        return false;
    }

    @Override
    public boolean remove(@Nullable Object o) {
        int index = items.indexOf(o);
        if (index >= 0) {
            items.remove(index);
            notifyItemRemoved(index);
            return true;
        }
        return false;
    }

    @Override
    public boolean containsAll(@NonNull Collection<?> c) {
        return new HashSet<>(items).containsAll(c);
    }

    @Override
    public boolean addAll(@NonNull Collection<? extends T> c) {
        int start = items.size();
        if (items.addAll(c)) {
            notifyItemRangeInserted(start, c.size());
            return true;
        }
        return false;
    }

    @Override
    public boolean addAll(int index, @NonNull Collection<? extends T> c) {
        if (items.addAll(index, c)) {
            notifyItemRangeInserted(index, c.size());
            return true;
        }
        return false;
    }

    @Override
    public boolean removeAll(@NonNull Collection<?> c) {
        if (items.removeAll(c)) {
            notifyDataSetChanged();
            return true;
        }
        return false;
    }

    @Override
    public boolean retainAll(@NonNull Collection<?> c) {
        if (items.retainAll(c)) {
            notifyDataSetChanged();
            return true;
        }
        return false;
    }

    @Override
    public void clear() {
        int size = items.size();
        items.clear();
        notifyItemRangeRemoved(0, size);
    }

    /**
     * Replace all items in the adapter with the given collection.
     * This is functionally equivalent to calling {@link #clear()} and then {@link #addAll(Collection)},
     * but it is more efficient as it only notifies the RecyclerView once.
     *
     * @param newItems The new items
     */
    public void replaceAll(Collection<T> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @Override
    public T get(int index) {
        return items.get(index);
    }

    @Override
    public T set(int index, T element) {
        T old = items.set(index, element);
        notifyItemChanged(index);
        return old;
    }

    /**
     * Set a range of items in the adapter to the given list of items.
     * This is functionally equivalent to calling {@link #set(int, Object)} for each item in the list,
     * but it is more efficient as it only notifies the RecyclerView once.
     *
     * @param start The index of the first item to replace
     * @param newItems The new items
     */
    public void setRange(int start, List<T> newItems) {
        for (int i = 0; i < newItems.size(); i++) {
            items.set(start + i, newItems.get(i));
        }
        notifyItemRangeChanged(start, newItems.size());
    }

    @Override
    public void add(int index, T element) {
        items.add(index, element);
        notifyItemInserted(index);
    }

    @Override
    public T remove(int index) {
        T old = items.remove(index);
        notifyItemRemoved(index);
        return old;
    }

    /**
     * Remove a range of items from the adapter.
     * This is functionally equivalent to calling {@link #remove(int)} for each item in the range,
     * but it is more efficient as it only notifies the RecyclerView once.
     *
     * @param fromIndex The index of the first item to remove
     * @param count The number of items to remove
     */
    public void removeRange(int fromIndex, int count) {
        items.subList(fromIndex, fromIndex + count).clear();
        notifyItemRangeRemoved(fromIndex, count);
    }

    @Override
    public int indexOf(@Nullable Object o) {
        return items.indexOf(o);
    }

    @Override
    public int lastIndexOf(@Nullable Object o) {
        return items.lastIndexOf(o);
    }

    @NonNull
    @Override
    public ListIterator<T> listIterator() {
        return items.listIterator();
    }

    @NonNull
    @Override
    public ListIterator<T> listIterator(int index) {
        return items.listIterator(index);
    }

    @NonNull
    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        return Collections.emptyList();
    }

    @Override
    public void onRowMoved(int fromPosition, int toPosition) {
        T item = items.remove(fromPosition);
        items.add(toPosition, item);
        notifyItemMoved(fromPosition, toPosition);
    }

    /**
     * Listener interface called when binding data items to view holders.
     * This interface is not actually used in this class, as it is up to the implementation
     * to decide when and how to attach items to view holders. However, it is provided
     * as a convenience for implementing classes to use.
     *
     * @see BasicListAdapter#addOnBindListener(OnBindListener)
     *
     * @param <T> The item data type
     * @param <V> The adapting view holder type
     */
    public interface OnBindListener<T, V> {

        void onBind(T item, V viewHolder);
    }
}
