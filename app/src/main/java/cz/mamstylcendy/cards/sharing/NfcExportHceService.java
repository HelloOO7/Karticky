package cz.mamstylcendy.cards.sharing;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.IOException;

import javax.inject.Inject;

import cz.mamstylcendy.cards.BuildConfig;
import cz.mamstylcendy.cards.CardsApplication;
import cz.mamstylcendy.cards.data.PersonalCardStore;

public class NfcExportHceService extends HostApduService {

    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static final String LOG_TAG = NfcExportHceService.class.getName();

    @Inject
    NfcExportServiceState serviceState;
    @Inject
    PersonalCardStore personalCardStore;

    @Override
    public void onCreate() {
        super.onCreate();
        CardsApplication.getInstance().getApplicationComponent().inject(this);
    }

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        if (NfcCommon.checkCommand(commandApdu, NfcCommon.CMD_SELECT_APDU)) {
            return NfcCommon.SW_OK;
        }
        if (!serviceState.isEnabled() || !CardsApplication.isAppInForeground()) {
            Log.w(LOG_TAG, "Service is not enabled by foreground app, access is denied.");
            return NfcCommon.SW_SECURITY_CONDITION_NOT_SATISFIED;
        }
        if (commandApdu.length < 5) {
            Log.w(LOG_TAG, "Command APDU is too short: " + commandApdu.length);
            return NfcCommon.SW_MORE_DATA_EXPECTED;
        }

        if (DEBUG) {
            Log.d(LOG_TAG, "Received command APDU: " + NfcCommon.encodeHex(commandApdu));
        }

        NfcCardTransfer.ServiceRequest nfcRequest;

        try {
            nfcRequest = NfcCardTransfer.readServiceRequest(commandApdu);
        } catch (IOException ex) {
            Log.e(LOG_TAG, "Error reading command APDU", ex);
            return NfcCommon.SW_COMMAND_ABORTED;
        }

        if (DEBUG) {
            Log.d(LOG_TAG, "Parsed request: " + nfcRequest);
        }

        CardTransfer transfer = newNfcCardTransfer(nfcRequest.protocolVersion(), nfcRequest.txId());

        try {
            byte[] response = transfer.respondToRequest(nfcRequest.payload(), (command, requestIn) -> switch (command) {
                case CardTransfer.COMMAND_GET_PERSONAL_CARDS ->
                        transfer.createPersonalCardPacket(serviceState.getCardsForExport(personalCardStore));
                default ->
                        throw new CardTransfer.CardTransferException(CardTransfer.ErrorCode.UNKNOWN_COMMAND);
            });
            if (DEBUG) {
                Log.d(LOG_TAG, "Sending response " + NfcCommon.encodeHex(response));
            }
            return response;
        } catch (CardTransfer.CardTransferException xferException) {
            Log.e(LOG_TAG, "Error processing command", xferException);
            return switch (xferException.getErrorCode()) {
                case INVALID_MAGIC -> NfcCommon.SW_CLASS_NOT_SUPPORTED;
                case UNKNOWN_COMMAND -> NfcCommon.SW_COMMAND_NOT_ALLOWED;
                case NOT_ENOUGH_DATA -> NfcCommon.SW_MORE_DATA_EXPECTED;
                case INVALID_DATA, UNSUPPORTED_PROTOCOL_VERSION -> NfcCommon.SW_INVALID_DATA;
                case WRONG_CHECKSUM -> NfcCommon.SW_WRONG_CHECKSUM;
                case SECURITY_VIOLATION -> NfcCommon.SW_SECURITY_CONDITION_NOT_SATISFIED;
            };
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error writing response", e);
            return NfcCommon.SW_COMMAND_ABORTED;
        }
    }

    @Override
    public void onDeactivated(int reason) {

    }

    private CardTransfer newNfcCardTransfer(int clientProtocolVersion, int transactionId) {
        int usedProtocolVersion = Math.min(clientProtocolVersion, NfcCardTransfer.CURRENT_PROTOCOL_VERSION);

        return new CardTransfer() {

            @Override
            protected void writeCustomPacketPrologue(DataOutputStream out) throws IOException {
                super.writeCustomPacketPrologue(out);
                out.write(NfcCardTransfer.createResponseHeader(usedProtocolVersion, transactionId));
            }

            @Override
            protected void writeCustomPacketEpilogue(DataOutputStream out) throws IOException {
                super.writeCustomPacketEpilogue(out);
                out.write(NfcCommon.SW_OK);
            }
        };
    }
}
