package cz.nocard.android.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.IntentCompat;
import androidx.core.os.BundleCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.zxing.BarcodeFormat;
import com.rarepebble.colorpicker.ColorPickerView;

import cz.nocard.android.R;
import cz.nocard.android.util.ZxingCodeDrawable;
import cz.nocard.android.data.PersonalCard;
import cz.nocard.android.data.PersonalCardStore;
import cz.nocard.android.databinding.ActivityNewCustomCardBinding;
import cz.spojenka.android.ui.helpers.SimpleTextWatcher;
import cz.spojenka.android.util.ViewUtils;

public class NewCustomCardActivity extends AppCompatActivity {

    public static final String RESULT_EXTRA_CARD = "card";

    private static final String STATE_CHOSEN_BARCODE_TYPE = "chosen_barcode_type";
    private static final String STATE_FIRST_LAUNCH = "first_launch";
    private static final String STATE_CARD_CODE = "card_code";
    private static final String STATE_CARD_PROVIDER_NAME = "card_provider_name";
    private static final String STATE_CARD_NAME = "card_name";
    private static final String STATE_CARD_COLOR = "card_color";
    private static final String STATE_CARD_NUMBER_CUSTOMIZED = "card_number_customized";

    public static ActivityResultContract<Void, PersonalCard> NEW_CUSTOM_CARD = new ActivityResultContract<Void, PersonalCard>() {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, Void unused) {
            return new Intent(context, NewCustomCardActivity.class);
        }

