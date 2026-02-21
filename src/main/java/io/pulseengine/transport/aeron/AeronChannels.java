package io.pulseengine.transport.aeron;

public final class AeronChannels {
    public static final String IPC_CHANNEL = "aeron:ipc";
    public static final String UDP_INGRESS_UNICAST = "aeron:udp?endpoint=127.0.0.1:40123";
    public static final String UDP_MD_MULTICAST = "aeron:udp?endpoint=239.20.90.1:40456|interface=127.0.0.1";
    public static final int ORDERS_STREAM_ID = 1001;
    public static final int EXEC_STREAM_ID = 1002;
    public static final int MD_STREAM_ID = 1003;

    private AeronChannels() {
    }

    public static String udpIngressUnicast(String endpointHost, int endpointPort) {
        return "aeron:udp?endpoint=" + endpointHost + ":" + endpointPort;
    }

    public static String udpMdMulticast(String group, int port, String networkInterface) {
        return "aeron:udp?endpoint=" + group + ":" + port + "|interface=" + networkInterface;
    }
}
