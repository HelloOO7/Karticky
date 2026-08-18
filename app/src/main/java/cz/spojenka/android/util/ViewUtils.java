package cz.spojenka.android.util;

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
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.DimenRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.badge.BadgeUtils;
import com.google.android.material.badge.ExperimentalBadgeUtils;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Various utilities and workarounds for working with Views.
 */
public class ViewUtils {

    /**
     * Convert a dimension in dp to pixels.
     *
     * @param view View context
     * @param dp   Amount in dp
     * @return Amount in pixels
     */
    public static int dpToPx(View view, double dp) {
        return (int) Math.round(view.getResources().getDisplayMetrics().density * dp);
    }

    /**
     * Convert a dimension in dp to pixels.
     *
     * @param resources Resources
     * @param dp        Amount in dp
     * @return Amount in pixels
     */
    public static int dpToPx(Resources resources, double dp) {
        return (int) Math.round(resources.getDisplayMetrics().density * dp);
    }

    /**
     * Convert a dimension in dp to pixels.
     *
     * @param context Context
     * @param dp      Amount in dp
     * @return Amount in pixels
     */
    public static int dpToPx(Context context, double dp) {
        return (int) Math.round(context.getResources().getDisplayMetrics().density * dp);
    }

    /**
     * Set the padding of all sides of a View.
     *
     * @param view    The View
     * @param padding Padding in dp.
     */
    public static void setPaddingDp(View view, int padding) {
        setPaddingDp(view, padding, padding);
    }

    /**
     * Set the padding for a View separately for horizontal and vertical dimensions.
     *
     * @param view       The View
     * @param horizontal Horizontal padding in dp.
     * @param vertical   Vertical padding in dp.
     */
    public static void setPaddingDp(View view, int horizontal, int vertical) {
        setPaddingDp(view, horizontal, vertical, horizontal, vertical);
    }

    /**
     * Set the padding for a View separately for each side.
     *
     * @param view The View
     * @param l    Left padding in dp.
     * @param t    Top padding in dp.
     * @param r    Right padding in dp.
     * @param b    Bottom padding in dp.
     */
    public static void setPaddingDp(View view, int l, int t, int r, int b) {
        view.setPadding(dpToPx(view, l), dpToPx(view, t), dpToPx(view, r), dpToPx(view, b));
    }

    /**
     * Set the layout margin of all sides of a View.
     *
     * @param view   The View
     * @param lp     Layout parameters of the View which the margin is to be applied to
     * @param margin Margin in dp.
     */
    public static void setMarginDp(View view, ViewGroup.MarginLayoutParams lp, int margin) {
        setMarginDp(view, lp, margin, margin);
    }

    /**
     * Set the layout margin for a View separately for horizontal and vertical dimensions.
     *
     * @param view       The View
     * @param lp         Layout parameters of the View which the margin is to be applied to
     * @param horizontal Horizontal margin in dp.
     * @param vertical   Vertical margin in dp.
     */
    public static void setMarginDp(View view, ViewGroup.MarginLayoutParams lp, int horizontal, int vertical) {
        setMarginDp(view, lp, horizontal, vertical, horizontal, vertical);
    }

    /**
     * Set the layout margin for a View separately for each side.
     *
     * @param view The View
     * @param lp   Layout parameters of the View which the margin is to be applied to
     * @param l    Left margin in dp.
     * @param t    Top margin in dp.
     * @param r    Right margin in dp.
     * @param b    Bottom margin in dp.
     */
    public static void setMarginDp(View view, ViewGroup.MarginLayoutParams lp, int l, int t, int r, int b) {
        setMargin(lp, dpToPx(view, l), dpToPx(view, t), dpToPx(view, r), dpToPx(view, b));
    }

