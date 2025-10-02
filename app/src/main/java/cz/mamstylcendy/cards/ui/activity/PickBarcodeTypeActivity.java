package cz.mamstylcendy.cards.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.IntentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.zxing.BarcodeFormat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import cz.mamstylcendy.cards.util.BarcodeTypeNames;
import cz.mamstylcendy.cards.R;
import cz.mamstylcendy.cards.util.ZxingCodeDrawable;
import cz.mamstylcendy.cards.databinding.ActivityPickBarcodeTypeBinding;
import cz.mamstylcendy.cards.databinding.BarcodeSampleCardBinding;

public class PickBarcodeTypeActivity extends AppCompatActivity {

    public static final String RESULT_EXTRA_BARCODE_FORMAT = "barcode_format";

    public static final ActivityResultContract<Void, BarcodeFormat> PICK_BARCODE_TYPE = new ActivityResultContract<Void, BarcodeFormat>() {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, Void unused) {
            return new Intent(context, PickBarcodeTypeActivity.class);
        }

        @Override
        public BarcodeFormat parseResult(int i, @Nullable Intent intent) {
            if (intent != null) {
                return IntentCompat.getSerializableExtra(intent, RESULT_EXTRA_BARCODE_FORMAT, BarcodeFormat.class);
            } else {
                return null;
            }
        }
    };

    private ActivityPickBarcodeTypeBinding ui;

    private CodeType[] codeTypes;
    private String[] codeTypeNames;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        ui = ActivityPickBarcodeTypeBinding.inflate(getLayoutInflater());
        setContentView(ui.getRoot());

        addBarcodeSelection(
                BarcodeFormat.EAN_13,
                R.string.code_type_ean_13,
                "0000690101612"
        );
        addBarcodeSelection(
                BarcodeFormat.CODE_128,
                R.string.code_type_code_128,
                new String(Base64.decode("SnVsa2EgamUgbmVqa3Jhc25lanNpIG5hIHN2ZXRlIDwz", Base64.DEFAULT))
        );
        addBarcodeSelection(
                BarcodeFormat.QR_CODE,
                R.string.code_type_qr,
                new String(Base64.decode("aHR0cHM6Ly93d3cueW91dHViZS5jb20vd2F0Y2g/dj1kUXc0dzlXZ1hjUQ==", Base64.DEFAULT), StandardCharsets.UTF_8)
        );

        ui.btnCodeTypeMoreOptions.setOnClickListener(v -> showMoreOptionsDialog());
    }

    private void addBarcodeSelection(BarcodeFormat format, @StringRes int nameRes, String sampleDrawableValue) {
        BarcodeSampleCardBinding binding = BarcodeSampleCardBinding.inflate(getLayoutInflater(), ui.llBarcodeTypeList, false);
        binding.tvBarcodeTypeName.setText(nameRes);
        ZxingCodeDrawable codeDrawable = new ZxingCodeDrawable(
                getResources(),
                new ZxingCodeDrawable.Options()
                        .setFormat(format)
                        .setCharacterSet("UTF-8")
                        .setData(sampleDrawableValue)
        );
        binding.ivBarcode.setImageDrawable(codeDrawable);
        binding.getRoot().setOnClickListener(v -> finishWithResult(format));
        ui.llBarcodeTypeList.addView(binding.getRoot());
    }

    private void showMoreOptionsDialog() {
        if (codeTypes == null) {
            codeTypes = getCodeTypes();
            codeTypeNames = Arrays.stream(codeTypes).map(CodeType::name).toArray(String[]::new);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.code_type_more_options)
                .setItems(codeTypeNames, (dialog, which) -> {
                    finishWithResult(codeTypes[which].format());
                })
                .show();
    }

    private CodeType[] getCodeTypes() {
        return Arrays
                .stream(BarcodeFormat.values())
                .map(format -> new CodeType(format, BarcodeTypeNames.get(this, format)))
                .filter(codeType -> codeType.name() != null)
                .toArray(CodeType[]::new);
    }

    private void finishWithResult(BarcodeFormat barcodeFormat) {
        setResult(RESULT_OK, new Intent().putExtra(RESULT_EXTRA_BARCODE_FORMAT, barcodeFormat));
        finish();
    }

    private static record CodeType(BarcodeFormat format, String name) {

    }
}
