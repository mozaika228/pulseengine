package io.pulseengine.core;

public final class RejectCode {
    public static final byte NONE = 0;
    public static final byte INVALID_QTY = 1;
    public static final byte FOK_UNFILLED = 2;
    public static final byte INVALID_ORDER = 3;
    public static final byte UNKNOWN_CANCEL = 4;
    public static final byte RATE_LIMIT = 5;
    public static final byte FAT_FINGER = 6;
    public static final byte POSITION_LIMIT = 7;

    private RejectCode() {
    }
}