    /**
     * Set the layout margin for a View without respect to layout direction, making
     * sure that start/end margins do not override left/right margins.
     *
     * @param lp Layout parameters of the View which the margin is to be applied to
     * @param l  Left margin in px.
     * @param t  Top margin in px.
     * @param r  Right margin in px.
     * @param b  Bottom margin in px.
     */
    public static void setMargin(ViewGroup.MarginLayoutParams lp, int l, int t, int r, int b) {
        if (lp.getLayoutDirection() == View.LAYOUT_DIRECTION_LTR) {
            lp.setMarginStart(l);
            lp.setMarginEnd(r);
        } else {
            lp.setMarginStart(r);
            lp.setMarginEnd(l);
        }
        lp.topMargin = t;
        lp.bottomMargin = b;
    }

    /**
     * Set the layout margin for a View respecting layout direction.
     *
     * @param lp     Layout parameters of the View which the margin is to be applied to
     * @param start  Start margin in px.
     * @param top    Top margin in px.
     * @param end    End margin in px.
     * @param bottom Bottom margin in px.
     */
    public static void setMarginRelative(ViewGroup.MarginLayoutParams lp, int start, int top, int end, int bottom) {
        lp.setMarginStart(start);
        lp.setMarginEnd(end);
        lp.topMargin = top;
        lp.bottomMargin = bottom;
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
     * @param dimen    ID of the dimension resource
     */
    public static void setTextSize(TextView textView, @DimenRes int dimen) {
        setTextPixelSize(textView, textView.getResources().getDimension(dimen));
    }

    /**
     * Set the text size of a TextView in pixels.
     *
     * @param textView The TextView
     * @param pixels   Size in pixels
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
     * @param color     A color resource ID.
     */
    public static void setTintRes(ImageView imageView, @ColorRes int color) {
        setTint(imageView, ResourcesCompat.getColor(imageView.getResources(), color, null));
    }

    /**
     * Set the tint of an ImageView.
     *
     * @param imageView The ImageView
     * @param color     A color value (not resource ID).
     */
    public static void setTint(ImageView imageView, @ColorInt int color) {
        ImageViewCompat.setImageTintList(imageView, ColorStateList.valueOf(color));
    }

    /**
     * Get the integer value of an EditText.
     *
     * @param et           The EditText
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
     * <p>
     * The Views must have an ID in order for their state to be saved.
     *
     * @param view    Root of the View hierearchy.
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
     * @param view           Root of the View hierarchy.
     * @param state          The previously saved state.
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

    /**
     * Runs an action on the backing {@link RecyclerView} of a {@link ViewPager2}. The recycler view
     * will be obtained by searching the children of the pager.
     *
     * @param viewPager2 The ViewPager2
     * @param callback   The action to run on the RecyclerView
     */
    public static void runForViewPager2RecyclerView(ViewPager2 viewPager2, Consumer<RecyclerView> callback) {
        for (int i = 0; i < viewPager2.getChildCount(); i++) {
            View child = viewPager2.getChildAt(i);
            if (child instanceof RecyclerView rv) {
                callback.accept(rv);
                break;
            }
        }
    }

    /**
     * Set the {@link RecyclerView.RecycledViewPool} of the {@link RecyclerView}
     * that a {@link ViewPager2} is internally implemented with.
     * <p>
     * This allows optimizing performance in places where a lot of {@link ViewPager2}s share Views,
     * but the official API does not allow it by default.
     *
     * @param viewPager2       The {@link ViewPager2}
     * @param recycledViewPool The {@link RecyclerView.RecycledViewPool}
     * @see RecyclerView#setRecycledViewPool(RecyclerView.RecycledViewPool)
     */
    public static void setViewPager2RecycledViewPool(ViewPager2 viewPager2, RecyclerView.RecycledViewPool recycledViewPool) {
        runForViewPager2RecyclerView(viewPager2, rv -> rv.setRecycledViewPool(recycledViewPool));
    }

    /**
     * Sets the overscroll mode of the {@link RecyclerView} that a {@link ViewPager2} is internally
     * implemented with.
     *
     * @param viewPager2 The {@link ViewPager2}
     * @param mode       The overscroll mode, one of {@link View#OVER_SCROLL_ALWAYS},
     *                   {@link View#OVER_SCROLL_IF_CONTENT_SCROLLS}, or {@link View#OVER_SCROLL_NEVER}.
     * @see View#setOverScrollMode(int)
     */
    public static void setViewPager2OverscrollMode(ViewPager2 viewPager2, int mode) {
        runForViewPager2RecyclerView(viewPager2, rv -> rv.setOverScrollMode(mode));
    }

    /**
     * Performs an operation on every ViewHolder in a {@link RecyclerView} whose class matches
     * that of an argument.
     *
     * @param recyclerView    The {@link RecyclerView}
     * @param viewHolderClass Class of the ViewHolder upon which operations should be performed
     * @param func            The operation to run
     * @param <VH>            Type parameter of the ViewHolder type
     */
    public static <VH extends RecyclerView.ViewHolder> void forEachViewHolder(RecyclerView recyclerView, Class<VH> viewHolderClass, Consumer<VH> func) {
        viewHolderStream(recyclerView, viewHolderClass).forEach(func);
    }

    private static Field recyclerViewRecyclerField = null;
    private static Field recyclerCachedViewsField = null;

    /**
     * Creates a {@link Stream} that iterates over all ViewHolders in a {@link RecyclerView}
     * that match a provided class.
     * This method uses reflection to access RecyclerView internals in order to obtain
     * cached ViewHolders for views that are not attached to anything. It throws an eager
     * {@link RuntimeException} if the reflection fails, so any errors should be noticed immediately.
     * <p>
     * Note that the RecyclerView library is in maintenance mode, so future breaking changes are
     * unlikely.
     *
     * @param recyclerView    The {@link RecyclerView}
     * @param viewHolderClass Class of the ViewHolders to be processed. May also be {@link RecyclerView.ViewHolder} to process regardless of the subclass.
     * @param <VH>            Type parameter of the ViewHolder type
     * @return The ViewHolder stream
     */
    @SuppressWarnings("unchecked")
    public static <VH extends RecyclerView.ViewHolder> Stream<VH> viewHolderStream(RecyclerView recyclerView, Class<VH> viewHolderClass) {
        RecyclerView.Recycler recycler;
        List<VH> cachedViews;
        try {
            if (recyclerViewRecyclerField == null) {
                recyclerViewRecyclerField = RecyclerView.class.getDeclaredField("mRecycler");
                recyclerViewRecyclerField.setAccessible(true);
            }
            if (recyclerCachedViewsField == null) {
                recyclerCachedViewsField = RecyclerView.Recycler.class.getDeclaredField("mCachedViews");
                recyclerCachedViewsField.setAccessible(true);
            }
            recycler = (RecyclerView.Recycler) Objects.requireNonNull(recyclerViewRecyclerField.get(recyclerView));
            cachedViews = (List<VH>) Objects.requireNonNull(recyclerCachedViewsField.get(recycler));
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }

        return Stream.concat(
                        Stream.iterate(0, i -> i + 1)
                                .limit(recyclerView.getChildCount())
                                .map(recyclerView::getChildAt)
                                .map(recyclerView::getChildViewHolder),
                        Stream.concat(
                                recycler.getScrapList().stream(),
                                cachedViews.stream()
                        )
                )
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
     * @param textView      The TextView
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
     * @param view      The target view
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
     * @param b        Whether to add the bold style or to keep normal/italics
     */
    public static void setBold(TextView textView, boolean b) {
        Typeface typeface = textView.getTypeface();
        if (b) {
            if (!typeface.isBold()) {
                textView.setTypeface(typeface, Typeface.BOLD);
            }
        } else {
            if (typeface.isBold()) {
                textView.setTypeface(Typeface.create(typeface, typeface.isItalic() ? Typeface.ITALIC : Typeface.NORMAL));
            }
        }
    }

    /**
     * Set the color of the cursor in an EditText. On APIs older than Q, this uses reflection.
     * The color will be applied as a color filter over the existing cursor drawable.
     *
     * @param editText The EditText
     * @param color    The color
     */
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

    /**
     * Get the measured width of a string of text as if it was rendered in a TextView.
     *
     * @param textView The TextView
     * @param text     The text
     * @return Width of the text in pixels
     */
    public static float getTextWidth(TextView textView, String text) {
        return textView.getPaint().measureText(text);
    }

    /**
     * Forces an {@link ImageView} to start its animation if its drawable is an
     * {@link AnimatedVectorDrawable}. If the drawable is not an AVD, nothing happens.
     *
     * @param imageView
     */
    public static void startDrawableAnimation(ImageView imageView) {
        if (imageView.getDrawable() instanceof AnimatedVectorDrawable avd) {
            avd.start();
        }
    }

    /**
     * Sets an {@link AnimatedVectorDrawable} resource as the drawable of an {@link ImageView}
     * and automatically starts its animation.
     *
     * @param imageView The ImageView
     * @param resId     ID of the drawable resource
     */
    public static void setAnimatedDrawableAndStart(ImageView imageView, @DrawableRes int resId) {
        imageView.setImageResource(resId);
        startDrawableAnimation(imageView);
    }

    /**
     * Must be called on each view that will possibly use a drawable shape that is inverse clipped.
     * On Android 8 and lower, there is a bug that ignores inverse clipping and draws inside the clipped
     * shape on hardware layers, so on these versions, this method will force the view to use a software layer.
     * <p>
     * See <a href="https://stackoverflow.com/questions/42944569/path-filltype-inverse-winding-doesnt-work-in-hardwareaccelerated-view">StackOverflow</a>.
     *
     * @param view The view
     */
    public static void useOuterClipping(View view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            view.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
    }

    /**
     * Checks whether a layout dimension is an exact size (not wrap_content or match_parent).
     *
     * @param dimension The dimension to check
     * @return True if the dimension is exact, false otherwise
     */
    public static boolean isExactViewDimension(int dimension) {
        return dimension != ViewGroup.LayoutParams.WRAP_CONTENT && dimension != ViewGroup.LayoutParams.MATCH_PARENT;
    }

    /**
     * Measures a View as if it was set to {@code wrap_content} in both dimensions.
     * The results can then be obtained using {@link View#getMeasuredWidth()} and
     * {@link View#getMeasuredHeight()}.
     *
     * @param view The view
     */
    public static void measureViewForWrapContent(View view) {
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
    }

    /**
     * Measures a View as if its width was set to {@code wrap_content}, and
     * to a specific size in height. The result can then be obtained using
     * {@link View#getMeasuredHeight()}.
     *
     * @param view            The view
     * @param simulatedHeight Height to simulate in pixels
     */
    public static void measureViewWidthForWrapContent(View view, int simulatedHeight) {
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.makeMeasureSpec(simulatedHeight, View.MeasureSpec.EXACTLY));
    }

    /**
     * Measures a View as if its height was set to {@code wrap_content}, and
     * to a specific size in width. The result can then be obtained using
     * {@link View#getMeasuredHeight()}.
     *
     * @param view           The view
     * @param simulatedWidth Width to simulate in pixels
     */
    public static void measureViewHeightForWrapContent(View view, int simulatedWidth) {
        view.measure(View.MeasureSpec.makeMeasureSpec(simulatedWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
    }

    /**
     * Enables or disabled item change (modification events, not insertion/deletion) animations
     * of a {@link RecyclerView}, provided that its {@link RecyclerView.ItemAnimator}
     * is a {@link SimpleItemAnimator}. Otherwise, this method has no effect.
     *
     * @param view    The RecyclerView
     * @param enabled Whether to enable or disable change animations
     */
    public static void setRecyclerViewChangeAnimationsEnabled(RecyclerView view, boolean enabled) {
        if (view.getItemAnimator() instanceof SimpleItemAnimator simple) {
            simple.setSupportsChangeAnimations(enabled);
        }
    }

    /**
     * Adjust the height of a {@link ViewPager2} to match the height of its currently displayed item.
     *
     * @param viewPager The pager
     */
    public static void adjustViewPagerHeightToCurrentItem(ViewPager2 viewPager) {
        adjustViewPagerHeightToItem(viewPager, viewPager.getCurrentItem());
    }

    /**
     * Adjust the height of a {@link ViewPager2} to match the height of one of its items.
     *
     * @param viewPager The pager
     * @param itemIndex Index of the item to match the height to
     */
    public static void adjustViewPagerHeightToItem(ViewPager2 viewPager, int itemIndex) {
        runForViewPager2RecyclerView(viewPager, rv -> {
            RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(itemIndex);
            if (vh != null) {
                View itemView = vh.itemView;
                measureViewHeightForWrapContent(itemView, itemView.getWidth());
                ViewGroup.LayoutParams lp = viewPager.getLayoutParams();
                lp.height = itemView.getMeasuredHeight();
                viewPager.setLayoutParams(lp);
            }
        });
    }

    public static void setupViewPagerFragmentHeightAutoUpdate(FragmentManager fragmentManager) {
        fragmentManager.registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
            @Override
            public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
                super.onFragmentResumed(fm, f);
                View v = f.getView();
                if (v != null) {
                    v.requestLayout();
                }
            }
        }, false);
    }

