package io.pulseengine.md;

public final class MdMessageType {
    public static final short SNAPSHOT_L2 = 1;
    public static final short INCREMENTAL_L2 = 2;
    public static final short SNAPSHOT_L2_DEPTH = 3;
    public static final short INCREMENTAL_L3 = 4;

    public static final byte L3_ADD = 1;
    public static final byte L3_MODIFY = 2;
    public static final byte L3_CANCEL = 3;
    public static final byte L3_TRADE = 4;

    private MdMessageType() {
    }
}
