package io.pulseengine.transport.aeron;

public final class AeronOrderCommand {
    public static final int FIXED_LENGTH = 80;

    public static final byte TYPE_LIMIT = 1;
    public static final byte TYPE_MARKET = 2;
    public static final byte TYPE_STOP_MARKET = 3;
    public static final byte TYPE_CANCEL = 4;

    public static final byte SIDE_BUY = 0;
    public static final byte SIDE_SELL = 1;

    public static final byte TIF_GTC = 0;
    public static final byte TIF_IOC = 1;
    public static final byte TIF_FOK = 2;

    private AeronOrderCommand() {
    }
}
