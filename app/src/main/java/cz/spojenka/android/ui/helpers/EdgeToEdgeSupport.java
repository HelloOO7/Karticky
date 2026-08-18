package cz.spojenka.android.ui.helpers;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.ViewGroupCompat;
import androidx.core.view.WindowInsetsCompat;

import org.jspecify.annotations.NonNull;

import cz.spojenka.android.util.ViewUtils;

public class EdgeToEdgeSupport {

    public static final int SIDE_TOP = WindowInsetsCompat.Side.TOP;
    public static final int SIDE_BOTTOM = WindowInsetsCompat.Side.BOTTOM;
    public static final int SIDE_LEFT = WindowInsetsCompat.Side.LEFT;
    public static final int SIDE_RIGHT = WindowInsetsCompat.Side.RIGHT;
    public static final int SIDE_RELATIVE_DIR = 16;
    public static final int SIDE_START = SIDE_LEFT | SIDE_RELATIVE_DIR;
    public static final int SIDE_END = SIDE_RIGHT | SIDE_RELATIVE_DIR;
    public static final int SIDE_HORIZONTAL = SIDE_LEFT | SIDE_RIGHT;
    public static final int SIDE_VERTICAL = SIDE_TOP | SIDE_BOTTOM;
    public static final int SIDE_ALL = SIDE_HORIZONTAL | SIDE_VERTICAL;

    public static final int FLAG_APPLY_AS_PADDING = 1;
    public static final int FLAG_APPLY_AS_DIMENSION = 2;
    public static final int FLAG_INCLUDE_IME = 4;
    public static final int FLAG_BOTTOM_ONLY_IME = 8;

    public static void installInsets(View view) {
        installInsets(view, SIDE_ALL, 0, null);
    }

    public static void installInsets(View view, int sides) {
        installInsets(view, sides, 0, null);
    }

    public static void installInsets(View view, int sides, int flags) {
        installInsets(view, sides, flags, null);
    }

