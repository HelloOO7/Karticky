package cz.spojenka.android.ui.helpers;

import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class EdgeToEdgeSupport {

    public static int SIDE_TOP = 1;
    public static int SIDE_BOTTOM = 2;
    public static int SIDE_LEFT = 4;
    public static int SIDE_RIGHT = 8;
    public static int SIDE_HORIZONTAL = SIDE_LEFT | SIDE_RIGHT;
    public static int SIDE_VERTICAL = SIDE_TOP | SIDE_BOTTOM;
    public static int SIDE_ALL = SIDE_HORIZONTAL | SIDE_VERTICAL;

    public static int FLAG_APPLY_AS_PADDING = 1;

    public static void useExplicitFitsSystemWindows(View view) {
        useExplicitFitsSystemWindows(view, SIDE_ALL, 0, null);
    }

    public static void useExplicitFitsSystemWindows(View view, int sides, int flags) {
        useExplicitFitsSystemWindows(view, sides, flags, null);
    }

    public static void useExplicitFitsSystemWindows(View view, int sides, int flags, Interceptor interceptor) {
        ViewGroup.MarginLayoutParams baseLayoutParams = new ViewGroup.MarginLayoutParams(
                (ViewGroup.MarginLayoutParams) view.getLayoutParams()
        );
        int basePaddingTop = view.getPaddingTop();
        int basePaddingBottom = view.getPaddingBottom();
        int basePaddingLeft = view.getPaddingLeft();
        int basePaddingRight = view.getPaddingRight();

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                insets = WindowInsetsCompat.toWindowInsetsCompat(v.getRootWindowInsets());
            }
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            boolean usePadding = ((flags & FLAG_APPLY_AS_PADDING) != 0);

            int left, top, right, bottom;

            if (!usePadding) {
                left = baseLayoutParams.leftMargin;
                top = baseLayoutParams.topMargin;
                right = baseLayoutParams.rightMargin;
                bottom = baseLayoutParams.bottomMargin;
            } else {
                left = basePaddingLeft;
                top = basePaddingTop;
                right = basePaddingRight;
                bottom = basePaddingBottom;
            }

            left = computeSideValue(left, systemBars.left, sides, SIDE_LEFT, interceptor);
            top = computeSideValue(top, systemBars.top, sides, SIDE_TOP, interceptor);
            right = computeSideValue(right, systemBars.right, sides, SIDE_RIGHT, interceptor);
            bottom = computeSideValue(bottom, systemBars.bottom, sides, SIDE_BOTTOM, interceptor);

            if (!usePadding) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                params.setMargins(left, top, right, bottom);

                v.setLayoutParams(params);
            } else {
                v.setPadding(left, top, right, bottom);
            }

            return new WindowInsetsCompat.Builder(insets)
                    .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(
                            adjustByNotSide(systemBars.left, sides, SIDE_LEFT),
                            adjustByNotSide(systemBars.top, sides, SIDE_TOP),
                            adjustByNotSide(systemBars.right, sides, SIDE_RIGHT),
                            adjustByNotSide(systemBars.bottom, sides, SIDE_BOTTOM)
                    ))
                    .build();
        });
    }

    private static int computeSideValue(int baseValue, int systemBarValue, int sideMask, int sideBit, Interceptor interceptor) {
        int computed = baseValue + adjustBySide(systemBarValue, sideMask, sideBit);
        if (interceptor != null) {
            computed = interceptor.interceptMargin(sideBit, computed);
        }
        return computed;
    }

    private static int adjustBySide(int value, int sideMask, int expectedBit) {
        if ((sideMask & expectedBit) != 0) {
            return value;
        } else {
            return 0;
        }
    }

    private static int adjustByNotSide(int value, int sideMask, int expectedBit) {
        if ((sideMask & expectedBit) == 0) {
            return value;
        } else {
            return 0;
        }
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
            useExplicitFitsSystemWindows(view);
        }
    }

    public interface Interceptor {

        int interceptMargin(int side, int computedMargin);
    }
}
