package cz.spojenka.android.ui.helpers;

import android.graphics.Canvas;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Helper class for reordering items in a {@link RecyclerView}, which works seamlessly
 * with {@link RecyclerView.ViewHolder}s that implement the
 * {@link DraggableViewHolder} interface for UI callbacks and exposes a {@link RowReorderListener}
 * for the actual reordering logic.
 * <p>
 * This class is intended to be used with a {@link ItemTouchHelper} instance, which is then
 * attached to the {@link RecyclerView} using {@link ItemTouchHelper#attachToRecyclerView(RecyclerView)}.
 * <p>
 * By default, item view swipe is disabled and only vertical dragging is supported.
 * Implicit UI elevation changes are also suppressed, so that the view holders can manage
 * it on their own.
 */
public class ListReorderHelper extends ItemTouchHelper.Callback {

    private RowReorderListener listener;

    @Override
    public boolean isItemViewSwipeEnabled() {
        return false;
    }

    /**
     * Sets the listener for row reordering events.
     * It will be notified each time a row is moved from one position to another.
     *
     * @param listener The listener
     */
    public void setRowReorderListener(RowReorderListener listener) {
        this.listener = listener;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int i) {

    }

    @Override
    public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        if (listener != null) {
            listener.onRowMoved(viewHolder.getBindingAdapterPosition(), target.getBindingAdapterPosition());
        }
        return true;
    }

    @Override
    public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
        if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
            if (viewHolder instanceof DraggableViewHolder draggable) {
                draggable.onDragStart();
            }
        }

        super.onSelectedChanged(viewHolder, actionState);
    }

    @Override
    public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);

        if (viewHolder instanceof DraggableViewHolder draggable) {
            draggable.onDragClear();
        }
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
        float currentElevation = viewHolder.itemView.getElevation();
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        viewHolder.itemView.setElevation(currentElevation); // We want the children to manage elevation themselves, not onChildDraw to override it (which it normally does)
    }

    /**
     * Interface for view holders that can be dragged.
     * It is optional to implement this interface, but it is recommended to do so
     * if the view holder needs to perform any UI changes when dragging starts or stops.
     */
    public interface DraggableViewHolder {

        /**
         * Called when dragging of this view holder starts.
         */
        void onDragStart();

        /**
         * Called when dragging of this view holder is finished.
         */
        void onDragClear();
    }

    /**
     * Interface for listeners that are notified when a row is moved in the list.
     */
    public interface RowReorderListener {

        /**
         * Called when a row is moved from one position to another.
         * The positions are the same as returned by the view holders'
         * {@link RecyclerView.ViewHolder#getBindingAdapterPosition()} method.
         *
         * @param fromPosition Original position of the row
         * @param toPosition  New position of the row
         */
        void onRowMoved(int fromPosition, int toPosition);
    }
}
