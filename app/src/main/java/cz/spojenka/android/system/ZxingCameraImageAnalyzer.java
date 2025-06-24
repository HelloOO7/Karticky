package cz.spojenka.android.system;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Binarizer;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Reader;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public abstract class ZxingCameraImageAnalyzer implements ImageAnalysis.Analyzer {

    private final Reader reader;
    private Binarizer binarizer;
    private Executor callbackExecutor;
    private boolean needsRotate;

    public ZxingCameraImageAnalyzer(Reader reader, boolean needsRotate) {
        this.reader = reader;
        this.needsRotate = needsRotate;
    }

    public void setCallbackExecutor(Executor callbackExecutor) {
        this.callbackExecutor = callbackExecutor;
    }

    public static Reader qrReader() {
        return new QRCodeReader();
    }

    public static Reader readerFor(BarcodeFormat format) {
        return new ReaderForFormat(List.of(format));
    }

    public abstract void onError(ErrorCode errorCode);
    public abstract void onSuccess(Result result);

    @Override
    public void analyze(@NonNull ImageProxy image) {
        try (image) {
            LuminanceSource source = createLuminanceSource(image);

            if (binarizer == null) {
                binarizer = new HybridBinarizer(source);
            } else {
                binarizer = binarizer.createBinarizer(source);
            }

            BinaryBitmap bitmap = new BinaryBitmap(binarizer);

            Result result = reader.decode(bitmap);
            runCallback(() -> onSuccess(result));
        } catch (NotFoundException ignored) {
            //continue
        } catch (ChecksumException e) {
            fail(e, ErrorCode.BAD_CHECKSUM);
        } catch (FormatException e) {
            fail(e, ErrorCode.BAD_FORMAT);
        }
    }

    private void runCallback(Runnable callback) {
        if (callbackExecutor != null) {
            callbackExecutor.execute(callback);
        } else {
            callback.run();
        }
    }

    private void fail(Exception e, ErrorCode errorCode) {
        Log.e(getClass().getSimpleName(), "Error decoding QR code", e);
        onError(errorCode);
    }

    private byte[] getImageBuffer(ImageProxy imageProxy) {
        ByteBuffer buffer = imageProxy.getPlanes()[0].getBuffer();
        buffer.rewind();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    private LuminanceSource createLuminanceSource(ImageProxy image) {
        if (!needsRotate || image.getImageInfo().getRotationDegrees() == 0) {
            byte[] buffer = getImageBuffer(image);
            return switch (image.getFormat()) {
                case ImageFormat.YUV_420_888, ImageFormat.YUV_422_888, ImageFormat.YUV_444_888 ->
                        new PlanarYUVLuminanceSource(
                                buffer,
                                image.getWidth(),
                                image.getHeight(),
                                0,
                                0,
                                image.getWidth(),
                                image.getHeight(),
                                false
                        );
                default ->
                        throw new IllegalArgumentException("Unsupported image format: " + image.getFormat());
            };
        } else {
            //slow
            Bitmap bitmap = image.toBitmap();
            Matrix rotationMatrix = new Matrix();
            rotationMatrix.postRotate(image.getImageInfo().getRotationDegrees());
            Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), rotationMatrix, false);
            return createLuminanceSourceForBitmap(rotatedBitmap);
        }
    }

    public static RGBLuminanceSource createLuminanceSourceForBitmap(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        return new RGBLuminanceSource(w, h, pixels);
    }

    public static enum ErrorCode {
        BAD_CHECKSUM,
        BAD_FORMAT,
    }

    private static class ReaderForFormat implements Reader {

        private final MultiFormatReader reader;

        public ReaderForFormat(List<BarcodeFormat> formats) {
            reader = new MultiFormatReader();
            reader.setHints(Map.of(DecodeHintType.POSSIBLE_FORMATS, formats));
        }

        @Override
        public Result decode(BinaryBitmap image) throws NotFoundException {
            return reader.decodeWithState(image);
        }

        @Override
        public Result decode(BinaryBitmap image, Map<DecodeHintType, ?> hints) throws NotFoundException {
            return reader.decode(image, hints);
        }

        @Override
        public void reset() {
            reader.reset();
        }
    }
}
