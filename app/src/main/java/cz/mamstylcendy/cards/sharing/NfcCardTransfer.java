package cz.mamstylcendy.cards.sharing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class NfcCardTransfer {

    public static final int CURRENT_PROTOCOL_VERSION = 1;

    public static ServiceRequest readServiceRequest(byte[] apdu) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(apdu))) {
            int version = in.readInt();
            int txId = in.readInt();
            byte[] payload = new byte[in.available()];
            in.readFully(payload);
            return new ServiceRequest(version, txId, payload);
        }
    }

    public static byte[] packServiceRequest(ServiceRequest request) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DataOutputStream dataOut = new DataOutputStream(out)) {
            dataOut.writeInt(request.protocolVersion());
            dataOut.writeInt(request.txId());
            dataOut.write(request.payload());
        }
        return out.toByteArray();
    }

    public static byte[] createResponseHeader(int protocolVersion, int txId) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DataOutputStream dataOut = new DataOutputStream(out)) {
            dataOut.writeInt(protocolVersion);
            dataOut.writeInt(txId);
        }
        return out.toByteArray();
    }

    private static void throwExceptionByStatus(byte[] packet) throws IOException {
        CardTransfer.ErrorCode errorCode = null;
        if (NfcCommon.checkStatusCode(packet, NfcCommon.SW_WRONG_CHECKSUM)) {
            errorCode = CardTransfer.ErrorCode.WRONG_CHECKSUM;
        } else if (NfcCommon.checkStatusCode(packet, NfcCommon.SW_SECURITY_CONDITION_NOT_SATISFIED)) {
            errorCode = CardTransfer.ErrorCode.SECURITY_VIOLATION;
        } else if (NfcCommon.checkStatusCode(packet, NfcCommon.SW_MORE_DATA_EXPECTED)) {
            errorCode = CardTransfer.ErrorCode.NOT_ENOUGH_DATA;
        } else if (NfcCommon.checkStatusCode(packet, NfcCommon.SW_COMMAND_NOT_ALLOWED)) {
            errorCode = CardTransfer.ErrorCode.UNKNOWN_COMMAND;
        } else if (NfcCommon.checkStatusCode(packet, NfcCommon.SW_INVALID_DATA)) {
            errorCode = CardTransfer.ErrorCode.INVALID_DATA;
        }
        if (errorCode != null) {
            throw new CardTransfer.CardTransferException(errorCode);
        } else {
            throw new IOException("Unexpected status code: " + NfcCommon.encodeHex(Arrays.copyOf(packet, Math.min(packet.length, 2))));
        }
    }

    public static ServiceResponse unpackResponse(byte[] packet) throws IOException {
        if (!NfcCommon.checkStatusCode(packet, NfcCommon.SW_OK)) {
            throwExceptionByStatus(packet);
        }
        ByteArrayInputStream in = new ByteArrayInputStream(packet);
        try (DataInputStream dataIn = new DataInputStream(in)) {
            int protocolVersion = dataIn.readInt();
            if (protocolVersion > CURRENT_PROTOCOL_VERSION) {
                throw new CardTransfer.CardTransferException(CardTransfer.ErrorCode.UNSUPPORTED_PROTOCOL_VERSION);
            }
            int txId = dataIn.readInt();
            byte[] payload = new byte[dataIn.available() - NfcCommon.SW_OK.length];
            dataIn.readFully(payload);
            return new ServiceResponse(txId, payload);
        } catch (IOException e) {
            throw new IOException("Failed to read response packet", e);
        }
    }

    public static record ServiceRequest(int protocolVersion, int txId, byte[] payload) {

    }

    public static record ServiceResponse(int txId, byte[] payload) {

    }
}
