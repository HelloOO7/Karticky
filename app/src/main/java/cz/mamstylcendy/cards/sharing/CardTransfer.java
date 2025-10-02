package cz.mamstylcendy.cards.sharing;

import com.google.zxing.BarcodeFormat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.CRC32;

import javax.inject.Inject;

import cz.mamstylcendy.cards.data.ConfigManager;
import cz.mamstylcendy.cards.CardsApplication;
import cz.mamstylcendy.cards.data.CardsConfig;
import cz.mamstylcendy.cards.data.PersonalCard;
import cz.mamstylcendy.cards.data.PersonalCardStore;

public class CardTransfer {

    public static final int MAGIC_NUMBER = 0xCA4DDA7A;
    public static final int CURRENT_FORMAT_VERSION = 1;

    public static final int COMMAND_GET_PERSONAL_CARDS = 0x11;

    /*
    Barcode format constants
     */
    public static final int BCFCONST_AZTEC = 1;
    public static final int BCFCONST_CODABAR = 2;
    public static final int BCFCONST_CODE_39 = 3;
    public static final int BCFCONST_CODE_93 = 4;
    public static final int BCFCONST_CODE_128 = 5;
    public static final int BCFCONST_DATA_MATRIX = 6;
    public static final int BCFCONST_EAN_8 = 7;
    public static final int BCFCONST_EAN_13 = 8;
    public static final int BCFCONST_ITF = 9;
    public static final int BCFCONST_MAXICODE = 10;
    public static final int BCFCONST_PDF_417 = 11;
    public static final int BCFCONST_QR_CODE = 12;
    public static final int BCFCONST_RSS_14 = 13;
    public static final int BCFCONST_RSS_EXPANDED = 14;
    public static final int BCFCONST_UPC_A = 15;
    public static final int BCFCONST_UPC_E = 16;
    public static final int BCFCONST_UPC_EAN_EXTENSION = 17;

    /*
    Card number format constants
     */
    public static final int CNFCONST_INT = 1;
    public static final int CNFCONST_LONG = 2;
    public static final int CNFCONST_BIGINTEGER = 3;
    public static final int CNFCONST_STRING = 4;

    /*
    Card record flags
     */
    public static final int CRFLAG_HAS_NAME = 1;
    public static final int CRFLAG_IS_CUSTOM = (1 << 1);
    public static final int CRFLAG_HAS_FALLBACK = (1 << 2);

    private int negotiatedFormatVersion = CURRENT_FORMAT_VERSION;
    @Inject
    ConfigManager config;

    public CardTransfer() {
        CardsApplication.getInstance().getApplicationComponent().inject(this);
    }

    protected void writeCustomPacketPrologue(DataOutputStream out) throws IOException {

    }

    protected void writeCustomPacketEpilogue(DataOutputStream out) throws IOException {

    }

