package io.pulseengine.transport.aeron;

public final class AeronChannels {
    public static final String IPC_CHANNEL = "aeron:ipc";
    public static final int ORDERS_STREAM_ID = 1001;
    public static final int EXEC_STREAM_ID = 1002;
    public static final int MD_STREAM_ID = 1003;

    private AeronChannels() {
    }
}
