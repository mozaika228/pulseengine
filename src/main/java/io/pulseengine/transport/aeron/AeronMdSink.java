package io.pulseengine.transport.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import org.agrona.concurrent.UnsafeBuffer;

public final class AeronMdSink implements io.pulseengine.md.MdMessageSink, AutoCloseable {
    private final Publication publication;
    private final UnsafeBuffer sendBuffer = new UnsafeBuffer(0, 0);

    public AeronMdSink(Aeron aeron) {
        this(aeron, AeronChannels.IPC_CHANNEL, AeronChannels.MD_STREAM_ID);
    }

    public AeronMdSink(Aeron aeron, String channel, int streamId) {
        this.publication = aeron.addPublication(channel, streamId);
    }

    @Override
    public void onMessage(byte[] buffer, int offset, int length) {
        sendBuffer.wrap(buffer);
        while (publication.offer(sendBuffer, offset, length) < 0) {
            Thread.onSpinWait();
        }
    }

    @Override
    public void close() {
        publication.close();
    }

    public boolean isConnected() {
        return publication.isConnected();
    }
}