    public static Stream<View> getChildren(ViewGroup root) {
        return IntStream.range(0, root.getChildCount()).mapToObj(root::getChildAt);
    }

    public static Stream<View> getChildrenRecursive(ViewGroup root) {
        return getChildren(root).flatMap(child -> {
            Stream<View> childStream = Stream.of(child);
            if (child instanceof ViewGroup vg) {
                Stream<View> nested = getChildrenRecursive(vg);
                return Stream.concat(childStream, nested);
            } else {
                return childStream;
            }
        });
    }

    public static Stream<View> findViewsAtPosition(ViewGroup root, float x, float y) {
        return getChildren(root)
                .filter(child -> x >= child.getLeft() && x <= child.getRight() && y >= child.getTop() && y <= child.getBottom())
                .flatMap(child -> {
                    Stream<View> childStream = Stream.of(child);
                    if (child instanceof ViewGroup vg) {
                        Stream<View> nested = findViewsAtPosition(vg, x - child.getLeft(), y - child.getTop());
                        return Stream.concat(childStream, nested);
                    } else {
                        return childStream;
                    }
                });
    }

    /**
     * Programmatically cancel any ongoing touch interactions with a View by sending
     * a {@link MotionEvent#ACTION_CANCEL} event to it. The event will be dispatched
     * at (0, 0) coordinates.
     *
     * @param view the View
     */
    public static void cancelTouchEvents(View view) {
        long now = SystemClock.uptimeMillis();
        MotionEvent cancelEvent = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0);
        view.dispatchTouchEvent(cancelEvent);
        view.cancelLongPress();
        cancelEvent.recycle();
    }

    public static void resizeListLinearLayout(LinearLayout ll, int newSize, Supplier<View> viewCtor) {
        resizeListLinearLayout(ll, newSize, viewCtor, v -> {
        });
    }

    public static void resizeListLinearLayout(LinearLayout ll, int newSize, Supplier<View> viewCtor, Consumer<View> viewRecycler) {
        while (ll.getChildCount() > newSize) {
            ll.removeViewAt(ll.getChildCount() - 1);
        }
        while (ll.getChildCount() < newSize) {
            ll.addView(viewCtor.get());
        }
    }

    @OptIn(markerClass = ExperimentalBadgeUtils.class)
    public static void fixBadgeDrawableAutoLayout(BadgeDrawable badge, View anchor) {
        anchor.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            BadgeUtils.setBadgeDrawableBounds(badge, anchor, null);
        });
    }

    public static class ViewEnabledSaveState {
        private final Map<Integer, Boolean> stateMap = new HashMap<>();
    }
}
