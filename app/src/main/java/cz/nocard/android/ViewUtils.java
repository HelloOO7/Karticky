package cz.nocard.android;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.DimenRes;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Various utilities and workarounds for working with Views.
 */
public class ViewUtils {

    /**
     * Convert a dimension in dp to pixels.
     *
     * @param view View context
     * @param dp Amount in dp
     * @return Amount in pixels
     */
    public static int dpToPx(View view, double dp) {
        return (int) Math.round(view.getResources().getDisplayMetrics().density * dp);
    }

    /**
     * Convert a dimension in dp to pixels.
     *
     * @param resources Resources
     * @param dp Amount in dp
     * @return Amount in pixels
     */
    public static int dpToPx(Resources resources, double dp) {
        return (int) Math.round(resources.getDisplayMetrics().density * dp);
    }

    /**
     * Convert a dimension in dp to pixels.
     *
     * @param context Context
     * @param dp Amount in dp
     * @return Amount in pixels
     */
    public static int dpToPx(Context context, double dp) {
        return (int) Math.round(context.getResources().getDisplayMetrics().density * dp);
    }

    /**
     * Set the padding of all sides of a View.
     *
     * @param view The View
     * @param padding Padding in dp.
     */
    public static void setPaddingDp(View view, int padding) {
        setPaddingDp(view, padding, padding);
    }

    /**
     * Set the padding for a View separately for horizontal and vertical dimensions.
     *
     * @param view The View
     * @param horizontal Horizontal padding in dp.
     * @param vertical Vertical padding in dp.
     */
    public static void setPaddingDp(View view, int horizontal, int vertical) {
        setPaddingDp(view, horizontal, vertical, horizontal, vertical);
    }

    /**
     * Set the padding for a View separately for each side.
     *
     * @param view The View
     * @param l Left padding in dp.
     * @param t Top padding in dp.
     * @param r Right padding in dp.
     * @param b Bottom padding in dp.
     */
    public static void setPaddingDp(View view, int l, int t, int r, int b) {
        view.setPadding(dpToPx(view, l), dpToPx(view, t), dpToPx(view, r), dpToPx(view, b));
    }

    /**
     * Set the layout margin of all sides of a View.
     *
     * @param view The View
     * @param lp Layout parameters of the View which the margin is to be applied to
     * @param margin Margin in dp.
     */
    public static void setMarginDp(View view, ViewGroup.MarginLayoutParams lp, int margin) {
        setMarginDp(view, lp, margin, margin);
    }

    /**
     * Set the layout margin for a View separately for horizontal and vertical dimensions.
     *
     * @param view The View
     * @param lp Layout parameters of the View which the margin is to be applied to
     * @param horizontal Horizontal margin in dp.
     * @param vertical Vertical margin in dp.
     */
    public static void setMarginDp(View view, ViewGroup.MarginLayoutParams lp, int horizontal, int vertical) {
        setMarginDp(view, lp, horizontal, vertical, horizontal, vertical);
    }

    /**
     * Set the layout margin for a View separately for each side.
     *
     * @param view The View
     * @param lp Layout parameters of the View which the margin is to be applied to
     * @param l Left margin in dp.
     * @param t Top margin in dp.
     * @param r Right margin in dp.
     * @param b Bottom margin in dp.
     */
    public static void setMarginDp(View view, ViewGroup.MarginLayoutParams lp, int l, int t, int r, int b) {
        lp.setMargins(dpToPx(view, l), dpToPx(view, t), dpToPx(view, r), dpToPx(view, b));
    }

    /**
     * Moves the value of an EditText to its hint and clears the text.
     *
     * @param editText The EditText
     */
    public static void textToHint(EditText editText) {
        editText.setHint(editText.getText());
        editText.setText("");
    }

    /**
     * Set the text size of a TextView from a dimension resource.
     *
     * @param textView The TextView
     * @param dimen ID of the dimension resource
     */
    public static void setTextSize(TextView textView, @DimenRes int dimen) {
        setTextPixelSize(textView, textView.getResources().getDimensionPixelSize(dimen));
    }

