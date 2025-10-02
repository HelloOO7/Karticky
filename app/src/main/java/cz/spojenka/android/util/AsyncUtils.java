package cz.spojenka.android.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import androidx.activity.ComponentActivity;
import androidx.activity.ComponentDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewTreeLifecycleOwner;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

import cz.mamstylcendy.cards.CardsApplication;

/**
 * Utility class for running tasks asynchronously using various executors.
 */
public class AsyncUtils {

    private static final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    /**
     * Run a task asynchronously. If an unhandled exception occurs, it is logged and discarded.
     *
     * @param task The task to run
     */
    public static void run(Runnable task) {
        CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } catch (Throwable th) {
                Log.e("AsyncUtils", "Error in async task", th);
            }
        });
    }

    /**
     * Run a task on the main application thread.
     *
     * @param task The task to run
     */
    public static void runOnMainThread(Runnable task) {
        mainThreadHandler.post(task);
    }

    /**
     * Run a task on the main application thread after a delay.
     *
     * @param task  The task to run
     * @param delay The delay in milliseconds
     */
    public static void runDelayedOnMainThread(Runnable task, long delay) {
        mainThreadHandler.postDelayed(task, delay);
    }

    /**
     * Get the executor for the main application thread.
     *
     * @return The executor
     * @see ContextCompat#getMainExecutor(Context)
     * @see Context#getMainExecutor()
     */
    public static Executor getMainThreadExecutor() {
        return ContextCompat.getMainExecutor(CardsApplication.getInstance());
    }

    /**
     * Create an executor that only processes tasks as long as a lifecycle is not the
     * {@link Lifecycle.State#DESTROYED} state.
     *
     * @param context        Context
     * @param lifecycleOwner Lifecycle owner
     * @return The executor
     */
    public static Executor getLifecycleExecutor(Context context, LifecycleOwner lifecycleOwner) {
        Executor base = ContextCompat.getMainExecutor(context);
        return new Executor() {
            @Override
            public void execute(Runnable command) {
                if (lifecycleOwner.getLifecycle().getCurrentState() != Lifecycle.State.DESTROYED) {
                    base.execute(command);
                }
            }
        };
    }

    /**
     * Create an executor that only runs during the lifecycle of a Fragment.
     *
     * @param fragment The fragment
     * @return The executor
     * @see #getLifecycleExecutor(Context, LifecycleOwner)
     */
    public static Executor getLifecycleExecutor(Fragment fragment) {
        return getLifecycleExecutor(fragment.requireContext(), fragment.getViewLifecycleOwner());
    }

    /**
     * Create an executor that only runs during the lifecycle of an Activity.
     *
     * @param activity The activity
     * @return The executor
     * @see #getLifecycleExecutor(Context, LifecycleOwner)
     */
    public static Executor getLifecycleExecutor(ComponentActivity activity) {
        return getLifecycleExecutor(activity, activity);
    }

    /**
     * Create an executor that only runs during the lifecycle of a Dialog.
     *
     * @param dialog The dialog
     * @return The executor
     * @see #getLifecycleExecutor(Context, LifecycleOwner)
     */
    public static Executor getLifecycleExecutor(ComponentDialog dialog) {
        return getLifecycleExecutor(dialog.getContext(), dialog);
    }

    /**
     * Create an executor that only runs during the lifecycle of a View tree.
     * {@link ViewTreeLifecycleOwner#get(View)} is used to get the lifecycle owner.
     *
     * @param view The view
     * @return The executor
     * @see #getLifecycleExecutor(Context, LifecycleOwner)
     */
    public static Executor getLifecycleExecutor(View view) {
        return getLifecycleExecutor(view.getContext(), ViewTreeLifecycleOwner.get(view));
    }

    public static <T> Function<Throwable, ? extends T> forwardError(CompletableFuture<?> dest) {
        return throwable -> {
            dest.completeExceptionally(throwable);
            return null;
        };
    }

    public static CompletableFuture<Void> runAsync(ThrowingRunnable task) {
        return supplyAsync(() -> {
            task.run();
            return null;
        });
    }

    public static <T> CompletableFuture<T> supplyAsync(Callable<T> c) {
        CompletableFuture<T> f = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            try {
                f.complete(c.call());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f;
    }

    public static interface ThrowingRunnable {

        void run() throws Exception;
    }
}
