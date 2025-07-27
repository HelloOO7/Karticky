package cz.nocard.android.ui.activity;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.core.content.IntentCompat;

import java.util.List;

import javax.inject.Inject;

import cz.nocard.android.BuildConfig;
import cz.nocard.android.NoCardApplication;
import cz.nocard.android.R;
import cz.nocard.android.sharing.CardTransfer;
import cz.nocard.android.sharing.NfcCardTransfer;
import cz.nocard.android.sharing.NfcCommon;
import cz.nocard.android.sharing.NfcExportServiceState;
import cz.nocard.android.data.PersonalCard;
import cz.nocard.android.data.PersonalCardStore;
import cz.nocard.android.databinding.ActivityNfcImportBinding;
import cz.spojenka.android.util.AsyncUtils;
import cz.spojenka.android.util.ViewUtils;

public class NfcImportActivity extends NfcActivityBase {

    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static final String TAG = NfcImportActivity.class.getSimpleName();

    private ActivityNfcImportBinding ui;

    private int transactionId = 1;
    private boolean isListening = false;
    private boolean inTransaction = false;
    private CardTransfer transfer;

    @Inject
    NfcExportServiceState serviceState;
    @Inject
    PersonalCardStore personalCardStore;

    private String nfcAid;
    private int defaultTextColor;
    private boolean showingAnimatedIcon = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        nfcAid = getString(R.string.nfc_aid);
        NoCardApplication.getInstance().getApplicationComponent().inject(this);
        setContentView(ui.getRoot());

        transfer = new CardTransfer();

        ui.btnFinish.setOnClickListener(v -> finish());
        ui.btnEnableNfc.setOnClickListener(v -> callNfcSettings());
        ui.btnImportMore.setOnClickListener(v -> startListening());