        @Override
        public PersonalCard parseResult(int resultCode, @Nullable Intent intent) {
            if (resultCode == RESULT_OK && intent != null) {
                return IntentCompat.getParcelableExtra(intent, RESULT_EXTRA_CARD, PersonalCard.class);
            } else {
                return null;
            }
        }
    };

    private ActivityNewCustomCardBinding ui;

    private ActivityResultLauncher<Void> pickBarcodeTypeLauncher;
    private ActivityResultLauncher<BarcodeFormat> importCardCodeLauncher;

    private boolean isFirstLaunch = true;
    private boolean isStartingChildActivity = false;

    private BarcodeFormat chosenBarcodeType;
    private String cardCode;
    private String providerName = "";
    private String cardName = "";
    private int cardColor;
    private boolean cardNumberIsCustomized;
    private boolean ignoreTextChanges = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = ActivityNewCustomCardBinding.inflate(getLayoutInflater());
        setContentView(ui.getRoot());

        cardColor = getColor(R.color.card_color_default);

        if (savedInstanceState != null) {
            isFirstLaunch = savedInstanceState.getBoolean(STATE_FIRST_LAUNCH, true);
            chosenBarcodeType = BundleCompat.getSerializable(savedInstanceState, STATE_CHOSEN_BARCODE_TYPE, BarcodeFormat.class);
            cardCode = savedInstanceState.getString(STATE_CARD_CODE, null);
            providerName = savedInstanceState.getString(STATE_CARD_PROVIDER_NAME, "");
            cardName = savedInstanceState.getString(STATE_CARD_NAME, "");
            cardColor = savedInstanceState.getInt(STATE_CARD_COLOR, cardColor);
            cardNumberIsCustomized = savedInstanceState.getBoolean(STATE_CARD_NUMBER_CUSTOMIZED, false);
        }

        pickBarcodeTypeLauncher = registerForActivityResult(PickBarcodeTypeActivity.PICK_BARCODE_TYPE, barcodeFormat -> {
            if (barcodeFormat == null && this.chosenBarcodeType == null) {
                finish(); //cancelled
            } else {
                this.chosenBarcodeType = barcodeFormat;
                checkStartChildActivity();
            }
        });

        importCardCodeLauncher = registerForActivityResult(ImportMethodJunctionActivity.IMPORT_CARD_CODE, cardCode -> {
            if (cardCode == null) {
                //cancelled
                if (this.cardCode == null) {
                    finish();
                }
            } else {
                String oldCardCode = this.cardCode;
                this.cardCode = cardCode;
                if (oldCardCode != null && cardName.contains(oldCardCode)) {
                    ignoreTextChanges = true;
                    ui.etCardName.setText(cardName.replace(oldCardCode, cardCode));
                    ignoreTextChanges = false;
                }
                updateCardPreviewUI();
                checkStartChildActivity();
            }
        });

        initUI();

        if (isFirstLaunch) {
            isFirstLaunch = false;
            pickBarcodeTypeLauncher.launch(null);
        } else {
            updateCardPreviewUI();
        }
    }

    private void initUI() {
        ViewUtils.enableRipple(ui.cardBarcodePreview.getRoot());
        ui.cardBarcodePreview.getRoot().setOnClickListener(v -> importCardCodeLauncher.launch(chosenBarcodeType));

        ui.etProviderName.setText(providerName);
        ui.etCardName.setText(cardName);

        ui.etProviderName.addTextChangedListener((SimpleTextWatcher) newText -> {
            String newProviderName = newText.toString().trim();
            providerName = newProviderName;
            if (!cardNumberIsCustomized) {
                String defaultCardName = PersonalCard.formatDefaultName(newProviderName, cardCode);
                ignoreTextChanges = true;
                ui.etCardName.setText(defaultCardName);
                ignoreTextChanges = false;
            }
            updateSaveButtonEnabled();
        });
        ui.etCardName.addTextChangedListener((SimpleTextWatcher) newText -> {
            String newCardName = newText.toString().trim();
            cardName = newCardName;
            if (!ignoreTextChanges) {
                cardNumberIsCustomized = !TextUtils.isEmpty(newCardName);
            }
            updateSaveButtonEnabled();
        });

        updateColorPreviewUI();
        ui.btnChangeColor.setOnClickListener(v -> showColorPickerDialog());

        ui.btnSaveCard.setOnClickListener(v -> {
            PersonalCard.CustomCardProperties customProps = new PersonalCard.CustomCardProperties(
                    providerName,
                    chosenBarcodeType,
                    cardColor
            );
            PersonalCard card = new PersonalCard(
                    PersonalCardStore.CARD_ID_TEMPORARY,
                    !cardNumberIsCustomized || TextUtils.isEmpty(cardName) ? null : cardName,
                    customProps,
                    cardCode
            );
            finishWithResult(card);
        });
        updateSaveButtonEnabled();
    }

    private void showColorPickerDialog() {
        ColorPickerView picker = new ColorPickerView(this);
        picker.setColor(cardColor);
        picker.showAlpha(false);
        picker.showHex(false);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.color_picker_title)
                .setView(picker)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    cardColor = picker.getColor();
                    updateColorPreviewUI();
                })
                .show();
    }

    private void updateSaveButtonEnabled() {
        ui.btnSaveCard.setEnabled(!TextUtils.isEmpty(ui.etProviderName.getText()));
    }

    private void updateColorPreviewUI() {
        ui.ivColorPreview.setImageTintList(ColorStateList.valueOf(cardColor));
    }

    private void updateCardPreviewUI() {
        if (cardCode == null || chosenBarcodeType == null) {
            return;
        }
        ui.cardBarcodePreview.tvBarcodeTypeName.setText(cardCode);
        ui.cardBarcodePreview.tvBarcodeTypeName.setTextSize(16f);
        ui.cardBarcodePreview.ivBarcode.setMaxHeight(Integer.MAX_VALUE);
        ui.cardBarcodePreview.ivBarcode.setImageDrawable(new ZxingCodeDrawable(
                getResources(),
                new ZxingCodeDrawable.Options()
                        .setFormat(chosenBarcodeType)
                        .setData(cardCode)
        ));
    }

    private void checkStartChildActivity() {
        if (!isFirstLaunch && !isStartingChildActivity) {
            isStartingChildActivity = true;
            if (chosenBarcodeType == null) {
                pickBarcodeTypeLauncher.launch(null);
            } else if (cardCode == null) {
                importCardCodeLauncher.launch(chosenBarcodeType);
            } else {
                isStartingChildActivity = false;
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_FIRST_LAUNCH, isFirstLaunch);
        outState.putSerializable(STATE_CHOSEN_BARCODE_TYPE, chosenBarcodeType);
        outState.putString(STATE_CARD_CODE, cardCode);
        outState.putString(STATE_CARD_PROVIDER_NAME, providerName);
        outState.putString(STATE_CARD_NAME, cardName);
        outState.putInt(STATE_CARD_COLOR, cardColor);
        outState.putBoolean(STATE_CARD_NUMBER_CUSTOMIZED, cardNumberIsCustomized);
    }

    private void finishWithResult(PersonalCard card) {
        setResult(RESULT_OK, new Intent().putExtra(RESULT_EXTRA_CARD, card));
        finish();
    }
}
