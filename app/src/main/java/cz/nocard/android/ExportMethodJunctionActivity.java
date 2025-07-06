package cz.nocard.android;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ShareCompat;

import java.io.IOException;

import cz.nocard.android.databinding.ActivityExportMethodChoiceBinding;

public class ExportMethodJunctionActivity extends AppCompatActivity {

    private ActivityExportMethodChoiceBinding ui;
    private ExportActivityCommon common;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        common = new ExportActivityCommon(this);
        ui = ActivityExportMethodChoiceBinding.inflate(getLayoutInflater());
        setContentView(ui.getRoot());

        boolean hasNFC = NfcCommon.isSupported(this);
        ui.btnShareNFC.setEnabled(hasNFC);

        if (hasNFC) {
            ui.btnShareNFC.setOnClickListener(v -> finishAndStart(common.forward(NfcExportActivity.class)));
        } else {
            ui.btnShareNFC.setOnClickListener(v -> CommonDialogs.newInfoDialog(this, R.string.unsupported_operation, R.string.nfc_not_supported).show());
        }

        ui.btnShareLink.setOnClickListener(v -> callLinkSharing());
    }

    private void finishAndStart(Intent intent) {
        finish();
        startActivity(intent);
    }

    public static Intent newIntent(Context context, int[] cardIDs) {
        return ExportActivityCommon.newIntent(context, ExportMethodJunctionActivity.class, cardIDs);
    }

    private void callLinkSharing() {
        try {
            CardTransfer transfer = LinkCardTransfer.newCardTransfer();

            byte[] packet = transfer.createPersonalCardPacket(common.getCardsToExport());

            new ShareCompat.IntentBuilder(this)
                    .setType("text/url")
                    .setText(LinkCardTransfer.newDeepLink(LinkCardTransfer.LinkType.APP_CARD_PACKET, packet).toString())
                    .startChooser();
        } catch (IOException e) {
            Log.e("LinkCardExport", "Error creating packet", e);
            CommonDialogs.newInfoDialog(this, R.string.error, R.string.card_link_creation_error).show();
        }
    }
}
