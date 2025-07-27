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
        useExplicitFitsSystemWindows(view, SIDE_ALL, 0);
    }

    public static void useExplicitFitsSystemWindows(View view, int sides, int flags) {
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

            if ((flags & FLAG_APPLY_AS_PADDING) == 0) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();

                params.setMargins(
                        adjustBySide(systemBars.left, sides, SIDE_LEFT) + baseLayoutParams.leftMargin,
                        adjustBySide(systemBars.top, sides, SIDE_TOP) + baseLayoutParams.topMargin,
                        adjustBySide(systemBars.right, sides, SIDE_RIGHT) + baseLayoutParams.rightMargin,
                        adjustBySide(systemBars.bottom, sides, SIDE_BOTTOM) + baseLayoutParams.bottomMargin
                );

                v.setLayoutParams(params);
            } else {
                v.setPadding(
                        adjustBySide(systemBars.left, sides, SIDE_LEFT) + basePaddingLeft,
                        adjustBySide(systemBars.top, sides, SIDE_TOP) + basePaddingTop,
                        adjustBySide(systemBars.right, sides, SIDE_RIGHT) + basePaddingRight,
                        adjustBySide(systemBars.bottom, sides, SIDE_BOTTOM) + basePaddingBottom
                );
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
}
