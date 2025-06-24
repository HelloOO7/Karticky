package cz.spojenka.android.system;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

/**
 * Utility for scheduling actions on a View that prevents overlapping execution.
 * Only the last submitted action will always be executed, regardless of delay.
 */
public class ViewActionMutex {

    private final View view;
    private Runnable current;

    /**
     * Create a mutex for a view. The actions will not be automatically cancelled
     * when the view is destroyed - it is needed to call {@link #cancel()} manually.
     *
     * @param view The view
     */
    public ViewActionMutex(View view) {
        this.view = view;
    }

    /**
     * Create a mutex for a view. The actions will be automatically cancelled
     * when a lifecycle is destroyed.
     *
     * @param lifecycleOwner The lifecycle owner
     * @param view The view
     */
    public ViewActionMutex(LifecycleOwner lifecycleOwner, View view) {
        this(view);
        lifecycleOwner.getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onDestroy(@NonNull LifecycleOwner owner) {
                cancel();
            }
        });
    }

    /**
     * Cancel the last scheduled action.
     */
    public void cancel() {
        if (current != null) {
            view.removeCallbacks(current);
            current = null;
        }
    }

    private Runnable setNewTask(Runnable callback) {
        cancel();
        Runnable wrapper = () -> {
            current = null;
            callback.run();
        };
        current = wrapper;
        return wrapper;
    }

    /**
     * Post a new action to be executed in the next event loop.
     * All pending actions will be cancelled.
     *
     * @param callback The action
     */
    public void post(Runnable callback) {
        view.post(setNewTask(callback));
    }

    /**
     * Run an action immediately, cancelling the previous one.
     * Unlike {@link #post(Runnable)}, you may call other methods of this class after
     * the method finishes without the effect of cancelling the action you provided.
     *
     * @param callback The action
     */
    public void runNow(Runnable callback) {
        cancel();
        callback.run();
    }

    /**
     * Post a new action to be executed after a delay.
     * All pending actions will be cancelled.
     *
     * @param callback The action
     * @param delay The delay in milliseconds
     */
    public void postDelayed(Runnable callback, long delay) {
        view.postDelayed(setNewTask(callback), delay);
    }
}
