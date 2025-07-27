package cz.nocard.android.sharing;

import android.content.Context;
import android.nfc.NfcAdapter;

public class NfcCommon {

    public static final byte[] SW_OK = new byte[]{(byte) 0x90, (byte) 0x00};
    public static final byte[] SW_CLASS_NOT_SUPPORTED = new byte[]{(byte) 0x6E, (byte) 0x00};
    public static final byte[] SW_COMMAND_NOT_ALLOWED = new byte[]{(byte) 0x69, (byte) 0x00};
    public static final byte[] SW_INVALID_DATA = new byte[]{(byte) 0x6A, (byte) 0x80};
    public static final byte[] SW_WRONG_CHECKSUM = new byte[]{(byte) 0x66, (byte) 0x02};
    public static final byte[] SW_COMMAND_ABORTED = new byte[]{(byte) 0x6F, (byte) 0x00};
    public static final byte[] SW_SECURITY_CONDITION_NOT_SATISFIED = new byte[]{(byte) 0x69, (byte) 0x82};
    public static final byte[] SW_MORE_DATA_EXPECTED = new byte[]{(byte) 0x63, (byte) 0xF1};

    public static final byte[] CMD_SELECT_APDU = new byte[]{(byte) 0x00, (byte) 0xA4, (byte) 0x04, (byte) 0x00};

    public static boolean isSupported(Context context) {
        return NfcAdapter.getDefaultAdapter(context) != null;
    }

    public static byte[] selectApdu(String aid) {
        return selectApdu(decodeHex(aid));
    }

    public static byte[] selectApdu(byte[] aid) {
        byte[] commandApdu = new byte[6 + aid.length];
        commandApdu[0] = (byte) 0x00;  // CLA
        commandApdu[1] = (byte) 0xA4;  // INS
        commandApdu[2] = (byte) 0x04;  // P1
        commandApdu[3] = (byte) 0x00;  // P2
        commandApdu[4] = (byte) (aid.length & 0xFF);       // Lc
        System.arraycopy(aid, 0, commandApdu, 5, aid.length);
        commandApdu[commandApdu.length - 1] = (byte) 0x00;  // Le
        return commandApdu;
    }

    public static boolean checkBytes(byte[] data, byte[] check, int atOffset) {
        if (data == null || check == null || atOffset < 0 || atOffset + check.length > data.length) {
            return false;
        }
        for (int i = 0; i < check.length; i++) {
            if (data[atOffset + i] != check[i]) {
                return false;
            }
        }
        return true;
    }

    public static boolean checkCommand(byte[] packet, byte[] command) {
        return checkBytes(packet, command, 0);
    }

    public static boolean checkStatusCode(byte[] packet, byte[] code) {
        return checkBytes(packet, code, packet.length - 2);
    }

    public static String encodeHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static byte[] decodeHex(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Invalid hex string");
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int index = i * 2;
            bytes[i] = (byte) ((Character.digit(hex.charAt(index), 16) << 4)
                    + Character.digit(hex.charAt(index + 1), 16));
        }
        return bytes;
    }
}
