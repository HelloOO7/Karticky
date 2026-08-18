package cz.spojenka.android.ui.helpers;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.function.Supplier;

public class SingleViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final Supplier<View> viewCreator;
    private int currentVisibility = View.VISIBLE;
    private View currentView;

    public SingleViewAdapter(Supplier<View> viewCreator) {
        this.viewCreator = viewCreator;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = viewCreator.get();
        this.currentView = view;
        if (view.getLayoutParams() == null) {
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        view.setVisibility(currentVisibility);
        return new RecyclerView.ViewHolder(view) {
        };
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {

    }

    public void setVisibility(int visibility) {
        int oldVisibility = currentVisibility;
        currentVisibility = visibility;
        if (visibility == View.GONE && oldVisibility != View.GONE) {
            notifyItemRemoved(0);
        } else if (visibility != View.GONE && oldVisibility == View.GONE) {
            notifyItemInserted(0);
        }
        if (currentView != null) {
            currentView.setVisibility(visibility);
        }
    }

    @Override
    public int getItemCount() {
        return currentVisibility != View.GONE ? 1 : 0;
    }
}
