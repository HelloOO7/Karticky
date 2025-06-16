package cz.nocard.android;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.Writer;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.Map;

public class ZxingCodeDrawable extends BitmapDrawable {

    private final float paddingPercent;
    private final int backgroundColor;
    private final Paint backgroundPaint = new Paint();

    public ZxingCodeDrawable(Resources resources, Options options) {
        super(resources, generateQRBitmap(options));
        setFilterBitmap(false);
        paddingPercent = options.padding;
        backgroundColor = options.backgroundColor;
        backgroundPaint.setColor(backgroundColor);
        backgroundPaint.setStyle(Paint.Style.FILL);
    }

    private final Outline imageOutline = new Outline();
    private final Rect imageRect = new Rect();

    @Override
    public void draw(Canvas canvas) {
        if (paddingPercent > 0) {
            if (backgroundColor != Color.TRANSPARENT) {
                getOutline(imageOutline);
                imageOutline.getRect(imageRect);
                canvas.drawRect(imageRect, backgroundPaint);
            }
            var bounds = getBounds();
            int width = bounds.width();
            int height = bounds.height();
            float paddingX = (int) (width * paddingPercent);
            float paddingY = (int) (height * paddingPercent);
            canvas.save();
            canvas.scale(
                    (width - paddingX * 2) / width,
                    (height - paddingY * 2) / height,
                    bounds.centerX(),
                    bounds.centerY()
            );
            super.draw(canvas);
            canvas.restore();
        } else {
            super.draw(canvas);
        }
    }

    private static Bitmap generateQRBitmap(Options options) {
        BitMatrix bitMatrix = createBitMatrix(options);
        int width = bitMatrix.getWidth();
        int height = bitMatrix.getHeight();
        int actualHeight = height;
        if (height == 1) {
            height = 48;
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                bitmap.setPixel(x, y, bitMatrix.get(x, Math.min(actualHeight - 1, y)) ? options.foregroundColor : options.backgroundColor);
            }
        }
        return bitmap;
    }

    private static BitMatrix createBitMatrix(Options options) {
        try {
            String text = options.text;
            if (options.format == BarcodeFormat.EAN_13) {
                if (text.length() < 13) {
                    text = "0".repeat(13 - text.length()) + text; // pad with zeros to 13 digits
                }
                return new CustomEAN13Writer().encodeAndRender(text, 128, 64);
            } else {
                return new MultiFormatWriter().encode(text, options.format, 0, 0, Map.of(
                        EncodeHintType.ERROR_CORRECTION, options.errorCorrectionLevel,
                        EncodeHintType.MARGIN, 0
                ));
            }
        } catch (WriterException e) {
            throw new RuntimeException(e);
        }
    }

    public static class Options {

        private int backgroundColor = Color.TRANSPARENT;
        private int foregroundColor = Color.BLACK;
        private ErrorCorrectionLevel errorCorrectionLevel = ErrorCorrectionLevel.L;
        private BarcodeFormat format = BarcodeFormat.QR_CODE;
        private String text;
        private float padding = 0f;

        public Options setBackgroundColor(int color) {
            this.backgroundColor = color;
            return this;
        }

        public Options setForegroundColor(int color) {
            this.foregroundColor = color;
            return this;
        }

        public Options setErrorCorrectionLevel(ErrorCorrectionLevel level) {
            this.errorCorrectionLevel = level;
            return this;
        }

        public Options setFormat(BarcodeFormat format) {
            this.format = format;
            return this;
        }

        public Options setData(String string) {
            this.text = string;
            return this;
        }

        public Options setPadding(float padding) {
            this.padding = padding;
            return this;
        }
    }
}
