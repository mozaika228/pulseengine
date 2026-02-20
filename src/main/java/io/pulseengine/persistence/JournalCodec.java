package io.pulseengine.persistence;

import io.pulseengine.core.OrderType;
import io.pulseengine.core.Side;
import io.pulseengine.core.TimeInForce;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;

final class JournalCodec {
    static final int RECORD_LENGTH = 96;
    static final int MAGIC = 0x50454A31; // PEJ1

    static final byte TYPE_NEW = 1;
    static final byte TYPE_CANCEL = 2;

    private static final int OFF_RECORD_TYPE = 0;
    private static final int OFF_SIDE = 1;
    private static final int OFF_ORDER_TYPE = 2;
    private static final int OFF_TIF = 3;

    private static final int OFF_MAGIC = 4;
    private static final int OFF_TS = 8;
    private static final int OFF_ORDER_ID = 16;
    private static final int OFF_TRADER_ID = 24;
    private static final int OFF_PRICE = 32;
    private static final int OFF_STOP_PRICE = 40;
    private static final int OFF_QUANTITY = 48;
    private static final int OFF_PEAK = 56;
    private static final int OFF_CANCEL_ORDER_ID = 64;

    private static final int OFF_CHECKSUM = 88;

    private JournalCodec() {
    }

    static void initializeRecord(ByteBuffer buffer) {
        buffer.putInt(OFF_MAGIC, MAGIC);
    }

    static void writeChecksum(byte[] record) {
        int checksum = computeChecksum(record);
        ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN).putInt(OFF_CHECKSUM, checksum);
    }

    static boolean validateChecksum(byte[] record) {
        int stored = ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN).getInt(OFF_CHECKSUM);
        return stored == computeChecksum(record);
    }

    static boolean validateMagic(byte[] record) {
        int magic = ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN).getInt(OFF_MAGIC);
        return magic == MAGIC;
    }

    private static int computeChecksum(byte[] record) {
        CRC32 crc32 = new CRC32();
        crc32.update(record, 0, OFF_CHECKSUM);
        crc32.update(record, OFF_CHECKSUM + 4, RECORD_LENGTH - (OFF_CHECKSUM + 4));
        return (int) crc32.getValue();
    }

    static byte mapSide(Side side) {
        return side == Side.SELL ? (byte) 1 : (byte) 0;
    }

    static Side unmapSide(byte side) {
        return side == 1 ? Side.SELL : Side.BUY;
    }

    static byte mapOrderType(OrderType type) {
        return switch (type) {
            case LIMIT -> 1;
            case MARKET -> 2;
            case STOP_MARKET -> 3;
        };
    }

    static OrderType unmapOrderType(byte type) {
        return switch (type) {
            case 2 -> OrderType.MARKET;
            case 3 -> OrderType.STOP_MARKET;
            default -> OrderType.LIMIT;
        };
    }

    static byte mapTif(TimeInForce tif) {
        return switch (tif) {
            case IOC -> 1;
            case FOK -> 2;
            default -> 0;
        };
    }

    static TimeInForce unmapTif(byte tif) {
        return switch (tif) {
            case 1 -> TimeInForce.IOC;
            case 2 -> TimeInForce.FOK;
            default -> TimeInForce.GTC;
        };
    }

    static int offRecordType() {
        return OFF_RECORD_TYPE;
    }

    static int offSide() {
        return OFF_SIDE;
    }

    static int offOrderType() {
        return OFF_ORDER_TYPE;
    }

    static int offTif() {
        return OFF_TIF;
    }

    static int offTs() {
        return OFF_TS;
    }

    static int offOrderId() {
        return OFF_ORDER_ID;
    }

    static int offTraderId() {
        return OFF_TRADER_ID;
    }

    static int offPrice() {
        return OFF_PRICE;
    }

    static int offStopPrice() {
        return OFF_STOP_PRICE;
    }

    static int offQuantity() {
        return OFF_QUANTITY;
    }

    static int offPeak() {
        return OFF_PEAK;
    }

    static int offCancelOrderId() {
        return OFF_CANCEL_ORDER_ID;
    }
}