    public static void installInsets(View view, int sides, int flags, Interceptor interceptor) {
        int basePaddingTop = view.getPaddingTop();
        int basePaddingBottom = view.getPaddingBottom();
        int basePaddingLeft = view.getPaddingLeft();
        int basePaddingRight = view.getPaddingRight();

        ViewCompat.setOnApplyWindowInsetsListener(view, new OnApplyWindowInsetsListener() {

            private boolean layoutParamsInitialized = false;
            private ViewGroup.MarginLayoutParams baseMarginParams;
            private int baseWidth;
            private int baseHeight;

            @Override
            public @NonNull WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                if (!layoutParamsInitialized) {
                    ViewGroup.LayoutParams lp = view.getLayoutParams();
                    if (lp == null) {
                        return insets;
                    }
                    baseWidth = lp.width;
                    baseHeight = lp.height;
                    if (lp instanceof ViewGroup.MarginLayoutParams) {
                        baseMarginParams = new ViewGroup.MarginLayoutParams((ViewGroup.MarginLayoutParams) lp);
                        baseMarginParams.resolveLayoutDirection(v.getLayoutDirection());
                    }
                    layoutParamsInitialized = true;
                }

                boolean usePadding = checkBit(flags, FLAG_APPLY_AS_PADDING);
                boolean useDimension = checkBit(flags, FLAG_APPLY_AS_DIMENSION);

                int left, top, right, bottom;

                if (usePadding) {
                    left = basePaddingLeft;
                    top = basePaddingTop;
                    right = basePaddingRight;
                    bottom = basePaddingBottom;
                } else if (useDimension) {
                    left = 0;
                    top = 0;
                    right = 0;
                    bottom = 0;
                } else {
                    if (baseMarginParams == null) {
                        throw new IllegalStateException("View " + view + " does not have margin layout params");
                    }
                    left = baseMarginParams.leftMargin;
                    top = baseMarginParams.topMargin;
                    right = baseMarginParams.rightMargin;
                    bottom = baseMarginParams.bottomMargin;
                }

                int finalSides = adjustSideMaskForRtl(v, sides);

                WindowInsetsCompat.Builder adjustedInsets = new WindowInsetsCompat.Builder(insets);
                int insetTypes = WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout();
                if (checkBit(flags, FLAG_INCLUDE_IME)) {
                    insetTypes |= WindowInsetsCompat.Type.ime();
                }

                int[] typeQueue;
                if (checkBit(flags, FLAG_BOTTOM_ONLY_IME)) {
                    int imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
                    if (imeInsets > 0) {
                        typeQueue = new int[]{insetTypes, finalSides & ~SIDE_BOTTOM, finalSides, WindowInsetsCompat.Type.ime(), SIDE_BOTTOM, SIDE_BOTTOM};
                    } else {
                        typeQueue = new int[]{insetTypes, finalSides & ~SIDE_BOTTOM, finalSides & ~SIDE_BOTTOM};
                    }
                } else {
                    typeQueue = new int[]{insetTypes, finalSides, finalSides};
                }

                for (int i = 0; i < typeQueue.length; i += 3) {
                    insetTypes = typeQueue[i];
                    finalSides = typeQueue[i + 1];
                    int consumeSides = typeQueue[i + 2];

                    Insets insetValues = insets.getInsets(insetTypes);

                    left = computeSideValue(left, insetValues.left, finalSides, SIDE_LEFT, interceptor);
                    top = computeSideValue(top, insetValues.top, finalSides, SIDE_TOP, interceptor);
                    right = computeSideValue(right, insetValues.right, finalSides, SIDE_RIGHT, interceptor);
                    bottom = computeSideValue(bottom, insetValues.bottom, finalSides, SIDE_BOTTOM, interceptor);

                    adjustedInsets.setInsets(insetTypes, Insets.of(
                            adjustByNotSide(insetValues.left, consumeSides, SIDE_LEFT),
                            adjustByNotSide(insetValues.top, consumeSides, SIDE_TOP),
                            adjustByNotSide(insetValues.right, consumeSides, SIDE_RIGHT),
                            adjustByNotSide(insetValues.bottom, consumeSides, SIDE_BOTTOM)
                    ));
                }

                if (usePadding) {
                    v.setPadding(left, top, right, bottom);
                } else if (useDimension) {
                    ViewGroup.LayoutParams params = v.getLayoutParams();
                    if (ViewUtils.isExactViewDimension(baseWidth)) {
                        params.width = baseWidth + left + right;
                    }
                    if (ViewUtils.isExactViewDimension(baseHeight)) {
                        params.height = baseHeight + top + bottom;
                    }
                    v.setLayoutParams(params);
                } else {
                    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                    ViewUtils.setMargin(params, left, top, right, bottom);
                    v.setLayoutParams(params);
                }

                return adjustedInsets.build();
            }
        });
    }

    public static void uninstallInsets(View view) {
        ViewCompat.setOnApplyWindowInsetsListener(view, null);
    }

    private static boolean isRtlDirection(int direction) {
        return direction == View.LAYOUT_DIRECTION_RTL;
    }

    private static int adjustSideMaskForRtl(View v, int sides) {
        boolean isRtl = checkBit(sides, SIDE_RELATIVE_DIR) && isRtlDirection(v.getLayoutDirection());

        int finalSides = sides;

        if (isRtl) {
            finalSides = finalSides & ~(SIDE_LEFT | SIDE_RIGHT);
            if (checkBit(sides, SIDE_LEFT)) {
                finalSides |= SIDE_RIGHT;
            }
            if (checkBit(sides, SIDE_RIGHT)) {
                finalSides |= SIDE_LEFT;
            }
        }
        return finalSides;
    }

    private static boolean checkBit(int mask, int bit) {
        return (mask & bit) != 0;
    }

    private static int computeSideValue(int baseValue, int systemBarValue, int sideMask, int sideBit, Interceptor interceptor) {
        int computed = baseValue + adjustBySide(systemBarValue, sideMask, sideBit);
        if (interceptor != null) {
            computed = interceptor.interceptMargin(sideBit, computed);
        }
        return computed;
    }

    private static int adjustBySide(int value, int sideMask, int expectedBit) {
        if (checkBit(sideMask, expectedBit)) {
            return value;
        } else {
            return 0;
        }
    }

    private static int adjustByNotSide(int value, int sideMask, int expectedBit) {
        if (!checkBit(sideMask, expectedBit)) {
            return value;
        } else {
            return 0;
        }
    }

    /**
     * See <a href="https://issuetracker.google.com/issues/282790626">this Android issue</a>.
     *
     * @param activity The activity whose decor view will get the compat insets fixups
     */
    public static void registerCompatInsetsFixups(Activity activity) {
        ViewGroupCompat.installCompatInsetsDispatch(activity.getWindow().getDecorView());
    }

    public interface Interceptor {

        int interceptMargin(int side, int computedMargin);
    }
}