    public byte[] newRequest(int command) throws IOException {
        ByteArrayOutputStream byteBuf = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(byteBuf)) {
            out.writeInt(MAGIC_NUMBER);
            out.writeInt(negotiatedFormatVersion);
            out.writeByte(command);
            return byteBuf.toByteArray();
        }
    }

    public byte[] respondToRequest(byte[] payload, CommandResponseHandler handler) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            int magicNumber = in.readInt();
            if (magicNumber != MAGIC_NUMBER) {
                throw new CardTransferException(ErrorCode.INVALID_MAGIC);
            }
            int version = in.readInt();
            this.negotiatedFormatVersion = Math.min(CURRENT_FORMAT_VERSION, version);
            int command = in.readUnsignedByte();

            return handler.respond(command, in);
        } catch (EOFException eof) {
            throw new CardTransferException(ErrorCode.NOT_ENOUGH_DATA, eof);
        }
    }

    private void beginResponse(DataOutputStream out) throws IOException {
        writeCustomPacketPrologue(out);
        out.writeInt(MAGIC_NUMBER);
        out.writeInt(negotiatedFormatVersion);
    }

    private void endResponse(DataOutputStream out) throws IOException {
        writeCustomPacketEpilogue(out);
    }

    private byte[] createResponsePacket(PacketWriter writer) throws IOException {
        ByteArrayOutputStream byteBuf = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(byteBuf)) {
            beginResponse(out);
            int prologueSize = byteBuf.size() - 8; //subtract non-prologue header size
            writer.write(out);
            out.writeInt(calcCRC32(byteBuf.toByteArray(), prologueSize, byteBuf.size() - prologueSize));
            endResponse(out);
        }
        return byteBuf.toByteArray();
    }

    public byte[] createPersonalCardPacket(List<PersonalCard> cards) throws IOException {
        return createResponsePacket(out -> {
            out.writeInt(cards.size());
            for (PersonalCard card : cards) {
                int flags = 0;
                if (card.name() != null) {
                    flags |= CRFLAG_HAS_NAME;
                }
                if (card.isCustom()) {
                    flags |= CRFLAG_IS_CUSTOM;
                }
                // target app may not support the providers in source app - create fallback custom
                // properties for that scenario
                PersonalCard.CustomCardProperties customProps = card.customProperties();
                if (customProps == null) {
                    CardsConfig.ProviderInfo providerInfo = config.getProviderInfoOrNull(card.provider());
                    if (providerInfo != null) {
                        customProps = new PersonalCard.CustomCardProperties(
                                config.getProviderNameOrDefault(card.provider()),
                                providerInfo.format(),
                                providerInfo.brandColor() == null ? 0 : providerInfo.brandColor()
                        );
                    }
                }
                if (customProps != null) {
                    flags |= CRFLAG_HAS_FALLBACK;
                }
                out.writeByte(flags);
                if (card.name() != null) {
                    out.writeUTF(card.name());
                }
                writeCardNumber(out, card.cardNumber());
                out.writeUTF(card.provider());
                if (customProps != null) {
                    out.writeUTF(customProps.providerName());
                    out.writeByte(serializeBarcodeFormat(customProps.format()));
                    out.writeInt(customProps.color());
                }
            }
        });
    }

    private boolean checkPacketCRC(byte[] packet) {
        if (packet.length > 4) {
            int crcStart = packet.length - 4;
            int expected = calcCRC32(packet, 0, crcStart);
            int crc = ByteBuffer.wrap(packet, crcStart, 4).getInt();

            return expected == crc;
        }
        return false;
    }

    private <T> T receivePacketData(byte[] packet, PacketReader<T> reader) throws IOException {
        if (!checkPacketCRC(packet)) {
            throw new CardTransferException(ErrorCode.WRONG_CHECKSUM);
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(packet))) {
            int magicNumber = in.readInt();
            if (magicNumber != MAGIC_NUMBER) {
                throw new CardTransferException(ErrorCode.INVALID_MAGIC);
            }
            int version = in.readInt();
            if (version > CURRENT_FORMAT_VERSION) {
                throw new CardTransferException(ErrorCode.UNSUPPORTED_PROTOCOL_VERSION);
            }
            negotiatedFormatVersion = version;

            return reader.read(in);
        } catch (EOFException eof) {
            throw new CardTransferException(ErrorCode.NOT_ENOUGH_DATA, eof);
        }
    }

    public List<PersonalCard> receivePersonalCardPacket(byte[] packet) throws IOException {
        return receivePacketData(packet, in -> {
            List<PersonalCard> out = new ArrayList<>();

            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                int flags = in.readUnsignedByte();

                boolean hasName = (flags & CRFLAG_HAS_NAME) != 0;
                boolean isCustom = (flags & CRFLAG_IS_CUSTOM) != 0;
                boolean hasFallbackCustomProps = (flags & CRFLAG_HAS_FALLBACK) != 0;

                String name = hasName ? in.readUTF() : null;
                String cardNumber = readCardNumber(in);
                String provider = in.readUTF();

                PersonalCard.CustomCardProperties customProps = null;
                if (isCustom || hasFallbackCustomProps) {
                    String providerName = in.readUTF();
                    BarcodeFormat format = deserializeBarcodeFormat(in.readUnsignedByte());
                    int color = in.readInt();
                    customProps = new PersonalCard.CustomCardProperties(providerName, format, color);
                }

                CardsConfig.ProviderInfo providerInfo = config.getProviderInfoOrNull(provider);
                boolean providerKnown = providerInfo != null;

                if (isCustom && !providerKnown) { //if the provider is known, use as non-custom even if originally custom
                    if (customProps != null) {
                        out.add(new PersonalCard(PersonalCardStore.CARD_ID_TEMPORARY, name, customProps, cardNumber));
                    } else {
                        throw new CardTransferException(ErrorCode.INVALID_DATA);
                    }
                } else {
                    if (providerInfo != null) {
                        out.add(new PersonalCard(PersonalCardStore.CARD_ID_TEMPORARY, name, provider, cardNumber));
                    } else if (hasFallbackCustomProps) {
                        //use fallback
                        out.add(new PersonalCard(PersonalCardStore.CARD_ID_TEMPORARY, name, provider, customProps, cardNumber));
                    }
                }
            }

            return out;
        });
    }

    private static String readCardNumber(DataInputStream in) throws IOException {
        int header = in.readUnsignedByte();
        int type = header & 0b111;
        int numLeadingZeros = (header >> 3) & 0b11111;
        return "0".repeat(numLeadingZeros) + switch (type) {
            case CNFCONST_INT -> Integer.toString(in.readInt());
            case CNFCONST_LONG -> Long.toString(in.readLong());
            case CNFCONST_BIGINTEGER -> {
                int length = in.readUnsignedByte();
                byte[] bytes = new byte[length];
                in.readFully(bytes);
                yield new BigInteger(bytes).toString();
            }
            case CNFCONST_STRING -> in.readUTF();
            default -> throw new CardTransferException(ErrorCode.INVALID_DATA, new IOException("Unknown card number type: " + type));
        };
    }

    private static int countLeadingZeros(String number) {
        int count = 0;
        for (int i = 0; i < number.length(); i++) {
            if (number.charAt(i) == '0') {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private static void writeNumStringHeader(DataOutputStream out, int type, String number) throws IOException {
        int clz = countLeadingZeros(number);
        if (clz >= 32) {
            throw new UnsupportedEncodingException("Too many (>= 32) leading zeros in number: " + number);
        }
        out.writeByte(type | (clz << 3));
    }

    private static void writeCardNumber(DataOutputStream out, String number) throws IOException {
        PacketWriter tryAsInt = out1 -> {
            int val = Integer.parseInt(number);
            writeNumStringHeader(out, CNFCONST_INT, number);
            out1.writeInt(val);
        };
        PacketWriter tryAsLong = out1 -> {
            long val = Long.parseLong(number);
            writeNumStringHeader(out, CNFCONST_LONG, number);
            out1.writeLong(val);
        };
        PacketWriter tryAsBigInteger = out1 -> {
            BigInteger val = new BigInteger(number);
            writeNumStringHeader(out, CNFCONST_BIGINTEGER, number);
            byte[] bytes = val.toByteArray();
            out1.write(bytes.length);
            out1.write(bytes);
        };

        for (PacketWriter attempt : new PacketWriter[]{tryAsInt, tryAsLong, tryAsBigInteger}) {
            try {
                attempt.write(out);
                return;
            } catch (NumberFormatException | UnsupportedOperationException e) {
                // continue to the next attempt
            }
        }

        //if all attempts failed, write raw string, do not count leading zeros (leave as part of the string)
        out.writeByte(CNFCONST_STRING);
        out.writeUTF(number);
    }

    private static int serializeBarcodeFormat(BarcodeFormat barcodeFormat) {
        return Objects.requireNonNull(BARCODE_FORMAT_TO_INT.get(barcodeFormat));
    }

    private static BarcodeFormat deserializeBarcodeFormat(int barcodeFormatInt) {
        BarcodeFormat format = INT_TO_BARCODE_FORMAT.get(barcodeFormatInt);
        if (format == null) {
            throw new IllegalArgumentException("Unknown barcode format: " + barcodeFormatInt);
        }
        return format;
    }

    private static int getSerializedBarcodeFormatInt(BarcodeFormat barcodeFormat) {
        return switch (barcodeFormat) {
            case AZTEC -> BCFCONST_AZTEC;
            case CODABAR -> BCFCONST_CODABAR;
            case CODE_39 -> BCFCONST_CODE_39;
            case CODE_93 -> BCFCONST_CODE_93;
            case CODE_128 -> BCFCONST_CODE_128;
            case DATA_MATRIX -> BCFCONST_DATA_MATRIX;
            case EAN_8 -> BCFCONST_EAN_8;
            case EAN_13 -> BCFCONST_EAN_13;
            case ITF -> BCFCONST_ITF;
            case MAXICODE -> BCFCONST_MAXICODE;
            case PDF_417 -> BCFCONST_PDF_417;
            case QR_CODE -> BCFCONST_QR_CODE;
            case RSS_14 -> BCFCONST_RSS_14;
            case RSS_EXPANDED -> BCFCONST_RSS_EXPANDED;
            case UPC_A -> BCFCONST_UPC_A;
            case UPC_E -> BCFCONST_UPC_E;
            case UPC_EAN_EXTENSION -> BCFCONST_UPC_EAN_EXTENSION;
        };
    }

    private static final Map<BarcodeFormat, Integer> BARCODE_FORMAT_TO_INT = new HashMap<>();
    private static final Map<Integer, BarcodeFormat> INT_TO_BARCODE_FORMAT = new HashMap<>();

    static {
        for (BarcodeFormat bcf : BarcodeFormat.values()) {
            int intVal = getSerializedBarcodeFormatInt(bcf);
            BARCODE_FORMAT_TO_INT.put(bcf, intVal);
            INT_TO_BARCODE_FORMAT.put(intVal, bcf);
        }
    }

    private static int calcCRC32(byte[] buf) {
        return calcCRC32(buf, 0, buf.length);
    }

    private static int calcCRC32(byte[] buf, int off, int len) {
        CRC32 crc = new CRC32();
        crc.update(buf, off, len);
        return (int) crc.getValue();
    }

    public static enum ErrorCode {
        INVALID_MAGIC,
        UNKNOWN_COMMAND,
        SECURITY_VIOLATION,
        NOT_ENOUGH_DATA,
        INVALID_DATA,
        WRONG_CHECKSUM,
        UNSUPPORTED_PROTOCOL_VERSION
    }

    public static class CardTransferException extends IOException {

        private final ErrorCode errorCode;

        public CardTransferException(ErrorCode code) {
            super("Card transfer error: " + code);
            this.errorCode = code;
        }

        public CardTransferException(ErrorCode code, Throwable cause) {
            super("Card transfer error: " + code, cause);
            this.errorCode = code;
        }

        public ErrorCode getErrorCode() {
            return errorCode;
        }
    }

    private static interface PacketWriter {

        public void write(DataOutputStream out) throws IOException;
    }

    private static interface PacketReader<T> {

        public T read(DataInputStream in) throws IOException;
    }

    public static interface CommandResponseHandler {

        public byte[] respond(int command, DataInputStream requestIn) throws IOException;
    }
}
