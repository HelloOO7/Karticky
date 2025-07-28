package cz.spojenka.android.ui.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A fully transparent drawable that does nothing. This may be used in cases
 * where setting a null drawable has undesirable effects on view layout.
 */
public class EmptyDrawable extends Drawable {

    private int intrinsicWidth = -1;
    private int intrinsicHeight = -1;

    public EmptyDrawable() {

    }

    public EmptyDrawable(int intrinsicWidth, int intrinsicHeight) {
        this.intrinsicWidth = intrinsicWidth;
        this.intrinsicHeight = intrinsicHeight;
    }

    @Override
    public int getIntrinsicWidth() {
        return intrinsicWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return intrinsicHeight;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {

    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }
}
