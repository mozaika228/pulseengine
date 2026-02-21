package io.pulseengine.jni;

public final class NativeOrderBinaryLayout {
    public static final int VERSION = 1;

    public static final int FIXED_LENGTH = 80;

    public static final int OFF_TYPE = 0;
    public static final int OFF_SIDE = 1;
    public static final int OFF_TIF = 2;
    public static final int OFF_ORDER_ID = 8;
    public static final int OFF_TRADER_ID = 16;
    public static final int OFF_PRICE = 24;
    public static final int OFF_STOP_PRICE = 32;
    public static final int OFF_QUANTITY = 40;
    public static final int OFF_PEAK = 48;
    public static final int OFF_CANCEL_ORDER_ID = 56;

    public static final int HASH = computeHash();

    private NativeOrderBinaryLayout() {
    }

    private static int computeHash() {
        int h = 17;
        h = 31 * h + VERSION;
        h = 31 * h + FIXED_LENGTH;
        h = 31 * h + OFF_TYPE;
        h = 31 * h + OFF_SIDE;
        h = 31 * h + OFF_TIF;
        h = 31 * h + OFF_ORDER_ID;
        h = 31 * h + OFF_TRADER_ID;
        h = 31 * h + OFF_PRICE;
        h = 31 * h + OFF_STOP_PRICE;
        h = 31 * h + OFF_QUANTITY;
        h = 31 * h + OFF_PEAK;
        h = 31 * h + OFF_CANCEL_ORDER_ID;
        return h;
    }
}