        addOnNewIntentListener(this::handleForegroundDispatch);
    }

    @Override
    protected void onCreateUI() {
        super.onCreateUI();
        EdgeToEdge.enable(this);
        ui = ActivityNfcImportBinding.inflate(getLayoutInflater());
        defaultTextColor = ui.tvStatusText.getCurrentTextColor();
    }

    @Override
    protected void onNfcAdapterInitialized() {
        startListening();
    }

    @Override
    protected void onNfcAdapterEnabled() {
        if (isListening) {
            setReceiveStatusInfo();
        }
    }

    @Override
    protected void onNfcAdapterDisabled() {
        if (isListening) {
            setNfcOffStatusInfo();
        }
    }

    @Override
    protected void onNfcNotSupported() {
        setNoNfcStatusInfo();
    }

    private void setRegularStatusText(String text) {
        ui.tvStatusText.setText(text);
        ui.tvStatusText.setTextColor(defaultTextColor);
    }

    private void setRegularStatusText(int resId) {
        setRegularStatusText(getString(resId));
    }

    private void showNonAnimatedIcon(int resId) {
        showingAnimatedIcon = false;
        ui.ivStatusImage.setImageResource(resId);
    }

    private void setReceiveStatusInfo() {
        if (!showingAnimatedIcon) {
            ViewUtils.setAnimatedDrawableAndStart(ui.ivStatusImage, R.drawable.ic_contactless_animated);
            showingAnimatedIcon = true;
        }
        setRegularStatusText(R.string.nfc_listening);
        ui.llButtons.setVisibility(View.GONE);
        ui.btnEnableNfc.setVisibility(View.GONE);
    }

    private void setNfcOffStatusInfo() {
        showNonAnimatedIcon(R.drawable.ic_contactless_off_48px);
        setRegularStatusText(R.string.nfc_enable_prompt);
        ui.llButtons.setVisibility(View.VISIBLE);
        ui.btnEnableNfc.setVisibility(View.VISIBLE);
        ui.btnImportMore.setVisibility(View.GONE);
        ui.btnFinish.setVisibility(View.GONE);
    }

    private void setNoNfcStatusInfo() {
        showNonAnimatedIcon(R.drawable.ic_contactless_off_48px);
        setRegularStatusText(R.string.nfc_not_supported);
        ui.llButtons.setVisibility(View.GONE);
    }

    private void startListening() {
        if (adapter == null) {
            return;
        }
        Log.d(TAG, "Starting NFC listening");
        isListening = true;
        if (adapter.isEnabled()) {
            setReceiveStatusInfo();
        } else {
            setNfcOffStatusInfo();
        }
    }

    private synchronized void newTransactionId() {
        ++transactionId;
    }

    private void handleForegroundDispatch(Intent intent) {
        Log.d(TAG, "Handling foreground dispatch with intent: " + intent);
        if (!isListening || inTransaction) {
            Log.d(TAG, "Ignoring foreground dispatch, isListening " + isListening + " inTransaction " + inTransaction);
            return;
        }
        inTransaction = true;
        Tag tag = IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag.class);
        if (tag == null) {
            return;
        }

        AsyncUtils.supplyAsync(() -> {
                    try (IsoDep isoDep = IsoDep.get(tag)) {
                        isoDep.connect();

                        byte[] selectResponse = isoDep.transceive(NfcCommon.selectApdu(nfcAid));
                        if (!NfcCommon.checkStatusCode(selectResponse, NfcCommon.SW_OK)) {
                            Log.w(TAG, "Failed to select AID " + nfcAid + ", got response " + NfcCommon.encodeHex(selectResponse));
                            throw new CardTransfer.CardTransferException(CardTransfer.ErrorCode.UNKNOWN_COMMAND);
                        }

                        byte[] responseData = isoDep.transceive(NfcCardTransfer.packServiceRequest(new NfcCardTransfer.ServiceRequest(
                                NfcCardTransfer.CURRENT_PROTOCOL_VERSION,
                                transactionId,
                                transfer.newRequest(CardTransfer.COMMAND_GET_PERSONAL_CARDS)
                        )));

                        if (DEBUG) {
                            Log.d(TAG, "Received response: " + NfcCommon.encodeHex(responseData));
                        }

                        List<PersonalCard> cards = transfer.receivePersonalCardPacket(NfcCardTransfer.unpackResponse(responseData).payload());
                        personalCardStore.merge(cards);
                        return cards;
                    }
                }).handleAsync((personalCards, throwable) -> {
                    if (throwable != null) {
                        Log.e(TAG, "Error during import", throwable);
                        showError(throwable);
                    } else {
                        isListening = false;
                        showSuccess(personalCards);
                    }
                    inTransaction = false;
                    newTransactionId();
                    return null;
                }, AsyncUtils.getLifecycleExecutor(this));
    }

    private void showError(Throwable error) {
        boolean isRecoverable = false;
        String statusText;
        if (error instanceof CardTransfer.CardTransferException cte) {
            statusText = switch (cte.getErrorCode()) {
                case SECURITY_VIOLATION -> {
                    isRecoverable = true;
                    yield getString(R.string.nfc_security_violation);
                }
                case UNSUPPORTED_PROTOCOL_VERSION -> {
                    isRecoverable = true;
                    yield getString(R.string.nfc_unsupported_protocol);
                }
                default -> cte.getErrorCode().toString();
            };
        } else if (error instanceof TagLostException) {
            statusText = getString(R.string.nfc_tag_lost);
            isRecoverable = true;
        } else {
            statusText = error.getLocalizedMessage();
        }
        if (isRecoverable) {
            ui.tvStatusText.setText(statusText);
        } else {
            ui.tvStatusText.setText(getString(R.string.nfc_read_error, statusText));
            ui.tvStatusText.setTextColor(getColor(R.color.spanish_red));
        }
    }

    private void showSuccess(List<PersonalCard> importedCards) {
        showNonAnimatedIcon(R.drawable.ic_cards_added_48px);
        setRegularStatusText(getResources().getQuantityString(R.plurals.cards_received, importedCards.size(), importedCards.size()));
        ui.llButtons.setVisibility(View.VISIBLE);
        ui.btnFinish.setVisibility(View.VISIBLE);
        ui.btnImportMore.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResumeNfc() {
        super.onResumeNfc();
        serviceState.setEnabled(false); //disable HCE when receiving
        adapter.enableForegroundDispatch(
                this,
                PendingIntent.getActivity(
                        this,
                        0,
                        new Intent(this, getClass())
                                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_MUTABLE
                ),
                new IntentFilter[]{new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)},
                new String[][]{new String[]{IsoDep.class.getName()}}
        );
    }

    @Override
    protected void onPauseNfc() {
        super.onPauseNfc();
        adapter.disableForegroundDispatch(this);
    }
}