    /**
     * Set the text size of a TextView in pixels.
     *
     * @param textView The TextView
     * @param pixels Size in pixels
     */
    public static void setTextPixelSize(TextView textView, float pixels) {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, pixels);
    }

    /**
     * Get the Android ripple effect drawable applicable to a view.
     *
     * @param view View context
     * @return A ripple drawable, never null
     */
    public static Drawable getRippleDrawable(View view) {
        TypedValue value = new TypedValue();
        view.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return ResourcesCompat.getDrawable(view.getResources(), value.resourceId, view.getContext().getTheme());
    }

    /**
     * Enables the ripple effect for a View's foreground layer.
     *
     * @param view The View
     */
    public static void enableRipple(View view) {
        view.setForeground(getRippleDrawable(view));
    }

    /**
     * Enables the ripple effect for a View's background layer.
     *
     * @param view The View
     */
    public static void enableRippleBG(View view) {
        view.setBackground(getRippleDrawable(view));
    }

    /**
     * Inverse function to {@link #enableRipple(View)}.
     * Effectively, it removes any drawable from the foreground layer.
     *
     * @param view The View
     */
    public static void disableRipple(View view) {
        view.setForeground(null);
    }

    /**
     * Inverse function to {@link #enableRippleBG(View)}.
     * Effectively, it removes any drawable from the background layer.
     *
     * @param view The View
     */
    public static void disableRippleBG(View view) {
        view.setBackground(null);
    }

    /**
     * Set the tint of an ImageView.
     *
     * @param imageView The ImageView
     * @param color A color resource ID.
     */
    public static void setTintRes(ImageView imageView, @ColorRes int color) {
        setTint(imageView, ResourcesCompat.getColor(imageView.getResources(), color, null));
    }

    /**
     * Set the tint of an ImageView.
     *
     * @param imageView The ImageView
     * @param color A color value (not resource ID).
     */
    public static void setTint(ImageView imageView, @ColorInt int color) {
        ImageViewCompat.setImageTintList(imageView, ColorStateList.valueOf(color));
    }

    /**
     * Get the integer value of an EditText.
     *
     * @param et The EditText
     * @param defaultValue Value to return if the EditText does not contain a valid integer value.
     * @return The integer value, or defaultValue.
     */
    public static int getIntText(EditText et, int defaultValue) {
        try {
            return Integer.parseInt(et.getText().toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Wraps a View in a {@link ScrollView}. This can be used as a quick method to
     * make a View scrollable.
     * <p>
     * The returned {@link ScrollView} will have {@link ScrollView#isFillViewport()} set to true
     * and its own layout parameters will be {@link ViewGroup.LayoutParams#MATCH_PARENT}
     * in both dimensions.
     *
     * @param view The View.
     * @return The ScrollView which may be used for scrolling.
     */
    public static ScrollView wrapInScrollView(View view) {
        ScrollView scrollView = new ScrollView(view.getContext());
        scrollView.setFillViewport(true);
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrollView.addView(view);
        return scrollView;
    }

    /**
     * Recursively sets the enabled/disabled state of an entire View hierarchy,
     * saving the current state into an object. The state can later be restored
     * using {@link #restoreViewEnabledState(View, ViewEnabledSaveState, boolean)}.
     *
     * The Views must have an ID in order for their state to be saved.
     *
     * @param view Root of the View hierearchy.
     * @param enabled Whether to enable or disable the views.
     * @return A {@link ViewEnabledSaveState} that can be later passed to {@link #restoreViewEnabledState(View, ViewEnabledSaveState, boolean)}.
     */
    public static ViewEnabledSaveState setViewsEnabledRecursive(View view, boolean enabled) {
        ViewEnabledSaveState state = new ViewEnabledSaveState();
        setViewsEnabledRecursive(view, enabled, state);
        return state;
    }

    /**
     * Restore the enabled/disabled state of all Views in a hierarchy previously saved with
     * {@link #setViewsEnabledRecursive(View, boolean)}.
     *
     * @param view Root of the View hierarchy.
     * @param state The previously saved state.
     * @param defaultEnabled Whether views without an entry in the saved state should be enabled or disabled by default.
     */
    public static void restoreViewEnabledState(View view, ViewEnabledSaveState state, boolean defaultEnabled) {
        if (view instanceof ViewGroup vg) {
            for (int i = 0; i < vg.getChildCount(); i++) {
                restoreViewEnabledState(vg.getChildAt(i), state, defaultEnabled);
            }
        }
        view.setEnabled(state.stateMap.getOrDefault(view.getId(), defaultEnabled));
    }

    private static void setViewsEnabledRecursive(View view, boolean enabled, ViewEnabledSaveState dest) {
        if (view instanceof ViewGroup vg) {
            for (int i = 0; i < vg.getChildCount(); i++) {
                setViewsEnabledRecursive(vg.getChildAt(i), enabled, dest);
            }
        }
        if (view.getId() != View.NO_ID) {
            dest.stateMap.put(view.getId(), view.isEnabled());
        }
        view.setEnabled(enabled);
    }

    /**
     * Attempts to recursively obtain the context of a parent {@link Activity} from a child context.
     *
     * @param context The leaf context.
     * @return Parent {@link Activity} context, or null of not attached to an activity.
     */
    public static Activity getActivityContext(Context context) {
        if (context instanceof Activity) {
            return (Activity) context;
        }
        if (context instanceof ContextWrapper) {
            return getActivityContext(((ContextWrapper) context).getBaseContext());
        }
        return null;
    }

    /**
     * For reasons unbeknownst to me, the LinkMovementMethod breaks text view layout if
     * justifying text is enabled. This method will substitute the link click handling,
     * but will unfortunately not actually provide keyboard movement support.
     * <p>
     * Source: <a href="https://stackoverflow.com/questions/68660290/justificationmode-with-linkmovementmethod-make-text-cut-off">Stackoverflow</a>
     *
     * @param textView Text view to install the workaround on
     */
    @SuppressLint("ClickableViewAccessibility")
    public static void workaroundForLinkMovementMethod(TextView textView) {
        textView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                int x = (int) (event.getX() - textView.getTotalPaddingLeft() + textView.getScrollX());
                int y = (int) (event.getY() - textView.getTotalPaddingTop() + textView.getScrollY());
                int line = textView.getLayout().getLineForVertical(y);
                int offset = textView.getLayout().getOffsetForHorizontal(line, x);
                if (textView.getText() instanceof Spanned spanned) {
                    ClickableSpan[] links = spanned.getSpans(offset, offset, ClickableSpan.class);
                    if (links.length > 0) {
                        links[0].onClick(textView);
                    }
                }
            }
            return true;
        });
    }

    /*
     */

    /**
     * Set the {@link RecyclerView.RecycledViewPool} of the {@link RecyclerView}
     * that a {@link ViewPager2} is internally implemented with.
     * <p>
     * This allows optimizing performance in places where a lot of {@link ViewPager2}s share Views,
     * but the official API does not allow it by default.
     *
     * @see RecyclerView#setRecycledViewPool(RecyclerView.RecycledViewPool)
     *
     * @param viewPager2 The {@link ViewPager2}
     * @param recycledViewPool The {@link RecyclerView.RecycledViewPool}
     */
    public static void setViewPager2RecycledViewPool(ViewPager2 viewPager2, RecyclerView.RecycledViewPool recycledViewPool) {
        for (int i = 0; i < viewPager2.getChildCount(); i++) {
            View child = viewPager2.getChildAt(i);
            if (child instanceof RecyclerView rv) {
                rv.setRecycledViewPool(recycledViewPool);
                break;
            }
        }
    }

    /**
     * Performs an operation on every ViewHolder in a {@link RecyclerView} whose class matches
     * that of an argument.
     *
     * @param recyclerView The {@link RecyclerView}
     * @param viewHolderClass Class of the ViewHolder upon which operations should be performed
     * @param func The operation to run
     * @param <VH> Type parameter of the ViewHolder type
     */
    public static <VH extends RecyclerView.ViewHolder> void forEachViewHolder(RecyclerView recyclerView, Class<VH> viewHolderClass, Consumer<VH> func) {
        viewHolderStream(recyclerView, viewHolderClass).forEach(func);
    }

    /**
     * Creates a {@link Stream} that iterates over all ViewHolders in a {@link RecyclerView}
     * that match a provided class.
     *
     * @param recyclerView The {@link RecyclerView}
     * @param viewHolderClass Class of the ViewHolders to be processed. May also be {@link RecyclerView.ViewHolder} to process regardless of the subclass.
     * @return The ViewHolder stream
     * @param <VH> Type parameter of the ViewHolder type
     */
    public static <VH extends RecyclerView.ViewHolder> Stream<VH> viewHolderStream(RecyclerView recyclerView, Class<VH> viewHolderClass) {
        return Stream.iterate(0, i -> i + 1).limit(recyclerView.getChildCount())
                .map(recyclerView::getChildAt)
                .map(recyclerView::getChildViewHolder)
                .filter(viewHolderClass::isInstance)
                .map(viewHolderClass::cast);
    }

    /**
     * Programmatically invokes the click operation of a View, additionally triggering
     * its default click animation.
     * <p>
     * The principal difference from {@link View#performClick()} is that
     * normally, the animation is actually not triggered.
     *
     * @param view The View
     */
    public static void performClickAnimated(View view) {
        view.setPressed(true);
        view.setPressed(false);
        view.performClick();
    }

    /**
     * Toggles strikethrough text style on a TextView.
     *
     * @param textView The TextView
     * @param strikethrough Whether to enable or disable strikethrough
     */
    public static void setStrikethrough(TextView textView, boolean strikethrough) {
        if (strikethrough) {
            textView.setPaintFlags(textView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            textView.setPaintFlags(textView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        }
    }

    /**
     * There is a bug in Android due to which animations that modify alpha do not take effect
     * if the View's initial alpha is zero. This method mimics {@link View#startAnimation(Animation)},
     * but additionally makes sure that the animations behave as any sane programmer would expect.
     * <p>
     * At the end of the animation, the alpha of the View will always be the same as it was at the start.
     *
     * @param view The target view
     * @param animation Animation to be played
     */
    public static void startAlphaAnimation(View view, Animation animation) {
        float initAlpha = view.getAlpha();
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                view.setAlpha(1f);
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                view.setAlpha(initAlpha);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        view.startAnimation(animation);
    }

    /**
     * Sets/clears the bold style of a TextView.
     *
     * @param textView The TextView
     * @param b Whether to add the bold style or to keep normal/italics
     */
    public static void setBold(TextView textView, boolean b) {
        Typeface typeface = textView.getTypeface();
        if (b) {
            textView.setTypeface(typeface, Typeface.BOLD);
        } else {
            textView.setTypeface(typeface, typeface.isItalic() ? Typeface.ITALIC : Typeface.NORMAL);
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    public static void setCursorDrawableColor(EditText editText, @ColorInt int color) {
        Function<Drawable, Drawable> applyColor = (drawable) -> {
            if (drawable == null) {
                return null;
            }
            var state = drawable.getConstantState();
            if (state != null) {
                drawable = state.newDrawable(editText.getResources());
            }
            drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            return drawable;
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            editText.setTextCursorDrawable(applyColor.apply(editText.getTextCursorDrawable()));
        } else {
            try {
                Field fCursorDrawableRes = TextView.class.getDeclaredField("mCursorDrawableRes");
                fCursorDrawableRes.setAccessible(true);
                Field fEditor = TextView.class.getDeclaredField("mEditor");
                fEditor.setAccessible(true);
                Object editor = fEditor.get(editText);
                Class<?> clazz = editor.getClass();
                Field fCursorDrawable = clazz.getDeclaredField("mCursorDrawable");
                fCursorDrawable.setAccessible(true);

                Drawable[] drawables = (Drawable[]) fCursorDrawable.get(editor);
                if (drawables != null) {
                    drawables[0] = applyColor.apply(drawables[0]);
                    drawables[1] = applyColor.apply(drawables[1]);
                }
            } catch (Throwable ex) {
                Log.e("ViewUtils", "Failed to set cursor color", ex);
            }
        }
    }

    public static float getTextWidth(TextView textView, String text) {
        return textView.getPaint().measureText(text);
    }

    /**
     * Before Android 11, system bar inset values are "approximate" and not guaranteed. Oftentimes, they
     * are simply zero, which makes snackbars etc. show up underneath the system bars. This method will,
     * in the case of Android 10 and below, register a listener (with {@link ViewCompat#setOnApplyWindowInsetsListener(View, OnApplyWindowInsetsListener)}
     * that will apply the system bar insets from {@link View#getRootWindowInsets()}, which works even on those older APIs.
     *
     * @param view The View to register the listener on
     */
    public static void registerCompatInsetsFixups(View view) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            ViewGroup.MarginLayoutParams baseLayoutParams = new ViewGroup.MarginLayoutParams(
                    (ViewGroup.MarginLayoutParams) view.getLayoutParams()
            );

            ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
                WindowInsetsCompat actualInsets = WindowInsetsCompat.toWindowInsetsCompat(v.getRootWindowInsets());
                Insets systemBars = actualInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();

                params.setMargins(
                        systemBars.left + baseLayoutParams.leftMargin,
                        systemBars.top + baseLayoutParams.topMargin,
                        systemBars.right + baseLayoutParams.rightMargin,
                        systemBars.bottom + baseLayoutParams.bottomMargin
                );

                v.setLayoutParams(params);

                return WindowInsetsCompat.CONSUMED;
            });
        }
    }

    public static class ViewEnabledSaveState {
        private final Map<Integer, Boolean> stateMap = new HashMap<>();
    }
}
