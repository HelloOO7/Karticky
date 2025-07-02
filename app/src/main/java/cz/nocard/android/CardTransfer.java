package cz.nocard.android;

import com.google.zxing.BarcodeFormat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

import javax.inject.Inject;

public class CardTransfer {

    public static final int MAGIC_NUMBER = 0xCA4DDA7A;
    public static final int CURRENT_FORMAT_VERSION = 1;

    public static final int COMMAND_GET_PERSONAL_CARDS = 0x11;

    private int negotiatedFormatVersion = CURRENT_FORMAT_VERSION;
    @Inject
    ConfigManager config;

    public CardTransfer() {
        NoCardApplication.getInstance().getApplicationComponent().inject(this);
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
                out.writeUTF(card.name());
                out.writeUTF(card.cardNumber());
                out.writeUTF(card.provider());
                out.writeBoolean(card.isCustom());
                // target app may not support the providers in source app - create fallback custom
                // properties for that scenario
                PersonalCard.CustomCardProperties customProps = card.customProperties();
                if (customProps == null) {
                    NoCardConfig.ProviderInfo providerInfo = config.getProviderInfoOrNull(card.provider());
                    if (providerInfo != null) {
                        customProps = new PersonalCard.CustomCardProperties(
                                config.getProviderNameOrDefault(card.provider()),
                                providerInfo.format(),
                                providerInfo.brandColor() == null ? 0 : providerInfo.brandColor()
                        );
                    }
                }
                if (customProps != null) {
                    out.writeBoolean(true);
                    out.writeUTF(customProps.providerName());
                    out.writeUTF(customProps.format().name());
                    out.writeInt(customProps.color());
                } else {
                    out.writeBoolean(false);
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
                String name = in.readUTF();
                String cardNumber = in.readUTF();
                String provider = in.readUTF();
                boolean isCustom = in.readBoolean();
                boolean hasCustomProps = in.readBoolean();

                PersonalCard.CustomCardProperties customProps = null;
                if (hasCustomProps) {
                    String providerName = in.readUTF();
                    BarcodeFormat format = BarcodeFormat.valueOf(in.readUTF());
                    int color = in.readInt();
                    customProps = new PersonalCard.CustomCardProperties(providerName, format, color);
                }

                NoCardConfig.ProviderInfo providerInfo = config.getProviderInfoOrNull(provider);
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
                    } else if (hasCustomProps) {
                        //use fallback
                        out.add(new PersonalCard(PersonalCardStore.CARD_ID_TEMPORARY, name, provider, customProps, cardNumber));
                    }
                }
            }

            return out;
        });
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
