package cz.nocard.android.ui.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.util.Size;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.view.LifecycleCameraController;
import androidx.camera.view.PreviewView;
import androidx.core.content.IntentCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import cz.nocard.android.R;
import cz.nocard.android.databinding.ActivityQrScanBinding;
import cz.spojenka.android.polyfills.VibratorCompat;
import cz.spojenka.android.system.PermissionRequestHelper;
import cz.spojenka.android.system.ZxingCameraImageAnalyzer;
import cz.spojenka.android.system.SubscreenSwitcher;
import cz.spojenka.android.system.ViewActionMutex;
import cz.spojenka.android.util.AsyncUtils;
import cz.spojenka.android.util.IntentUtils;

public class ZxingScanActivity extends AppCompatActivity implements PermissionRequestHelper.CameraPermissionHandler {

    private static final String TAG = ZxingScanActivity.class.getSimpleName();

    public static final String EXTRA_BARCODE_FORMAT = "barcode_format";
    public static final String RESULT_EXTRA_CODE = "code";

    private static final int ERROR_AGAIN_TIMEOUT = 2000;
    private static final int ERROR_VIEW_TIMEOUT = 1000;

    private static final boolean DISPLAY_GENERIC_ERRORS = false;

    public static final ActivityResultContract<BarcodeFormat, String> SCAN_STRING = new ActivityResultContract<BarcodeFormat, String>() {
        @Override
        public String parseResult(int resultCode, @Nullable Intent intent) {
            if (resultCode == RESULT_CANCELED || intent == null || !intent.hasExtra(RESULT_EXTRA_CODE)) {
                return null;
            }
            return intent.getStringExtra(RESULT_EXTRA_CODE);
        }

        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, BarcodeFormat barcodeFormat) {
            return new Intent(context, ZxingScanActivity.class)
                    .putExtra(EXTRA_BARCODE_FORMAT, barcodeFormat);
        }
    };

    private ActivityQrScanBinding binding;
    private PreviewView viewFinder;
    private SubscreenSwitcher subscreens;
    private boolean reqOpenPermSettings = false;
    private boolean viewFinderStarted = false;
    private LifecycleCameraController cameraController;
    private BarcodeFormat barcodeFormat;

    private PermissionRequestHelper.Requester<PermissionRequestHelper.CameraPermissionHandler> cameraPermissionRequester;

    private ZxingCameraImageAnalyzer analyzer;

    private Vibrator vibrator;

    private ViewActionMutex errorUIMutex;
    private LastErrorRecord lastError;
    private long lastErrorTS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT));
        binding = ActivityQrScanBinding.inflate(getLayoutInflater());
        cameraController = new LifecycleCameraController(this);
        viewFinder = binding.viewFinder;
        vibrator = VibratorCompat.getDefaultVibrator(this);

        Intent intent = getIntent();
        if (intent != null) {
            barcodeFormat = IntentCompat.getSerializableExtra(intent, EXTRA_BARCODE_FORMAT, BarcodeFormat.class);
        } else {
            barcodeFormat = BarcodeFormat.EAN_13;
        }

        analyzer = new ZxingCameraImageAnalyzer(ZxingCameraImageAnalyzer.readerFor(barcodeFormat), barcodeFormat != BarcodeFormat.QR_CODE) {
            @Override
            public void onError(ErrorCode errorCode) {
                if (DISPLAY_GENERIC_ERRORS) {
                    handleError(LastErrorRecord.ofGenericError(errorCode), getStringForErrorCode(errorCode));
                }
            }

            @Override
            public void onSuccess(Result result) {
                if (checkResultPattern(result)) {
                    cameraController.clearImageAnalysisAnalyzer();
                    indicateSuccess();
                    setResult(result);
                    finish();
                } else {
                    handleError(LastErrorRecord.ofIncompatibleCode(result), getString(R.string.code_not_compatible));
                }
            }
        };
        analyzer.setCallbackExecutor(getLifecycleExecutor());
        subscreens = new SubscreenSwitcher(binding.permRequestBg, binding.clPermNotGranted, binding.clQrScan);
        setContentView(binding.getRoot());
        errorUIMutex = new ViewActionMutex(this, binding.ivCenterMarker);

        binding.btnFixCameraPermission.setOnClickListener(v -> {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                reqOpenPermSettings = true;
            }
            cameraPermissionRequester.request(this);
        });

        cameraPermissionRequester = PermissionRequestHelper.setupRequestCameraPermission(this, this);
        cameraPermissionRequester.request(this);
    }

    private void playSuccessVibration() {
        if (vibrator != null) {
            VibratorCompat.vibrate(vibrator, 50);
        } else {
            Log.w(TAG, "Vibrator not available");
        }
    }

    private void playErrorVibration() {
        if (vibrator != null) {
            VibratorCompat.vibrate(vibrator, new long[]{0, 50, 50, 50}, -1);
        } else {
            Log.w(TAG, "Vibrator not available");
        }
    }

    private void indicateSuccess() {
        playSuccessVibration();
        errorUIMutex.runNow(() -> {
            setViewFinderFrameColor(getColor(R.color.light_green));
            showErrorText(null);
        });
    }

    private void handleError(LastErrorRecord error, String text) {
        if (Objects.equals(error, lastError)) {
            if (System.currentTimeMillis() - lastErrorTS < ERROR_AGAIN_TIMEOUT) {
                return;
            }
        }
        lastError = error;
        lastErrorTS = System.currentTimeMillis();
        indicateError(text);
    }

    private void indicateError(String text) {
        playErrorVibration();
        errorUIMutex.runNow(() -> {
            setViewFinderFrameColor(getColor(R.color.spanish_red));
            showErrorText(text);
        });
        errorUIMutex.postDelayed(() -> {
            setViewFinderFrameColor(getColor(R.color.overlay_white));
            showErrorText(null);
        }, ERROR_VIEW_TIMEOUT);
    }

    private void setViewFinderFrameColor(@ColorInt int color) {
        binding.ivCenterMarker.setImageTintList(ColorStateList.valueOf(color));
    }

    private void showErrorText(String text) {
        if (text == null) {
            binding.tvErrorText.setVisibility(View.GONE);
        } else {
            binding.tvErrorText.setVisibility(View.VISIBLE);
            binding.tvErrorText.setText(text);
        }
    }

    private String getStringForErrorCode(ZxingCameraImageAnalyzer.ErrorCode errorCode) {
        return getString(switch (errorCode) {
            case BAD_CHECKSUM -> R.string.code_bad_checksum;
            case BAD_FORMAT -> R.string.code_bad_format;
        });
    }

    protected boolean checkResultPattern(Result result) {
        return true;
    }

    private void setResult(Result result) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(RESULT_EXTRA_CODE, result.getText());
        setResult(RESULT_OK, resultIntent);
    }

    @Override
    public void onPermissionGranted() {
        subscreens.setSubscreen(binding.clQrScan);
        tryStartViewFinder();
    }

    @Override
    protected void onResume() {
        super.onResume();
        tryStartViewFinder();
    }

    @Override
    public void onPermissionDenied(boolean neverAskAgain) {
        if (neverAskAgain && reqOpenPermSettings) {
            startActivity(
                    IntentUtils.createApplicationDetailsIntent(getPackageName())
                            .addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            );
        }
        reqOpenPermSettings = false;
        subscreens.setSubscreen(binding.clPermNotGranted);
    }

    private boolean isInViewFinder() {
        return binding.clQrScan.getVisibility() == View.VISIBLE;
    }

    private void tryStartViewFinder() {
        if (!isInViewFinder()) {
            return;
        }
        if (viewFinderStarted) {
            return;
        }
        viewFinderStarted = true;

        cameraController.getInitializationFuture().addListener(() -> {
            CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;
            if (!cameraController.hasCamera(selector)) {
                selector = CameraSelector.DEFAULT_FRONT_CAMERA;
                if (!cameraController.hasCamera(selector)) {
                    showCameraError();
                    return;
                }
            }
            cameraController.setCameraSelector(selector);
            cameraController.setImageAnalysisResolutionSelector(
                    new ResolutionSelector.Builder()
                            .setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
                            .setResolutionStrategy(new ResolutionStrategy(new Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                            .build()
            );
            cameraController.setImageAnalysisAnalyzer(Executors.newSingleThreadExecutor(), analyzer);
            cameraController.bindToLifecycle(ZxingScanActivity.this);

            viewFinder.setController(cameraController);
        }, getLifecycleExecutor());
    }

    private Executor getLifecycleExecutor() {
        return AsyncUtils.getLifecycleExecutor(this);
    }

    private void showCameraError() {
        subscreens.setSubscreen(binding.permRequestBg);
        new AlertDialog.Builder(this)
                .setTitle(R.string.error)
                .setMessage(R.string.camera_error_message)
                .setPositiveButton(android.R.string.ok, null)
                .setOnDismissListener(dialog -> {
                    setResult(RESULT_CANCELED);
                    finish();
                })
                .show();
    }

    private record LastErrorRecord(ZxingCameraImageAnalyzer.ErrorCode code, String qrData) {

        public static LastErrorRecord ofIncompatibleCode(Result code) {
            return new LastErrorRecord(ZxingCameraImageAnalyzer.ErrorCode.BAD_FORMAT, code.getText());
        }

        public static LastErrorRecord ofGenericError(ZxingCameraImageAnalyzer.ErrorCode code) {
            return new LastErrorRecord(code, null);
        }
    }
}
