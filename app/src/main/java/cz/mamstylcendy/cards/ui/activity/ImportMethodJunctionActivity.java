package cz.mamstylcendy.cards.ui.activity;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.IntentCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Binarizer;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.common.HybridBinarizer;

import cz.mamstylcendy.cards.util.BarcodeTypeNames;
import cz.mamstylcendy.cards.ui.dialogs.CommonDialogs;
import cz.mamstylcendy.cards.ui.dialogs.ProgressDialog;
import cz.mamstylcendy.cards.R;
import cz.mamstylcendy.cards.databinding.ActivityImportMethodChoiceBinding;
import cz.spojenka.android.system.ZxingCameraImageAnalyzer;
import cz.spojenka.android.util.AsyncUtils;

public class ImportMethodJunctionActivity extends AppCompatActivity {

    public static final String EXTRA_BARCODE_FORMAT = "barcode_format";
    public static final String RESULT_EXTRA_CODE = "code";

    public static final ActivityResultContract<BarcodeFormat, String> IMPORT_CARD_CODE = new ActivityResultContract<BarcodeFormat, String>() {
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
            return new Intent(context, ImportMethodJunctionActivity.class)
                    .putExtra(EXTRA_BARCODE_FORMAT, barcodeFormat);
        }
    };

    private ActivityImportMethodChoiceBinding ui;

    private BarcodeFormat barcodeFormat = BarcodeFormat.EAN_13;

    private ActivityResultLauncher<BarcodeFormat> scanCodeLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> pickImageLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        ui = ActivityImportMethodChoiceBinding.inflate(getLayoutInflater());
        setContentView(ui.getRoot());

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_BARCODE_FORMAT)) {
            barcodeFormat = IntentCompat.getSerializableExtra(intent, EXTRA_BARCODE_FORMAT, BarcodeFormat.class);
        }

        scanCodeLauncher = registerForActivityResult(ZxingScanActivity.SCAN_STRING, scanned -> {
            if (scanned != null) {
                finishWithResult(scanned);
            }
        });

        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), imageContentUri -> {
            if (imageContentUri == null) {
                return;
            }
            ContentResolver contentResolver = getContentResolver();
            BarcodeFormat format = barcodeFormat;
            ProgressDialog.doInBackground(this, R.string.reading_code, () -> {
                try (ParcelFileDescriptor pfd = contentResolver.openFileDescriptor(imageContentUri, "r")) {
                    if (pfd == null) {
                        return null; //content resolver crashed
                    }
                    Bitmap bitmap = BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor());
                    Binarizer binarizer = new HybridBinarizer(ZxingCameraImageAnalyzer.createLuminanceSourceForBitmap(bitmap));
                    BinaryBitmap binaryBitmap = new BinaryBitmap(binarizer);
                    return ZxingCameraImageAnalyzer.readerFor(format).decode(binaryBitmap);
                }
            }).handleAsync((scanned, ex) -> {
                if (ex != null) {
                    Log.e(getClass().getSimpleName(), "Error reading code from image", ex);
                    CommonDialogs.newInfoDialog(
                            this,
                            getString(R.string.error),
                            getString(R.string.error_reading_code, BarcodeTypeNames.getOrDefault(this, barcodeFormat))
                    ).show();
                } else if (scanned != null) {
                    finishWithResult(scanned.getText());
                }
                return null;
            }, AsyncUtils.getLifecycleExecutor(this));
        });

        ui.btnScanCode.setOnClickListener(v -> scanCodeLauncher.launch(barcodeFormat));

        ui.btnPickFile.setOnClickListener(v -> pickImageLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setDefaultTab(ActivityResultContracts.PickVisualMedia.DefaultTab.PhotosTab.INSTANCE)
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()
        ));

        ui.btnManualInput.setOnClickListener(v -> {
            EditText editText = new EditText(this);
            editText.requestFocus();
            CommonDialogs
                    .newTextInputDialog(this, editText)
                    .setTitle(R.string.enter_code)
                    .setPositiveButton(R.string.confirm, (dialog, which) -> {
                        String code = editText.getText().toString();
                        if (!code.isEmpty()) {
                            finishWithResult(code);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
    }

    private void finishWithResult(String code) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(RESULT_EXTRA_CODE, code);
        setResult(RESULT_OK, resultIntent);
        finish();
    }
}
