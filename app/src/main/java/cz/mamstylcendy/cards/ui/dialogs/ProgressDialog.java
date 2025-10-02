package cz.mamstylcendy.cards.ui.dialogs;

import android.content.Context;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDialog;
import androidx.lifecycle.Lifecycle;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import cz.mamstylcendy.cards.databinding.ProgressDialogBinding;
import cz.spojenka.android.util.AsyncUtils;

/**
 * A replacement for the deprecated {@link android.app.ProgressDialog}.
 * This is an immutable progress indicator and should only be used in cases where blocking the
 * UI is really necessary.
 */
public class ProgressDialog extends AppCompatDialog {

    private static final String ARG_TEXT = "text";

    private ProgressDialogBinding binding;
    private String text;

    /**
     * Create a new progress dialog with the given text.
     *
     * @param context Context
     * @param text    Text to display next to the progress indicator.
     */
    public ProgressDialog(@NonNull Context context, String text) {
        super(context);
        this.text = text;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            text = savedInstanceState.getString(ARG_TEXT);
        }

        binding = ProgressDialogBinding.inflate(getLayoutInflater());

        setText(text);

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(binding.getRoot(), new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setCancelable(false);
    }

    @NonNull
    @Override
    public Bundle onSaveInstanceState() {
        Bundle state = super.onSaveInstanceState();
        state.putString(ARG_TEXT, text);
        return state;
    }

    @Override
    public void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        text = savedInstanceState.getString(ARG_TEXT);
    }

    /**
     * Set the message displayed next to the progress indicator.
     *
     * @param text The message text.
     */
    public void setText(String text) {
        this.text = text;
        binding.progressText.setText(text);
    }

    /**
     * Dismiss the dialog, unless it is already destroyed.
     *
     * @return True if the dialog was dismissed, false if it was already destroyed.
     */
    public boolean dismissIfExists() {
        if (getLifecycle().getCurrentState() != Lifecycle.State.DESTROYED) {
            dismiss();
            return true;
        }
        return false;
    }

    /**
     * Run an asynchronous task along with a progress dialog.
     *
     * @param context        Context
     * @param progressTextId Text resource ID to be displayed in the progress dialog.
     * @param task           The task to run.
     * @param <R>            The return type of the task.
     * @return A {@link CompletableFuture} that will complete when the task is done.
     */
    public static <R> CompletableFuture<R> doInBackground(Context context, @StringRes int progressTextId, Callable<R> task) {
        return doInBackground(context, context.getString(progressTextId), task);
    }

    /**
     * Run an asynchronous task along with a progress dialog.
     *
     * @param context      Context
     * @param progressText Text to be displayed in the progress dialog.
     * @param task         The task to run.
     * @param <R>          The return type of the task.
     * @return A {@link CompletableFuture} that will complete when the task is done.
     */
    public static <R> CompletableFuture<R> doInBackground(Context context, String progressText, Callable<R> task) {
        return doInBackground(context, progressText, AsyncUtils.supplyAsync(task));
    }

    public static <R> CompletableFuture<R> doInBackground(Context context, @StringRes int progressTextId, CompletableFuture<R> task) {
        return doInBackground(context, context.getString(progressTextId), task);
    }

    public static <R> CompletableFuture<R> doInBackground(Context context, String progressText, CompletableFuture<R> task) {
        ProgressDialog dialog = new ProgressDialog(context, progressText);
        dialog.show();

        task.handleAsync((r, throwable) -> {
            dialog.dismissIfExists();
            return null;
        }, AsyncUtils.getLifecycleExecutor(dialog));

        return task;
    }
}
