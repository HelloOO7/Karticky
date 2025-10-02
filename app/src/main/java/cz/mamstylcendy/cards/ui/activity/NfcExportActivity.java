package cz.mamstylcendy.cards.ui.activity;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.nfc.cardemulation.CardEmulation;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;

import javax.inject.Inject;

import cz.mamstylcendy.cards.CardsApplication;
import cz.mamstylcendy.cards.R;
import cz.mamstylcendy.cards.sharing.NfcExportHceService;
import cz.mamstylcendy.cards.sharing.NfcExportServiceState;
import cz.mamstylcendy.cards.databinding.ActivityNfcExportBinding;
import cz.spojenka.android.util.ViewUtils;

public class NfcExportActivity extends NfcActivityBase {

    private ActivityNfcExportBinding ui;
    private ExportActivityCommon common;

    private CardEmulation cardEmulation;

    @Inject
    NfcExportServiceState serviceState;

    private boolean showingAnimatedIcon = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CardsApplication.getInstance().getApplicationComponent().inject(this);
        setContentView(ui.getRoot());
        common = new ExportActivityCommon(this);

        ui.btnEnableNfc.setOnClickListener(v -> callNfcSettings());

        serviceState.setExportCardFilter(common.getCardsToExport());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        serviceState.clearExportCardFilter();
    }

    @Override
    protected void onCreateUI() {
        super.onCreateUI();
        EdgeToEdge.enable(this);
        ui = ActivityNfcExportBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNfcAdapterInitialized() {
        cardEmulation = CardEmulation.getInstance(adapter);
        if (adapter.isEnabled()) {
            setHceOnStatusInfo();
        } else {
            setNfcOffStatusInfo();
        }
    }

    private void setNfcOffStatusInfo() {
        ui.ivStatusImage.setImageResource(R.drawable.ic_contactless_off_48px);
        showingAnimatedIcon = false;
        ui.tvStatusText.setText(R.string.nfc_enable_prompt);
        ui.btnEnableNfc.setVisibility(View.VISIBLE);
    }

    private void setHceOnStatusInfo() {
        if (!showingAnimatedIcon) {
            ViewUtils.setAnimatedDrawableAndStart(ui.ivStatusImage, R.drawable.ic_hce_share_animated);
            showingAnimatedIcon = true;
        }
        ui.tvStatusText.setText(R.string.nfc_sharing);
        ui.btnEnableNfc.setVisibility(View.GONE);
    }

    @Override
    protected void onNfcNotSupported() {
        ui.ivStatusImage.setImageResource(R.drawable.ic_contactless_off_48px);
        showingAnimatedIcon = false;
        ui.tvStatusText.setText(R.string.nfc_not_supported);
    }

    @Override
    protected void onNfcAdapterEnabled() {
        setHceOnStatusInfo();
    }

    @Override
    protected void onNfcAdapterDisabled() {
        setNfcOffStatusInfo();
    }

    @Override
    protected void onResumeNfc() {
        super.onResumeNfc();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        serviceState.setEnabled(true);
        cardEmulation.setPreferredService(this, new ComponentName(this, NfcExportHceService.class));
        //add foreground dispatch to suppress system NFC tag discovery processes when HCEing
        adapter.enableForegroundDispatch(
                this,
                PendingIntent.getActivity(
                        this,
                        0,
                        new Intent(this, getClass())
                                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_MUTABLE
                ),
                null,
                new String[][]{new String[]{IsoDep.class.getName()}}
        );
    }

    @Override
    protected void onPauseNfc() {
        super.onPauseNfc();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        serviceState.setEnabled(false);
        cardEmulation.unsetPreferredService(this);
        adapter.disableForegroundDispatch(this);
    }
}
