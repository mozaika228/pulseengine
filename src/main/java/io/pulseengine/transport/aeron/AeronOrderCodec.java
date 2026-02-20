package io.pulseengine.transport.aeron;

import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class AeronOrderCodec {
    private static final int OFF_TYPE = 0;
    private static final int OFF_SIDE = 1;
    private static final int OFF_TIF = 2;

    private static final int OFF_ORDER_ID = 8;
    private static final int OFF_TRADER_ID = 16;
    private static final int OFF_PRICE = 24;
    private static final int OFF_STOP_PRICE = 32;
    private static final int OFF_QUANTITY = 40;
    private static final int OFF_PEAK = 48;
    private static final int OFF_CANCEL_ORDER_ID = 56;

    private AeronOrderCodec() {
    }

    static UnsafeBuffer newBuffer() {
        UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(AeronOrderCommand.FIXED_LENGTH));
        buffer.byteBuffer().order(ByteOrder.LITTLE_ENDIAN);
        return buffer;
    }

    static void encode(
        UnsafeBuffer buffer,
        byte type,
        byte side,
        byte tif,
        long orderId,
        long traderId,
        long price,
        long stopPrice,
        long quantity,
        long peak,
        long cancelOrderId
    ) {
        buffer.putByte(OFF_TYPE, type);
        buffer.putByte(OFF_SIDE, side);
        buffer.putByte(OFF_TIF, tif);

        buffer.putLong(OFF_ORDER_ID, orderId, ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(OFF_TRADER_ID, traderId, ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(OFF_PRICE, price, ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(OFF_STOP_PRICE, stopPrice, ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(OFF_QUANTITY, quantity, ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(OFF_PEAK, peak, ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(OFF_CANCEL_ORDER_ID, cancelOrderId, ByteOrder.LITTLE_ENDIAN);
    }

    static byte type(UnsafeBuffer buffer, int offset) {
        return buffer.getByte(offset + OFF_TYPE);
    }

    static byte side(UnsafeBuffer buffer, int offset) {
        return buffer.getByte(offset + OFF_SIDE);
    }

    static byte tif(UnsafeBuffer buffer, int offset) {
        return buffer.getByte(offset + OFF_TIF);
    }

    static long orderId(UnsafeBuffer buffer, int offset) {
        return buffer.getLong(offset + OFF_ORDER_ID, ByteOrder.LITTLE_ENDIAN);
    }

    static long traderId(UnsafeBuffer buffer, int offset) {
        return buffer.getLong(offset + OFF_TRADER_ID, ByteOrder.LITTLE_ENDIAN);
    }

    static long price(UnsafeBuffer buffer, int offset) {
        return buffer.getLong(offset + OFF_PRICE, ByteOrder.LITTLE_ENDIAN);
    }

    static long stopPrice(UnsafeBuffer buffer, int offset) {
        return buffer.getLong(offset + OFF_STOP_PRICE, ByteOrder.LITTLE_ENDIAN);
    }

    static long quantity(UnsafeBuffer buffer, int offset) {
        return buffer.getLong(offset + OFF_QUANTITY, ByteOrder.LITTLE_ENDIAN);
    }

    static long peak(UnsafeBuffer buffer, int offset) {
        return buffer.getLong(offset + OFF_PEAK, ByteOrder.LITTLE_ENDIAN);
    }

    static long cancelOrderId(UnsafeBuffer buffer, int offset) {
        return buffer.getLong(offset + OFF_CANCEL_ORDER_ID, ByteOrder.LITTLE_ENDIAN);
    }
}
